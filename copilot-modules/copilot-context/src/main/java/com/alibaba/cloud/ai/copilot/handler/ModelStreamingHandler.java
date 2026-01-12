package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.context.SseEmitterContext;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 模型流式输出处理器
 * 处理 AGENT_MODEL_STREAMING 类型的输出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelStreamingHandler implements OutputTypeHandler {

    private final SseEventService sseEventService;

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_MODEL_STREAMING;
    }

    @Override
    public void handle(StreamingOutput output) {
        // 流式增量内容，逐步显示
        log.debug("模型流式输出: {}", output.message().getText());

        SseEmitter emitter = SseEmitterContext.get();
        if (emitter != null) {
            try {
                // 发送 OpenAI 兼容格式的流式内容给前端
                String content = output.message().getText();
                sseEventService.sendOpenAiCompatibleContent(emitter, content);
            } catch (Exception e) {
                log.error("发送模型流式内容失败", e);
            }
        }
    }
}