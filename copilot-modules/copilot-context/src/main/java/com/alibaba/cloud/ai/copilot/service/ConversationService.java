package com.alibaba.cloud.ai.copilot.service;

import java.util.List;

/**
 * 会话管理服务接口
 * 负责管理用户会话ID和会话历史
 */
public interface ConversationService {
    
    /**
     * 为用户生成或获取会话ID
     * @param userId 用户ID
     * @return 会话ID
     */
    String getOrCreateConversationId(String userId);
    
    /**
     * 为用户创建新的会话
     * @param userId 用户ID
     * @return 新的会话ID
     */
    String createNewConversation(String userId);
    
    /**
     * 获取用户的所有会话ID
     * @param userId 用户ID
     * @return 会话ID列表
     */
    List<String> getUserConversations(String userId);
    
    /**
     * 删除指定会话
     * @param conversationId 会话ID
     */
    void deleteConversation(String conversationId);
    
    /**
     * 清除用户的所有会话
     * @param userId 用户ID
     */
    void clearUserConversations(String userId);
}
