package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息数据类
 * 用于存储和传输聊天消息信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    /**
     * 消息角色 (user, assistant, system)
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 构造函数 - 不包含会话ID
     */
    public ChatMessage(String role, String content, LocalDateTime createdAt) {
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}
