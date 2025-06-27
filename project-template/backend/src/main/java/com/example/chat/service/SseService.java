package com.example.chat.service;

import com.example.chat.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE (Server-Sent Events) 服务
 * 管理客户端连接和消息推送
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
@Service
public class SseService {
    
    private static final Logger logger = LoggerFactory.getLogger(SseService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 存储客户端连接，键为会话ID，值为SseEmitter
    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();
    
    // SSE连接超时时间（5分钟）
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    
    /**
     * 创建SSE连接
     * 
     * @param conversationId 会话ID
     * @return SseEmitter
     */
    public SseEmitter createConnection(String conversationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // 设置连接回调
        emitter.onCompletion(() -> removeConnection(conversationId));
        emitter.onTimeout(() -> removeConnection(conversationId));
        emitter.onError((ex) -> {
            logger.error("SSE连接错误，会话ID: {}", conversationId, ex);
            removeConnection(conversationId);
        });
        
        // 存储连接
        connections.put(conversationId, emitter);
        
        // 发送连接确认消息
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"message\":\"连接已建立\",\"conversationId\":\"" + conversationId + "\"}"));
        } catch (IOException e) {
            logger.error("发送连接确认消息失败，会话ID: {}", conversationId, e);
            removeConnection(conversationId);
        }
        
        logger.info("创建SSE连接，会话ID: {}", conversationId);
        return emitter;
    }
    
    /**
     * 移除连接
     * 
     * @param conversationId 会话ID
     */
    public void removeConnection(String conversationId) {
        connections.remove(conversationId);
        logger.info("移除SSE连接，会话ID: {}", conversationId);
    }
    
    /**
     * 发送消息到指定会话
     * 
     * @param conversationId 会话ID
     * @param response 响应对象
     */
    public void sendMessage(String conversationId, ChatResponse response) {
        SseEmitter emitter = connections.get(conversationId);
        if (emitter == null) {
            logger.warn("未找到SSE连接，会话ID: {}", conversationId);
            return;
        }
        
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            emitter.send(SseEmitter.event()
                .name("message")
                .data(jsonData));
            
            logger.debug("发送SSE消息成功，会话ID: {}", conversationId);
            
            // 如果消息已完成，关闭连接
            if (response.isComplete()) {
                emitter.complete();
                removeConnection(conversationId);
            }
            
        } catch (IOException e) {
            logger.error("发送SSE消息失败，会话ID: {}", conversationId, e);
            removeConnection(conversationId);
        } catch (Exception e) {
            logger.error("序列化响应对象失败，会话ID: {}", conversationId, e);
        }
    }
    
    /**
     * 发送文本块（用于流式响应）
     * 
     * @param conversationId 会话ID
     * @param chunk 文本块
     * @param isComplete 是否完成
     */
    public void sendChunk(String conversationId, String chunk, boolean isComplete) {
        ChatResponse response = new ChatResponse(conversationId);
        response.setMessage(chunk);
        response.setComplete(isComplete);
        response.setStatus(isComplete ? 
            ChatResponse.ResponseStatus.SUCCESS : 
            ChatResponse.ResponseStatus.PROCESSING);
        
        sendMessage(conversationId, response);
    }
    
    /**
     * 发送错误消息
     * 
     * @param conversationId 会话ID
     * @param errorMessage 错误消息
     */
    public void sendError(String conversationId, String errorMessage) {
        ChatResponse response = ChatResponse.error(conversationId, errorMessage);
        sendMessage(conversationId, response);
    }
    
    /**
     * 获取当前连接数
     * 
     * @return 连接数
     */
    public int getConnectionCount() {
        return connections.size();
    }
    
    /**
     * 检查指定会话是否有活跃连接
     * 
     * @param conversationId 会话ID
     * @return 是否有连接
     */
    public boolean hasConnection(String conversationId) {
        return connections.containsKey(conversationId);
    }
}
