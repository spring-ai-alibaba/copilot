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
 * 模型完成输出处理器
 * 处理 AGENT_MODEL_FINISHED 类型的输出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFinishedHandler implements OutputTypeHandler {

    private final SseEventService sseEventService;

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_MODEL_FINISHED;
    }

    @Override
    public void handle(StreamingOutput output) {
        // 模型推理完成，可获取完整响应
        log.debug("模型输出完成");

        SseEmitter emitter = SseEmitterContext.get();
        if (emitter != null) {
            try {
                // 发送 OpenAI 兼容格式的完成信号给前端
                sseEventService.sendOpenAiCompatibleFinish(emitter);
            } catch (Exception e) {
                log.error("发送模型完成事件失败", e);
            }
        }
    }
}