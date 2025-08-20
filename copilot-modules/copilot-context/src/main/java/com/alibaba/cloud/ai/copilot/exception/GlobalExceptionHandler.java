package com.alibaba.cloud.ai.copilot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Global exception handler
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Illegal argument exception", e);
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Invalid request",
                    "message", e.getMessage(),
                    "status", HttpStatus.BAD_REQUEST.value()
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception", e);
        
        // Check if it's an API key related error
        if (e.getMessage() != null && e.getMessage().contains("API key")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                        "error", "Invalid or missing API key",
                        "message", "Please check your API key configuration",
                        "status", HttpStatus.UNAUTHORIZED.value()
                    ));
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage(),
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", "An unexpected error occurred",
                    "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }
}
