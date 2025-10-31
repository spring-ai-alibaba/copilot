package com.alibaba.cloud.ai.copilot.exception;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Illegal argument exception", e);
        return R.fail(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public R<Void> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception", e);
        
        // Check if it's an API key related error
        if (e.getMessage() != null && e.getMessage().contains("API key")) {
            // 返回体仍为 R，但设置 code 为 401。HTTP 状态交由上层处理或保持 200。
            // 如需严格返回 401，可考虑抛出自定义异常并在安全异常处理器中标注 @ResponseStatus(UNAUTHORIZED)。
            return R.fail(HttpStatus.UNAUTHORIZED.value(), "Invalid or missing API key");
        }
        
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleGenericException(Exception e) {
        log.error("Unexpected exception", e);
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred");
    }
}
