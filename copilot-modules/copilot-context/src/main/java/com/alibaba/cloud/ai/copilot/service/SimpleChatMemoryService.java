package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.ChatMessage;

import java.util.List;

/**
 * 简单聊天记忆服务接口
 * 提供基本的消息存储和检索功能
 */
public interface SimpleChatMemoryService {
    
    /**
     * 添加用户消息
     * @param userId 用户ID
     * @param content 消息内容
     */
    void addUserMessage(String userId, String content);
    
    /**
     * 添加助手消息
     * @param userId 用户ID
     * @param content 消息内容
     */
    void addAssistantMessage(String userId, String content);
    
    /**
     * 添加系统消息
     * @param userId 用户ID
     * @param content 消息内容
     */
    void addSystemMessage(String userId, String content);
    
    /**
     * 获取用户的对话历史
     * @param userId 用户ID
     * @return 对话历史列表
     */
    List<ChatMessage> getConversationHistory(String userId);
    
    /**
     * 获取用户的对话历史（限制数量）
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 对话历史列表
     */
    List<ChatMessage> getConversationHistory(String userId, int limit);
    
    /**
     * 清除用户的对话历史
     * @param userId 用户ID
     */
    void clearConversationHistory(String userId);
    
    /**
     * 获取指定会话的消息数量
     * @param userId 用户ID
     * @return 消息数量
     */
    int getMessageCount(String userId);
}
