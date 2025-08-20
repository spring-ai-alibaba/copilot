package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Function;

/**
 * Streaming service interface
 */
public interface StreamingService {

    /**
     * Convert messages to Spring AI format
     */
    List<org.springframework.ai.chat.messages.Message> convertMessages(List<Message> messages);

    /**
     * Stream response using SSE
     */
    void streamResponse(ChatModel chatModel, Prompt prompt, SseEmitter emitter, 
                       Function<ChatResponse, Boolean> onComplete);
}
