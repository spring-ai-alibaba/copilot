package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.core.utils.StringUtils;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型流式输出处理器
 * 处理 AGENT_MODEL_STREAMING 类型的输出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelStreamingHandler implements OutputTypeHandler {

    private final SseEventService sseEventService;

    // 用于追踪每个 emitter 的 reasoning 内容是否已启动
    private final ConcurrentHashMap<SseEmitter, Boolean> reasoningStartedMap = new ConcurrentHashMap<>();

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_MODEL_STREAMING;
    }

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        try {
            String reasoningContent = output.message().getMetadata().get("reasoningContent").toString();
            boolean reasoningOpen = reasoningStartedMap.getOrDefault(emitter, false);

            if (StringUtils.isNotEmpty(reasoningContent)) {
                // 首次输出思考内容时打开 <think> 标签
                if (!reasoningOpen) {
                    reasoningStartedMap.put(emitter, true);
                    reasoningContent = "<think>" + reasoningContent;
                }
                sseEventService.sendChatContent(emitter, reasoningContent);
            } else {
                String content = output.message().getText();
                // 思考结束时关闭 </think> 标签
                if (reasoningOpen) {
                    reasoningStartedMap.put(emitter, false);
                    content = "</think>" + content;
                }
                if (StringUtils.isNotEmpty(content)) {
                    sseEventService.sendChatContent(emitter, content);
                }
            }
        } catch (Exception e) {
            log.error("发送模型流式内容失败", e);
            reasoningStartedMap.remove(emitter);
        }
    }
}