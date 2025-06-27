package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 聊天消息数据模型
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
public class ChatMessage {
    
    private String id;
    
    @NotBlank(message = "消息内容不能为空")
    private String message;
    
    private String conversationId;
    
    private MessageType type;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        USER,      // 用户消息
        ASSISTANT, // AI助手消息
        SYSTEM     // 系统消息
    }
    
    // 构造函数
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ChatMessage(String message, MessageType type) {
        this();
        this.message = message;
        this.type = type;
    }
    
    public ChatMessage(String message, MessageType type, String conversationId) {
        this(message, type);
        this.conversationId = conversationId;
    }
    
    // Getter 和 Setter 方法
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", type=" + type +
                ", timestamp=" + timestamp +
                '}';
    }
}
