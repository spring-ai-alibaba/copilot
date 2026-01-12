package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat service interface
 */
public interface ChatService {

    /**
     * Handle builder mode
     */
    void handleBuilderMode(ChatRequest request, String userId, SseEmitter emitter);
}
