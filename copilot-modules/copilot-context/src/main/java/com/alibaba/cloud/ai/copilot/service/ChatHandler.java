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
     *
     * @param messages 消息列表
     * @param modelConfigId  用户配置的模型ID
     * @param userId   用户ID
     * @param tools    工具列表
     * @param emitter  SSE发射器
     */
    void handle(List<Message> messages, String modelConfigId, String userId, List<ToolInfo> tools, SseEmitter emitter);
}
