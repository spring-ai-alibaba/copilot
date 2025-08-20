package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.PromptExtra;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import com.alibaba.cloud.ai.copilot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class BuilderHandlerImpl implements BuilderHandler {

    private final StreamingService streamingService;
    private final TokenService tokenService;
    private final FileProcessorService fileProcessorService;
    private final DynamicModelService dynamicModelService;
    private final OpenAiModelFactory openAiModelFactory;

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
            // Create a copy of messages for processing
            List<Message> historyMessages = List.copyOf(messages);

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

            if (estimatedTokens > 128000) {
                // Handle token limit by processing files differently
                FileProcessorService.ProcessedFiles limitedFiles =
                    fileProcessorService.processFiles(messages, true);
                files = limitedFiles.getFiles();

                // Build system prompt with file context
                String systemPrompt = buildMaxSystemPrompt(files, fileType, otherConfig);
                lastMessage.setContent(systemPrompt +
                    "Note the requirements above, when writing code, do not give me markdown, output must be XML!! Emphasis!; My question is: " +
                    lastMessage.getContent());
            } else {
                // Build regular system prompt
                String systemPrompt = buildSystemPrompt(fileType, otherConfig);
                lastMessage.setContent(systemPrompt +
                    "Note the requirements above, when writing code, do not give me markdown, output must be XML!! Emphasis!; My question is: " +
                    lastMessage.getContent());
            }

            // 使用动态模型服务获取对应的ChatModel
            ChatModel chatModel = dynamicModelService.getChatModel(model, userId);

            // Convert messages to Spring AI format
            List<org.springframework.ai.chat.messages.Message> springMessages =
                streamingService.convertMessages(messages);

            // Create prompt with options (set model per request)
            OpenAiChatOptions chatOptions = openAiModelFactory.createDefaultChatOptions(model);

            Prompt prompt = new Prompt(springMessages, chatOptions);

            // Stream response
            streamingService.streamResponse(chatModel, prompt, emitter, (response) -> {
                String content = response.getResult().getOutput().getText();
                log.info("Builder response: {}", content);
                return true;
            });
        } catch (Exception e) {
            log.error("Error in builder processing", e);
            throw new RuntimeException(e);
        }
    }

    private void handleScreenshotIfNeeded(Message lastMessage, List<Message> messages) {
        // Extract URL from message content
        String content = lastMessage.getContent();
        String urlPattern = "https?://[^\\s]+";
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
        prompt.append("You are We0 AI, an expert AI assistant and exceptional senior software developer with vast knowledge across multiple programming languages, frameworks, and best practices.\n");
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


}
