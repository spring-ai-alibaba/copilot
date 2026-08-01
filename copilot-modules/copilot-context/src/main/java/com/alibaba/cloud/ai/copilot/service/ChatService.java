package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {

    SseEmitter handleBuilderMode(ChatRequest request);
}
