package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat service interface
 */
public interface ChatService {

    /**
     * Handle chat mode
     */
    void handleChatMode(ChatRequest request, String userId, SseEmitter emitter);

    /**
     * Handle builder mode
     */
    void handleBuilderMode(ChatRequest request, String userId, SseEmitter emitter);
}
