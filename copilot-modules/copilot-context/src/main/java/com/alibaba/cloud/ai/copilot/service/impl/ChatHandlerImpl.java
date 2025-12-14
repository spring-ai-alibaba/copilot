package com.alibaba.cloud.ai.copilot.service.impl;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import com.alibaba.cloud.ai.copilot.service.ChatHandler;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.StreamingService;
import com.alibaba.cloud.ai.copilot.service.TokenService;
import com.alibaba.cloud.ai.copilot.util.ChatMcpToolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Chat handler implementation
 * 支持 MCP 工具调用的聊天处理器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHandlerImpl implements ChatHandler {

    private final StreamingService streamingService;
    private final TokenService tokenService;
    private final DynamicModelService dynamicModelService;
    private final ChatMcpToolUtils chatMcpToolUtils;

    private static final int MAX_RESPONSE_SEGMENTS = 2;
    private static final String CONTINUE_PROMPT = "Continue your prior response. IMPORTANT: Immediately begin from where you left off without any interruptions. Do not repeat any content, including artifact and action tags.";

    @Override
    public void handle(List<Message> messages, String modelConfigId, String userId, List<ToolInfo> tools, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                processChat(messages, modelConfigId, userId, tools, emitter);
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

    private void processChat(List<Message> messages, String modelConfigId, String userId, List<ToolInfo> tools, SseEmitter emitter) {
        try {
            // 使用动态模型服务获取对应的ChatModel
            ChatModel chatModel = dynamicModelService.getChatModelWithConfigId(modelConfigId);

            // Convert messages to Spring AI format
            List<org.springframework.ai.chat.messages.Message> springMessages =
                    streamingService.convertMessages(messages);

            // 创建 Prompt，如果有 MCP 工具则添加工具回调
            Prompt prompt;
            if (CollUtil.isNotEmpty(tools)) {
                // 将前端传来的 ToolInfo 转换为 Spring AI 的 ToolCallback
                List<ToolCallback> toolCallbacks = chatMcpToolUtils.convertToolsToCallbacks(tools);
                log.info("配置了 {} 个 MCP 工具用于聊天", toolCallbacks.size());

                // 创建包含工具的选项
                ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                        .toolCallbacks(toolCallbacks)
                        .build();

                prompt = new Prompt(springMessages, toolOptions);
            } else {
                // 没有工具时创建普通 Prompt
                prompt = new Prompt(springMessages);
            }

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
