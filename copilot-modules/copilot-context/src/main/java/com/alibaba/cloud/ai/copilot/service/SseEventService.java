package com.alibaba.cloud.ai.copilot.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE事件服务接口
 * 按照流式传输协议发送SSE事件
 */
public interface SseEventService {

    /**
     * 发送文件添加开始事件
     */
    void sendFileAddStart(SseEmitter emitter, String messageId, String operationId, String filePath);

    /**
     * 发送文件添加进度事件
     */
    void sendFileAddProgress(SseEmitter emitter, String messageId, String operationId, String filePath, String content);

    /**
     * 发送文件添加结束事件
     */
    void sendFileAddEnd(SseEmitter emitter, String messageId, String operationId, String filePath, String content);

    /**
     * 发送文件编辑开始事件
     */
    void sendFileEditStart(SseEmitter emitter, String messageId, String operationId, String filePath);

    /**
     * 发送文件编辑进度事件
     */
    void sendFileEditProgress(SseEmitter emitter, String messageId, String operationId, String filePath, String oldStr, String newStr);

    /**
     * 发送文件编辑结束事件
     */
    void sendFileEditEnd(SseEmitter emitter, String messageId, String operationId, String filePath);

    /**
     * 发送文件删除开始事件
     */
    void sendFileDeleteStart(SseEmitter emitter, String messageId, String operationId, String filePath);

    /**
     * 发送文件删除结束事件
     */
    void sendFileDeleteEnd(SseEmitter emitter, String messageId, String operationId, String filePath);

    /**
     * 发送命令执行事件
     */
    void sendCommandEvent(SseEmitter emitter, String messageId, String operationId, String command, String output);

    /**
     * 发送聊天内容事件
     */
    void sendChatContent(SseEmitter emitter, String messageId, String content);

    /**
     * 发送展示开始事件
     */
    void sendShowStart(SseEmitter emitter, String messageId, String filePath);

    /**
     * 发送展示结束事件
     */
    void sendShowEnd(SseEmitter emitter, String messageId, String filePath);

    /**
     * 发送错误事件
     */
    void sendError(SseEmitter emitter, String messageId, String operationId, String errorMessage);

    /**
     * 发送完成事件
     */
    void sendComplete(SseEmitter emitter);

    /**
     * 发送 OpenAI 兼容格式的流式内容
     * 前端期望的格式，用于与 ai/react 库兼容
     */
    void sendOpenAiCompatibleContent(SseEmitter emitter, String content);

    /**
     * 发送 OpenAI 兼容格式的完成信号
     */
    void sendOpenAiCompatibleFinish(SseEmitter emitter);

}