package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.service.StreamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Streaming service implementation
 */
@Slf4j
@Service
public class StreamingServiceImpl implements StreamingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<org.springframework.ai.chat.messages.Message> convertMessages(List<Message> messages) {
        return messages.stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());
    }

    @Override
    public void streamResponse(ChatModel chatModel, Prompt prompt, SseEmitter emitter,
                              Function<ChatResponse, Boolean> onComplete) {
        try {

            // 用于收集完整响应内容
            StringBuilder fullResponseBuilder = new StringBuilder();
            ChatResponse lastResponse = null;

            // 使用Flux流式API
            Flux<ChatResponse> responseStream = chatModel.stream(prompt);
            // 订阅流并处理每个响应块
            responseStream
                .doOnNext(chatResponse -> {
                    try {
                        // 获取当前块的内容
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
                            // 累积完整响应内容
                            fullResponseBuilder.append(content);
                            // 发送流式数据块
                            sendStreamingChunk(emitter, content);
                        }
                    } catch (Exception e) {
                        log.error("Error processing streaming chunk", e);
                    }
                })
                .doOnError(error -> {
                    try {
                        emitter.completeWithError(error);
                    } catch (Exception ex) {
                        log.error("Error completing emitter with error", ex);
                    }
                })
                .doOnComplete(() -> {
                    try {
                        // 发送结束信号
                        sendSseEndEvent(emitter);

                        // 创建包含完整响应的ChatResponse用于回调
                        if (!fullResponseBuilder.isEmpty()) {
                            // 创建一个包含完整内容的ChatResponse
                            ChatResponse completeResponse = createCompleteResponse(fullResponseBuilder.toString());
                            // 调用完成回调
                            if (onComplete != null) {
                                onComplete.apply(completeResponse);
                            }
                        }

                        // 完成SSE连接
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error completing streaming", e);
                    }
                })
                .subscribe();
        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    private org.springframework.ai.chat.messages.Message convertMessage(Message message) {
        return switch (message.getRole().toLowerCase()) {
            case "system" -> new SystemMessage(message.getContent());
            case "assistant" -> new AssistantMessage(message.getContent());
            default -> new UserMessage(message.getContent());
        };
    }

    /**
     * 创建包含完整响应内容的ChatResponse
     */
    private ChatResponse createCompleteResponse(String fullContent) {
        try {
            // 创建AssistantMessage
            AssistantMessage assistantMessage = new AssistantMessage(fullContent);

            // 创建Generation
            Generation generation = new Generation(assistantMessage);

            // 创建ChatResponse
            return new ChatResponse(List.of(generation));
        } catch (Exception e) {
            log.error("Error creating complete response", e);
            // 返回一个简单的响应
            AssistantMessage assistantMessage = new AssistantMessage(fullContent);
            Generation generation = new Generation(assistantMessage);
            return new ChatResponse(List.of(generation));
        }
    }

    /**
     * 发送流式数据块
     */
    private void sendStreamingChunk(SseEmitter emitter, String content) {
        try {
            // 使用Map构造JSON对象，确保格式正确
            Map<String, Object> response = new HashMap<>();
            response.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            response.put("object", "chat.completion.chunk");
            response.put("created", System.currentTimeMillis() / 1000);
            response.put("model", "");

            Map<String, Object> choice = new HashMap<>();
            choice.put("index", 0);
            choice.put("finish_reason", null);

            Map<String, Object> delta = new HashMap<>();
            delta.put("content", content);
            choice.put("delta", delta);

            response.put("choices", List.of(choice));

            // 使用Jackson生成JSON
            String aiFormatJson = objectMapper.writeValueAsString(response);

            // 发送标准SSE格式
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                .data(aiFormatJson);
            emitter.send(event);

        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }


    /**
     * 发送SSE结束事件
     */
    private void sendSseEndEvent(SseEmitter emitter) {
        try {
            // 先发送一个finish_reason为stop的chunk
            Map<String, Object> finishResponse = new HashMap<>();
            finishResponse.put("id", "chatcmpl-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            finishResponse.put("object", "chat.completion.chunk");
            finishResponse.put("created", System.currentTimeMillis() / 1000);
            finishResponse.put("model", "");

            Map<String, Object> finishChoice = new HashMap<>();
            finishChoice.put("index", 0);
            finishChoice.put("delta", new HashMap<>());
            finishChoice.put("finish_reason", "stop");

            finishResponse.put("choices", List.of(finishChoice));

            String finishChunk = objectMapper.writeValueAsString(finishResponse);

            SseEmitter.SseEventBuilder finishEvent = SseEmitter.event()
                .data(finishChunk);
            emitter.send(finishEvent);

            // 然后发送[DONE]标记
            SseEmitter.SseEventBuilder doneEvent = SseEmitter.event()
                .data("[DONE]");
            emitter.send(doneEvent);
        } catch (Exception e) {
            log.error("Error sending SSE end event", e);
        }
    }

}
