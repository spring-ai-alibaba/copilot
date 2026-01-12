package com.alibaba.cloud.ai.copilot.context;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE Emitter 上下文管理
 * 用于在处理器中访问当前请求的 SseEmitter
 */
public class SseEmitterContext {

    private static final ThreadLocal<SseEmitter> emitterHolder = new ThreadLocal<>();

    /**
     * 设置当前线程的 SseEmitter
     */
    public static void set(SseEmitter emitter) {
        emitterHolder.set(emitter);
    }

    /**
     * 获取当前线程的 SseEmitter
     */
    public static SseEmitter get() {
        return emitterHolder.get();
    }

    /**
     * 清除当前线程的 SseEmitter
     */
    public static void clear() {
        emitterHolder.remove();
    }
}