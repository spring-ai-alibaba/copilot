package com.alibaba.cloud.ai.copilot.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE事件服务接口
 * 按照流式传输协议发送SSE事件
 */
public interface SseEventService {

    /**
     * 发送文件编辑事件
     */
    void sendFileEditProgress(SseEmitter emitter,String filePath, String content);

    /**
     * 发送聊天内容事件
     */
    void sendChatContent(SseEmitter emitter, String content);


    /**
     * 发送完成事件
     */
    void sendComplete(SseEmitter emitter);

    /**
     * 发送通用 SSE 事件
     * @param emitter SSE 发射器
     * @param eventName 事件名称
     * @param data 事件数据
     */
    void sendSseEvent(SseEmitter emitter, String eventName, Map<String, Object> data);

}