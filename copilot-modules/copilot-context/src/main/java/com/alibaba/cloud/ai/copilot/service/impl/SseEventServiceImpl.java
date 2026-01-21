package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SSE事件服务实现类
 * 按照流式传输协议格式发送SSE事件
 * <p>
 * 协议格式：
 * event: xxx
 * data: {...}
 * <p>
 *
 */
@Slf4j
@Service
public class SseEventServiceImpl implements SseEventService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送SSE事件的通用方法
     */
    @Override
    public void sendSseEvent(SseEmitter emitter, String eventType, Map<String, Object> data) {
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(eventType)
                .data(dataJson);
            emitter.send(event);
            log.debug("Sent SSE event: {} with data: {}", eventType, dataJson);
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", eventType, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    @Override
    public void sendFileEditProgress(SseEmitter emitter, String filePath, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", "edit-progress");
        // messageId - 当前对话id
        data.put("messageId", UUID.randomUUID().toString());
        // 每个工具都有不同的操作id
        data.put("operationId", UUID.randomUUID().toString());
        data.put("data", Map.of(
            "type", "edit-progress",
            "filePath", filePath,
            "content", content
        ));
        sendSseEvent(emitter, "edit-progress", data);
    }

    @Override
    public void sendThinkingContent(SseEmitter emitter, String thinkingContent) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", "thinking");
        // messageId - 当前对话id
        data.put("messageId", UUID.randomUUID().toString());
        // 每个工具都有不同的操作id
        data.put("operationId", UUID.randomUUID().toString());
        data.put("data", Map.of(
            "type", "thinking",
            "content", thinkingContent
        ));
        sendSseEvent(emitter, "thinking", data);
    }

    @Override
    public void sendChatContent(SseEmitter emitter,String content) {
        try {
            // 生成 OpenAI 兼容格式的消息
            Map<String, Object> delta = new HashMap<>();
            delta.put("content", content);

            Map<String, Object> choice = new HashMap<>();
            choice.put("delta", delta);
            choice.put("finish_reason", null);

            Map<String, Object> data = new HashMap<>();
            data.put("choices", java.util.Arrays.asList(choice));

            String dataJson = objectMapper.writeValueAsString(data);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(dataJson);
            emitter.send(event);
            log.debug("Sent OpenAI compatible content: {}", content);
        } catch (Exception e) {
            log.error("Error sending OpenAI compatible content", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    @Override
    public void sendConversationId(SseEmitter emitter, String conversationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", conversationId);
        sendSseEvent(emitter, "conversation-id", data);
        log.debug("Sent conversation ID: {}", conversationId);
    }

    @Override
    public void sendComplete(SseEmitter emitter) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("complete")
                .data("[DONE]");
            emitter.send(event);
            emitter.complete();
            log.debug("SSE connection completed");
        } catch (Exception e) {
            log.error("Error completing SSE connection", e);
        }
    }

}