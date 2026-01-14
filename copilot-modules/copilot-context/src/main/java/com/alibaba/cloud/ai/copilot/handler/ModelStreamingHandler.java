package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.core.utils.StringUtils;
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

    private StringBuilder stringBuilder = new StringBuilder();

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_MODEL_STREAMING;
    }

    boolean isFastSend = true;

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        try {
            String reasoningContent = output.message().getMetadata().get("reasoningContent").toString();

            if (StringUtils.isNotEmpty(reasoningContent)) {
                sseEventService.sendThinkingContent(emitter, reasoningContent);
            } else {
                String content = output.message().getText();
                if (StringUtils.isNotEmpty(content)) {
                    sseEventService.sendChatContent(emitter, content);
                }
            }
        } catch (Exception e) {
            log.error("发送模型流式内容失败", e);
        }
    }
}