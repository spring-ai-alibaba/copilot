package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.model.ChatMode;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Handle chat requests
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "userId", required = false) String userId) {
        try {
            // 5 minutes timeout
            SseEmitter emitter = new SseEmitter(300000L);
            if (request.getMode() == ChatMode.CHAT) {
                chatService.handleChatMode(request, userId, emitter);
            } else {
                chatService.handleBuilderMode(request, userId, emitter);
            }
            return ResponseEntity.ok(emitter);
        } catch (Exception error) {
            if (error.getMessage() != null && error.getMessage().contains("API key")) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.status(500).build();
        }
    }
}
