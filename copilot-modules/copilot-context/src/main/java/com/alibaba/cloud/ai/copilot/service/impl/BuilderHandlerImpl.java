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
    private final PromptTemplateService promptTemplateService;

    public BuilderHandlerImpl(
            TokenService tokenService,
            FileProcessorService fileProcessorService,
            DynamicModelService dynamicModelService,
            OpenAiModelFactory openAiModelFactory,
            @Qualifier("chatMemoryConversationService") ConversationService conversationService,
            ChatMemory chatMemory,
            @Qualifier("promptTemplateServiceImpl") PromptTemplateService promptTemplateService) {
        this.tokenService = tokenService;
        this.fileProcessorService = fileProcessorService;
        this.dynamicModelService = dynamicModelService;
        this.openAiModelFactory = openAiModelFactory;
        this.conversationService = conversationService;
        this.chatMemory = chatMemory;
        this.promptTemplateService = promptTemplateService;
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
            boolean backEnd = otherConfig != null && otherConfig.isBackEnd();

            if (estimatedTokens > 128000) {
                // Handle token limit by processing files differently
                FileProcessorService.ProcessedFiles limitedFiles =
                    fileProcessorService.processFiles(messages, true);
                files = limitedFiles.getFiles();
                systemPrompt = promptTemplateService.buildMaxSystemPrompt(files, fileType, backEnd);
            } else {
                // Build regular system prompt
                if (fileType != null && !fileType.isEmpty()) {
                    systemPrompt = promptTemplateService.buildSystemPromptWithFileType(fileType, backEnd);
                } else {
                    systemPrompt = promptTemplateService.buildSystemPrompt(fileType, backEnd);
                }
            }

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
                   // finalMessages.add(systemMessage);

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
