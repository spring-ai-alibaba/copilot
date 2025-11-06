package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.model.ChatMode;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.core.domain.R;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    public SseEmitter chat(@RequestBody ChatRequest request) {
        // 强制使用已登录用户ID，避免信任客户端可伪造的 header
        String userId = StpUtil.getLoginIdAsString();
        
        SseEmitter emitter = new SseEmitter(0L);
        if (request.getMode() == ChatMode.CHAT) {
            chatService.handleChatMode(request, userId, emitter);
        } else {
            chatService.handleBuilderMode(request, userId, emitter);
        }
        return emitter;
    }
}
