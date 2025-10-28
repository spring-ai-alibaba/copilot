package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ChatHandler;
import com.alibaba.cloud.ai.copilot.service.BuilderHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat service implementation with file system support
 * 使用文件系统增强的聊天服务实现
 */
@Slf4j
@Service("chatServiceWithFileSystem")
@Primary
public class ChatServiceWithFileSystemImpl implements ChatService {

    private final ChatHandler chatHandler;
    private final BuilderHandler builderHandler;

    public ChatServiceWithFileSystemImpl(ChatHandler chatHandler,
                                        @Qualifier("builderHandlerWithFileSystem") BuilderHandler builderHandler) {
        this.chatHandler = chatHandler;
        this.builderHandler = builderHandler;
    }

    @Override
    public void handleChatMode(ChatRequest request, String userId, SseEmitter emitter) {
        try {
            chatHandler.handle(request.getMessages(), request.getModel(), userId, request.getTools(), emitter);
        } catch (Exception e) {
            log.error("Error in chat mode", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    @Override
    public void handleBuilderMode(ChatRequest request, String userId, SseEmitter emitter) {
        try {
            builderHandler.handle(
                request.getMessages(),
                request.getModel(),
                userId,
                request.getOtherConfig(),
                request.getTools(),
                emitter
            );
        } catch (Exception e) {
            log.error("Error in builder mode", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }
}