package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.PromptExtra;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import com.alibaba.cloud.ai.copilot.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Builder handler implementation
 */
@Slf4j
@Service
public class BuilderHandlerImpl implements BuilderHandler {

    private final TokenService tokenService;
    private final FileProcessorService fileProcessorService;
    private final DynamicModelService dynamicModelService;
    private final OpenAiModelFactory openAiModelFactory;
    private final ConversationService conversationService;
    private final ChatMemory chatMemory;

    public BuilderHandlerImpl(
            TokenService tokenService,
            FileProcessorService fileProcessorService,
            DynamicModelService dynamicModelService,
            OpenAiModelFactory openAiModelFactory,
            @Qualifier("chatMemoryConversationService") ConversationService conversationService,
            ChatMemory chatMemory) {
        this.tokenService = tokenService;
        this.fileProcessorService = fileProcessorService;
        this.dynamicModelService = dynamicModelService;
        this.openAiModelFactory = openAiModelFactory;
        this.conversationService = conversationService;
        this.chatMemory = chatMemory;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(List<Message> messages, String model, String userId, PromptExtra otherConfig,
                      List<ToolInfo> tools, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                processBuilder(messages, model, userId, otherConfig, tools, emitter);
            } catch (Exception e) {
                log.error("Error processing builder", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Error completing emitter with error", ex);
                }
            }
        });
    }

    private void processBuilder(List<Message> messages, String model, String userId, PromptExtra otherConfig,
                               List<ToolInfo> tools, SseEmitter emitter) {
        try {
            // Get or create conversation ID for this user session
            String conversationId = conversationService.getOrCreateConversationId(userId);
            log.info("Processing conversation {} for user {}", conversationId, userId);

            // Process files from messages
            FileProcessorService.ProcessedFiles processedFiles =
                fileProcessorService.processFiles(messages, false);
            Map<String, String> files = processedFiles.getFiles();
            String allContent = processedFiles.getAllContent();

            // Check for URL in last message and handle screenshot if needed
            Message lastMessage = messages.get(messages.size() - 1);
            if (lastMessage.getRole().equals("user") && lastMessage.getContent().startsWith("#")) {
                handleScreenshotIfNeeded(lastMessage, messages);
            }

            // Determine file type and handle token limits
            String fileType = determineFileType(files.keySet());
            int estimatedTokens = tokenService.estimateTokens(allContent);

            // 保存原始用户问题，避免系统提示词污染
            String originalUserQuestion = lastMessage.getContent();

            // 构建系统提示词（但不合并到用户消息中，留给记忆管理处理）
            String systemPrompt;
            if (estimatedTokens > 128000) {
                // Handle token limit by processing files differently
                FileProcessorService.ProcessedFiles limitedFiles =
                    fileProcessorService.processFiles(messages, true);
                files = limitedFiles.getFiles();
                systemPrompt = buildMaxSystemPrompt(files, fileType, otherConfig);
            } else {
                // Build regular system prompt
                if (fileType != null && !fileType.isEmpty()) {
                    systemPrompt = buildSystemPromptWithFileType(fileType, otherConfig);
                } else {
                    systemPrompt = buildSystemPrompt(fileType, otherConfig);
                }
            }

            // 添加输出格式要求到系统提示词
            systemPrompt += "\nNote the requirements above, when writing code, do not give me markdown, output must be XML!! Emphasis!";

            // 使用动态模型服务获取对应的ChatModel
            ChatModel chatModel = dynamicModelService.getChatModel(model, userId);

            try {
                // 获取记忆中的对话历史
                List<org.springframework.ai.chat.messages.Message> memoryMessages = chatMemory.get(conversationId);
                boolean isFirstConversation = memoryMessages.isEmpty();

                // 构建最终的消息列表
                List<org.springframework.ai.chat.messages.Message> finalMessages = new java.util.ArrayList<>();

                // 只在第一次对话时添加系统提示词到记忆中
                if (isFirstConversation) {
                    // 使用已经构建好的系统提示词（避免重复构建）
                    SystemMessage systemMessage = new SystemMessage(systemPrompt);
                    chatMemory.add(conversationId, systemMessage);
                    finalMessages.add(systemMessage);

                    log.debug("Added system prompt to memory for conversation {} (length: {})",
                             conversationId, systemPrompt.length());
                }

                // 将当前用户消息（原始的纯净问题）添加到记忆中
                UserMessage userMessage = new UserMessage(originalUserQuestion);
                chatMemory.add(conversationId, userMessage);

                // 获取更新后的记忆消息
                memoryMessages = chatMemory.get(conversationId);
                finalMessages.addAll(memoryMessages);

                // 创建包含历史记忆的Prompt
                OpenAiChatOptions chatOptions = openAiModelFactory.createDefaultChatOptions(model);
                Prompt prompt = new Prompt(finalMessages, chatOptions);

                // 用于收集完整的AI响应
                StringBuilder responseBuilder = new StringBuilder();

                // 使用Flux流式API
                Flux<ChatResponse> responseStream = chatModel.stream(prompt);
                responseStream
                    .doOnNext(chatResponse -> {
                        try {
                            // 获取当前块的内容
                            String content = chatResponse.getResult().getOutput().getText();
                            if (content != null && !content.isEmpty()) {
                                responseBuilder.append(content);
                                // 发送流式数据块到前端
                                sendStreamingChunk(emitter, content);
                            }
                        } catch (Exception e) {
                            log.error("Error processing streaming chunk", e);
                        }
                    })
                    .doOnError(error -> {
                        try {
                            emitter.completeWithError(error);
                        } catch (Exception ex) {
                            log.error("Error completing emitter with error", ex);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            // 将AI的完整响应添加到记忆中
                            if (!responseBuilder.isEmpty()) {
                                AssistantMessage assistantMessage = new AssistantMessage(responseBuilder.toString());
                                chatMemory.add(conversationId, assistantMessage);
                                log.debug("Added assistant response to memory for conversation {}: {}",
                                         conversationId, responseBuilder.length() > 100 ?
                                         responseBuilder.substring(0, 100) + "..." : responseBuilder.toString());
                            }
                            // 发送结束信号
                            sendSseEndEvent(emitter);
                            // 完成SSE连接
                            emitter.complete();
                            log.debug("Completed streaming response for conversation {}", conversationId);
                        } catch (Exception e) {
                            log.error("Error completing streaming", e);
                        }
                    })
                    .subscribe();
            } catch (Exception e) {
                log.error("Error in streaming with explicit memory management", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Error completing emitter with error", ex);
                }
            }
        } catch (Exception e) {
            log.error("Error in builder processing", e);
            throw new RuntimeException(e);
        }
    }

    private void handleScreenshotIfNeeded(Message lastMessage, List<Message> messages) {
        // Extract URL from message content
        String content = lastMessage.getContent();
        String urlPattern = "https?://\\S+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String url = matcher.group();
            try {
                // TODO: Implement screenshot capture
                // For now, just log the URL
                log.info("Screenshot requested for URL: {}", url);

                // Create a placeholder message for screenshot
                Message screenshotMessage = new Message();
                screenshotMessage.setId(UUID.randomUUID().toString());
                screenshotMessage.setRole("user");
                screenshotMessage.setContent("1:1 Restore this page");
                // TODO: Add experimental_attachments with screenshot URL

                messages.add(messages.size() - 1, screenshotMessage);
            } catch (Exception e) {
                log.error("Screenshot capture failed", e);
            }
        }
    }

    private String determineFileType(java.util.Set<String> filePaths) {
        // Simple file type detection based on file extensions
        for (String path : filePaths) {
            if (path.endsWith(".wxml") || path.endsWith(".wxss") || path.endsWith(".js")) {
                return "miniProgram";
            }
        }
        return "other";
    }

    private String buildSystemPrompt(String fileType, PromptExtra otherConfig) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are alibaba copilot AI, an expert AI assistant and exceptional senior software developer with vast knowledge across multiple programming languages, frameworks, and best practices.\n");
        prompt.append("When modifying the code, the output must be in the following format! ! ! ! emphasize! ! ! ! ! ! ! ! ! ! ! !\n\n");

        // Add artifact instructions
        prompt.append("IMPORTANT: Wrap the content in opening and closing `<boltArtifact>` tags. These tags contain more specific `<boltAction>` elements.\n");
        prompt.append("IMPORTANT: Add a title for the artifact to the `title` attribute of the opening `<boltArtifact>`.\n");
        prompt.append("IMPORTANT: Add a unique identifier to the `id` attribute of the opening `<boltArtifact>`. Use kebab-case (e.g., \"example-code-snippet\").\n");
        prompt.append("IMPORTANT: Use `<boltAction>` tags to define specific actions to perform.\n");
        prompt.append("IMPORTANT: For each `<boltAction>`, add a type to the `type` attribute:\n");
        prompt.append("  - file: For writing new files or updating existing files. Add a `filePath` attribute to specify the file path.\n");
        prompt.append("  - shell: For running shell commands.\n");
        prompt.append("  - start: For starting development server.\n\n");

        prompt.append("IMPORTANT: All code must be complete code, do not generate code snippets, and do not use Markdown\n");
        prompt.append("IMPORTANT: 强调：你必须每次都要按照下面格式输出<boltArtifact></boltArtifact> 例如这样的格式\n\n");

        // Add example
        prompt.append("CRITICAL EXAMPLE FORMAT - YOU MUST FOLLOW THIS EXACTLY:\n");
        prompt.append("<boltArtifact id=\"project-name\" title=\"Project Title\">\n");
        prompt.append("  <boltAction type=\"file\" filePath=\"index.html\">\n");
        prompt.append("<!DOCTYPE html>\n");
        prompt.append("<html>\n");
        prompt.append("  <head><title>Example</title></head>\n");
        prompt.append("  <body><h1>Hello World</h1></body>\n");
        prompt.append("</html>\n");
        prompt.append("  </boltAction>\n");
        prompt.append("  <boltAction type=\"file\" filePath=\"script.js\">\n");
        prompt.append("console.log('Hello World');\n");
        prompt.append("  </boltAction>\n");
        prompt.append("</boltArtifact>\n\n");
        prompt.append("IMPORTANT: Always include type=\"file\" in boltAction tags!\n");
        prompt.append("IMPORTANT: Always include proper filePath attribute!\n");
        prompt.append("IMPORTANT: File content should be complete and valid!\n\n");

        if ("miniProgram".equals(fileType)) {
            prompt.append("IMPORTANT: For any place that uses images, implement using weui's icon library\n");
        }

        if (otherConfig != null && otherConfig.isBackEnd()) {
            prompt.append("IMPORTANT: You must generate backend code, do not only generate frontend code\n");
            prompt.append("IMPORTANT: Backend must handle CORS for all domains\n");
        }

        return prompt.toString();
    }

    /**
     * 构建带有特定文件类型指令的系统提示词
     * 根据不同的文件类型添加相应的技术栈和最佳实践指导
     */
    private String buildSystemPromptWithFileType(String fileType, PromptExtra otherConfig) {
        StringBuilder prompt = new StringBuilder();

        // 添加基础系统提示词
        prompt.append(buildSystemPrompt(fileType, otherConfig));

        // 根据文件类型添加特定指令
        switch (fileType.toLowerCase()) {
            case "react":
                prompt.append("\nREACT SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Use functional components with hooks (useState, useEffect, etc.)\n");
                prompt.append("- Follow React best practices and patterns\n");
                prompt.append("- Use proper JSX syntax and component structure\n");
                prompt.append("- Implement proper state management\n");
                prompt.append("- Use TypeScript if applicable\n");
                prompt.append("- Include proper prop types and error handling\n\n");
                break;

            case "vue":
                prompt.append("\nVUE SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Use Vue 3 Composition API when possible\n");
                prompt.append("- Follow Vue.js best practices and conventions\n");
                prompt.append("- Use proper template syntax and directives\n");
                prompt.append("- Implement reactive data and computed properties\n");
                prompt.append("- Use proper component lifecycle hooks\n");
                prompt.append("- Include proper TypeScript support if needed\n\n");
                break;

            case "angular":
                prompt.append("\nANGULAR SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Use Angular latest version conventions\n");
                prompt.append("- Follow Angular style guide and best practices\n");
                prompt.append("- Use proper component, service, and module structure\n");
                prompt.append("- Implement dependency injection correctly\n");
                prompt.append("- Use TypeScript with proper typing\n");
                prompt.append("- Include proper RxJS usage for async operations\n\n");
                break;

            case "miniprogram":
                prompt.append("\nMINI PROGRAM SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Follow WeChat Mini Program development standards\n");
                prompt.append("- Use proper WXML, WXSS, and JavaScript structure\n");
                prompt.append("- Implement proper page lifecycle methods\n");
                prompt.append("- Use WeUI components and design patterns\n");
                prompt.append("- Handle proper data binding and event handling\n");
                prompt.append("- Include proper API usage and error handling\n\n");
                break;

            case "nodejs":
            case "node":
                prompt.append("\nNODE.JS SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Use modern Node.js features and ES6+ syntax\n");
                prompt.append("- Follow Node.js best practices and patterns\n");
                prompt.append("- Implement proper error handling and logging\n");
                prompt.append("- Use appropriate npm packages and dependencies\n");
                prompt.append("- Include proper async/await usage\n");
                prompt.append("- Implement proper security practices\n\n");
                break;

            case "python":
                prompt.append("\nPYTHON SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Follow PEP 8 style guidelines\n");
                prompt.append("- Use proper Python idioms and patterns\n");
                prompt.append("- Implement proper exception handling\n");
                prompt.append("- Use type hints when appropriate\n");
                prompt.append("- Follow Python best practices for imports and structure\n");
                prompt.append("- Include proper documentation and comments\n\n");
                break;

            case "java":
                prompt.append("\nJAVA SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Follow Java coding conventions and best practices\n");
                prompt.append("- Use proper OOP principles and design patterns\n");
                prompt.append("- Implement proper exception handling\n");
                prompt.append("- Use appropriate Java 8+ features (streams, lambdas, etc.)\n");
                prompt.append("- Include proper package structure and imports\n");
                prompt.append("- Use proper annotations and documentation\n\n");
                break;

            case "spring":
            case "springboot":
                prompt.append("\nSPRING BOOT SPECIFIC INSTRUCTIONS:\n");
                prompt.append("- Follow Spring Boot conventions and best practices\n");
                prompt.append("- Use proper dependency injection and annotations\n");
                prompt.append("- Implement proper REST API design\n");
                prompt.append("- Use appropriate Spring Boot starters\n");
                prompt.append("- Include proper configuration and properties\n");
                prompt.append("- Implement proper error handling and validation\n\n");
                break;

            default:
                prompt.append("\nGENERAL DEVELOPMENT INSTRUCTIONS:\n");
                prompt.append("- Follow language-specific best practices\n");
                prompt.append("- Use proper code structure and organization\n");
                prompt.append("- Implement proper error handling\n");
                prompt.append("- Include appropriate comments and documentation\n");
                prompt.append("- Use modern language features when applicable\n\n");
                break;
        }

        return prompt.toString();
    }

    private String buildMaxSystemPrompt(Map<String, String> files, String fileType, PromptExtra otherConfig) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(buildSystemPrompt(fileType, otherConfig));
        prompt.append("\nCurrent project files:\n");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            prompt.append("File: ").append(entry.getKey()).append("\n");
            prompt.append(entry.getValue()).append("\n\n");
        }

        prompt.append("\nIMPORTANT: You can only modify the contents within the directory tree above.\n");
        prompt.append("IMPORTANT: When updating existing files, make sure to include the complete file content.\n");
        prompt.append("IMPORTANT: Remember to use the <boltArtifact> and <boltAction> format as shown in the example above.\n");

        return prompt.toString();
    }

    /**
     * 发送流式数据块
     */
    private void sendStreamingChunk(SseEmitter emitter, String content) {
        try {
            // 使用Map构造JSON对象，确保格式正确
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            response.put("object", "chat.completion.chunk");
            response.put("created", System.currentTimeMillis() / 1000);
            response.put("model", "");

            Map<String, Object> choice = new java.util.HashMap<>();
            choice.put("index", 0);
            choice.put("finish_reason", null);

            Map<String, Object> delta = new java.util.HashMap<>();
            delta.put("content", content);
            choice.put("delta", delta);

            response.put("choices", List.of(choice));

            // 使用Jackson生成JSON
            String aiFormatJson = objectMapper.writeValueAsString(response);

            // 发送标准SSE格式
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .data(aiFormatJson);
            emitter.send(event);

        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    /**
     * 发送SSE结束事件
     */
    private void sendSseEndEvent(SseEmitter emitter) {
        try {
            // 先发送一个finish_reason为stop的chunk
            Map<String, Object> finishResponse = new java.util.HashMap<>();
            finishResponse.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            finishResponse.put("object", "chat.completion.chunk");
            finishResponse.put("created", System.currentTimeMillis() / 1000);
            finishResponse.put("model", "");

            Map<String, Object> finishChoice = new java.util.HashMap<>();
            finishChoice.put("index", 0);
            finishChoice.put("delta", new java.util.HashMap<>());
            finishChoice.put("finish_reason", "stop");

            finishResponse.put("choices", List.of(finishChoice));

            String finishChunk = objectMapper.writeValueAsString(finishResponse);

            SseEmitter.SseEventBuilder finishEvent = SseEmitter.event()
                .data(finishChunk);
            emitter.send(finishEvent);

            // 然后发送[DONE]标记
            SseEmitter.SseEventBuilder doneEvent = SseEmitter.event()
                .data("[DONE]");
            emitter.send(doneEvent);
        } catch (Exception e) {
            log.error("Error sending SSE end event", e);
        }
    }

}
