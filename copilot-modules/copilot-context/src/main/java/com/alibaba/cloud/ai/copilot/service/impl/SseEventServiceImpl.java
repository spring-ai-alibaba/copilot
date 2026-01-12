package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

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
    private void sendSseEvent(SseEmitter emitter, String eventType, Map<String, Object> data) {
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
    public void sendFileAddStart(SseEmitter emitter, String messageId, String operationId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "add-start",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "add-start", data);
    }

    @Override
    public void sendFileAddProgress(SseEmitter emitter, String messageId, String operationId, String filePath, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "add-progress",
            "filePath", filePath,
            "content", content
        ));
        sendSseEvent(emitter, "add-progress", data);
    }

    @Override
    public void sendFileAddEnd(SseEmitter emitter, String messageId, String operationId, String filePath, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "add-end",
            "filePath", filePath,
            "content", content,
            "encoding", "utf-8",
            "mode", "overwrite"
        ));
        sendSseEvent(emitter, "add-end", data);
    }

    @Override
    public void sendFileEditStart(SseEmitter emitter, String messageId, String operationId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "edit-start",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "edit-start", data);
    }

    @Override
    public void sendFileEditProgress(SseEmitter emitter, String messageId, String operationId, String filePath, String oldStr, String newStr) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "edit-progress",
            "filePath", filePath,
            "oldStr", oldStr,
            "newStr", newStr
        ));
        sendSseEvent(emitter, "edit-progress", data);
    }

    @Override
    public void sendFileEditEnd(SseEmitter emitter, String messageId, String operationId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "edit-end",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "edit-end", data);
    }

    @Override
    public void sendFileDeleteStart(SseEmitter emitter, String messageId, String operationId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "delete-start",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "delete-start", data);
    }

    @Override
    public void sendFileDeleteEnd(SseEmitter emitter, String messageId, String operationId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "delete-end",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "delete-end", data);
    }

    @Override
    public void sendCommandEvent(SseEmitter emitter, String messageId, String operationId, String command, String output) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("data", Map.of(
            "type", "cmd",
            "command", command,
            "output", output
        ));
        sendSseEvent(emitter, "cmd", data);
    }

    @Override
    public void sendChatContent(SseEmitter emitter, String messageId, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("data", Map.of(
            "type", "chat",
            "content", content
        ));
        sendSseEvent(emitter, "chat", data);
    }

    @Override
    public void sendShowStart(SseEmitter emitter, String messageId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("data", Map.of(
            "type", "show-start",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "chat", data);
    }

    @Override
    public void sendShowEnd(SseEmitter emitter, String messageId, String filePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("data", Map.of(
            "type", "show-end",
            "filePath", filePath
        ));
        sendSseEvent(emitter, "chat", data);
    }

    @Override
    public void sendError(SseEmitter emitter, String messageId, String operationId, String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("operationId", operationId);
        data.put("error", Map.of(
            "message", errorMessage,
            "code", "EXECUTION_ERROR"
        ));
        sendSseEvent(emitter, "error", data);
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

    @Override
    public void sendOpenAiCompatibleContent(SseEmitter emitter, String content) {
        try {
            // 生成 OpenAI 兼容格式的消息
            // 前端期望的格式：{"choices":[{"delta":{"content":"..."},"finish_reason":null}]}
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
    public void sendOpenAiCompatibleFinish(SseEmitter emitter) {
        try {
            // 发送完成信号
            Map<String, Object> delta = new HashMap<>();
            Map<String, Object> choice = new HashMap<>();
            choice.put("delta", delta);
            choice.put("finish_reason", "stop");

            Map<String, Object> data = new HashMap<>();
            data.put("choices", java.util.Arrays.asList(choice));

            String dataJson = objectMapper.writeValueAsString(data);
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .data(dataJson);
            emitter.send(event);
            log.debug("Sent OpenAI compatible finish signal");
        } catch (Exception e) {
            log.error("Error sending OpenAI compatible finish", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }
}