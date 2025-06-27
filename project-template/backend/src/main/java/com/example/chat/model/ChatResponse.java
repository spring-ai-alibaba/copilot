package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 聊天响应数据模型
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
public class ChatResponse {
    
    private String conversationId;
    private String message;
    private boolean isComplete;
    private ResponseStatus status;
    private String errorMessage;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 响应状态枚举
     */
    public enum ResponseStatus {
        SUCCESS,    // 成功
        PROCESSING, // 处理中
        ERROR,      // 错误
        TIMEOUT     // 超时
    }
    
    // 构造函数
    public ChatResponse() {
        this.timestamp = LocalDateTime.now();
        this.status = ResponseStatus.PROCESSING;
        this.isComplete = false;
    }
    
    public ChatResponse(String conversationId) {
        this();
        this.conversationId = conversationId;
    }
    
    public ChatResponse(String conversationId, String message) {
        this(conversationId);
        this.message = message;
    }
    
    // 静态工厂方法
    public static ChatResponse success(String conversationId, String message) {
        ChatResponse response = new ChatResponse(conversationId, message);
        response.setStatus(ResponseStatus.SUCCESS);
        response.setComplete(true);
        return response;
    }
    
    public static ChatResponse processing(String conversationId) {
        ChatResponse response = new ChatResponse(conversationId);
        response.setStatus(ResponseStatus.PROCESSING);
        return response;
    }
    
    public static ChatResponse error(String conversationId, String errorMessage) {
        ChatResponse response = new ChatResponse(conversationId);
        response.setStatus(ResponseStatus.ERROR);
        response.setErrorMessage(errorMessage);
        response.setComplete(true);
        return response;
    }
    
    // Getter 和 Setter 方法
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public void setComplete(boolean complete) {
        isComplete = complete;
    }
    
    public ResponseStatus getStatus() {
        return status;
    }
    
    public void setStatus(ResponseStatus status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "ChatResponse{" +
                "conversationId='" + conversationId + '\'' +
                ", message='" + message + '\'' +
                ", isComplete=" + isComplete +
                ", status=" + status +
                ", errorMessage='" + errorMessage + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
