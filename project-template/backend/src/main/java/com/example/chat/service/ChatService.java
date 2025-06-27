package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 聊天服务
 * 处理与AI模型的交互逻辑
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    @Autowired
    private ChatClient.Builder chatClientBuilder;
    
    @Autowired
    private SseService sseService;
    
    private ChatClient chatClient;
    
    /**
     * 初始化ChatClient
     */
    public void initChatClient() {
        if (chatClient == null) {
            chatClient = chatClientBuilder.build();
            logger.info("ChatClient初始化完成");
        }
    }
    
    /**
     * 发送消息并获取响应
     * 
     * @param message 用户消息
     * @return 聊天响应
     */
    public ChatResponse sendMessage(ChatMessage message) {
        try {
            initChatClient();
            
            logger.info("处理聊天消息: {}", message.getMessage());
            
            // 调用Spring AI获取响应
            String responseContent = chatClient.prompt()
                .user(message.getMessage())
                .call()
                .content();
            
            logger.info("AI响应: {}", responseContent);
            
            return ChatResponse.success(message.getConversationId(), responseContent);
            
        } catch (Exception e) {
            logger.error("处理聊天消息失败", e);
            return ChatResponse.error(message.getConversationId(), "处理消息时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 异步发送消息并通过SSE流式返回响应
     * 
     * @param message 用户消息
     */
    @Async
    public void sendMessageAsync(ChatMessage message) {
        try {
            initChatClient();
            
            String conversationId = message.getConversationId();
            if (conversationId == null) {
                conversationId = UUID.randomUUID().toString();
                message.setConversationId(conversationId);
            }
            
            logger.info("异步处理聊天消息: {}, 会话ID: {}", message.getMessage(), conversationId);
            
            // 发送处理中状态
            sseService.sendMessage(conversationId, ChatResponse.processing(conversationId));
            
            // 获取流式响应
            Flux<String> responseFlux = chatClient.prompt()
                .user(message.getMessage())
                .stream()
                .content();
            
            StringBuilder fullResponse = new StringBuilder();
            
            // 处理流式响应
            responseFlux.subscribe(
                chunk -> {
                    // 发送文本块
                    fullResponse.append(chunk);
                    sseService.sendChunk(conversationId, chunk, false);
                },
                error -> {
                    // 处理错误
                    logger.error("流式响应处理失败", error);
                    sseService.sendError(conversationId, "处理消息时发生错误: " + error.getMessage());
                },
                () -> {
                    // 完成处理
                    logger.info("流式响应完成，会话ID: {}, 完整响应: {}", conversationId, fullResponse.toString());
                    sseService.sendChunk(conversationId, "", true);
                }
            );
            
        } catch (Exception e) {
            logger.error("异步处理聊天消息失败", e);
            String conversationId = message.getConversationId();
            if (conversationId != null) {
                sseService.sendError(conversationId, "处理消息时发生错误: " + e.getMessage());
            }
        }
    }
    
    /**
     * 生成新的会话ID
     * 
     * @return 会话ID
     */
    public String generateConversationId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 健康检查
     * 
     * @return 是否健康
     */
    public boolean isHealthy() {
        try {
            initChatClient();
            return chatClient != null;
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            return false;
        }
    }
}
