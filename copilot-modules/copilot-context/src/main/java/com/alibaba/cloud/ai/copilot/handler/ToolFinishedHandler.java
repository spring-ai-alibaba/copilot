package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工具调用完成处理器
 * 处理 AGENT_TOOL_FINISHED 类型的输出
 */
@Component
public class ToolFinishedHandler implements OutputTypeHandler {

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_TOOL_FINISHED;
    }

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        Message message = output.message();
        if (message instanceof AssistantMessage assistantMessage) {
            if (assistantMessage.hasToolCalls()) {
                // 工具调用请求
                assistantMessage.getToolCalls().forEach(toolCall -> {
                    System.out.println("[Tool Call] " + toolCall.name() + ": " + toolCall.arguments());
                });
            } else {
                // 模型完整响应
                System.out.println("\n[Model Finished]");
            }
        }
    }
}