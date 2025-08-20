package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Chat handler interface
 */
public interface ChatHandler {

    /**
     * Handle chat mode processing
     */
    void handle(List<Message> messages, String model, String userId, List<ToolInfo> tools, SseEmitter emitter);
}
