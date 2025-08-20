package com.alibaba.cloud.ai.copilot.service.impl;


import com.alibaba.cloud.ai.copilot.model.Message;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chat handler implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHandlerImpl implements ChatHandler {

    private final StreamingService streamingService;
    private final TokenService tokenService;
    private final DynamicModelService dynamicModelService;
    private final OpenAiModelFactory openAiModelFactory;


    private static final int MAX_RESPONSE_SEGMENTS = 2;
    private static final String CONTINUE_PROMPT = "Continue your prior response. IMPORTANT: Immediately begin from where you left off without any interruptions. Do not repeat any content, including artifact and action tags.";

    @Override
    public void handle(List<Message> messages, String model, String userId, List<ToolInfo> tools, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                processChat(messages, model, userId, tools, emitter);
            } catch (Exception e) {
                log.error("Error processing chat", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("Error completing emitter with error", ex);
                }
            }
        });
    }

    private void processChat(List<Message> messages, String model, String userId, List<ToolInfo> tools, SseEmitter emitter) {
        try {
            // 使用动态模型服务获取对应的ChatModel
            ChatModel chatModel = dynamicModelService.getChatModel(model, userId);

            // Convert messages to Spring AI format
            List<org.springframework.ai.chat.messages.Message> springMessages =
                streamingService.convertMessages(messages);

            // Create prompt with runtime options
            OpenAiChatOptions chatOptions = openAiModelFactory.createDefaultChatOptions(model);
            Prompt prompt = new Prompt(springMessages, chatOptions);

            // Stream response
            streamingService.streamResponse(chatModel, prompt, emitter, (response) -> {
                // Handle completion
                String content = response.getResult().getOutput().getText();
                String finishReason = response.getResult().getMetadata().getFinishReason();

                if (!"length".equals(finishReason)) {
                    // Calculate tokens and deduct if user exists
                    if (userId != null) {
                        int tokens = tokenService.estimateTokens(content);
                        tokenService.deductUserTokens(userId, tokens);
                    }
                    return true; // Complete
                }

                // Handle continuation if needed
                if (messages.size() >= MAX_RESPONSE_SEGMENTS * 2) {
                    throw new RuntimeException("Cannot continue message: Maximum segments reached");
                }

                // Add assistant response and continue prompt
                Message assistantMessage = new Message();
                assistantMessage.setId(UUID.randomUUID().toString());
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(content);
                messages.add(assistantMessage);

                Message continueMessage = new Message();
                continueMessage.setId(UUID.randomUUID().toString());
                continueMessage.setRole("user");
                continueMessage.setContent(CONTINUE_PROMPT);
                messages.add(continueMessage);

                return false; // Continue
            });

        } catch (Exception e) {
            log.error("Error in chat processing", e);
            throw new RuntimeException(e);
        }
    }


}
