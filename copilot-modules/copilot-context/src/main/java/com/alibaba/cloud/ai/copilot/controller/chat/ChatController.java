package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天入口（AG-UI 协议，SSE 输出）。
 *
 * <p>前端 POST /api/chat，后端通过 {@link ChatService#handleBuilderMode} 内部用
 * agentscope AguiAgentAdapter 把 agent 事件流转成 AG-UI SSE 帧回写。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        return chatService.handleBuilderMode(request);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<R<Void>> handleChatServiceException(ServiceException exception) {
        Integer code = exception.getCode();
        HttpStatus status = code == null ? null : HttpStatus.resolve(code);
        if (status == null || !status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = status.value();
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(R.fail(code, exception.getMessage()));
    }
}
