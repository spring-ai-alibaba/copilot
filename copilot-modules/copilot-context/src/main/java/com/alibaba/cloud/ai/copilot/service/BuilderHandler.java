package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.PromptExtra;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Builder handler interface
 */
public interface BuilderHandler {

    /**
     * Handle builder mode processing
     */
    void handle(List<Message> messages, String model, String userId, PromptExtra otherConfig, 
                List<ToolInfo> tools, SseEmitter emitter);
}
