package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 聊天记忆会话管理服务实现
 * 使用Redis存储会话信息
 */
@Slf4j
@Service("chatMemoryConversationService")
public class ChatMemoryConversationServiceImpl implements ConversationService {
    
    private static final String USER_CONVERSATION_KEY_PREFIX = "user:conversations:";
    private static final String CURRENT_CONVERSATION_KEY_PREFIX = "user:current:conversation:";
    private static final long CONVERSATION_EXPIRE_HOURS = 24; // 会话过期时间24小时
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public ChatMemoryConversationServiceImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public String getOrCreateConversationId(String userId) {
        String currentConversationKey = CURRENT_CONVERSATION_KEY_PREFIX + userId;
        String conversationId = redisTemplate.opsForValue().get(currentConversationKey);
        
        if (conversationId == null) {
            conversationId = createNewConversation(userId);
        }
        
        return conversationId;
    }
    
    @Override
    public String createNewConversation(String userId) {
        String conversationId = generateConversationId(userId);
        String userConversationsKey = USER_CONVERSATION_KEY_PREFIX + userId;
        String currentConversationKey = CURRENT_CONVERSATION_KEY_PREFIX + userId;
        
        // 添加到用户会话列表
        redisTemplate.opsForSet().add(userConversationsKey, conversationId);
        redisTemplate.expire(userConversationsKey, CONVERSATION_EXPIRE_HOURS, TimeUnit.HOURS);
        
        // 设置为当前会话
        redisTemplate.opsForValue().set(currentConversationKey, conversationId, 
                                      CONVERSATION_EXPIRE_HOURS, TimeUnit.HOURS);
        
        log.info("Created new conversation {} for user {}", conversationId, userId);
        return conversationId;
    }
    
    @Override
    public List<String> getUserConversations(String userId) {
        String userConversationsKey = USER_CONVERSATION_KEY_PREFIX + userId;
        Set<String> conversations = redisTemplate.opsForSet().members(userConversationsKey);
        return conversations != null ? conversations.stream().toList() : List.of();
    }
    
    @Override
    public void deleteConversation(String conversationId) {
        // 从会话ID中提取用户ID
        String userId = extractUserIdFromConversationId(conversationId);
        if (userId != null) {
            String userConversationsKey = USER_CONVERSATION_KEY_PREFIX + userId;
            redisTemplate.opsForSet().remove(userConversationsKey, conversationId);
            
            // 如果删除的是当前会话，清除当前会话标记
            String currentConversationKey = CURRENT_CONVERSATION_KEY_PREFIX + userId;
            String currentConversation = redisTemplate.opsForValue().get(currentConversationKey);
            if (conversationId.equals(currentConversation)) {
                redisTemplate.delete(currentConversationKey);
            }
            
            log.info("Deleted conversation {} for user {}", conversationId, userId);
        }
    }
    
    @Override
    public void clearUserConversations(String userId) {
        String userConversationsKey = USER_CONVERSATION_KEY_PREFIX + userId;
        String currentConversationKey = CURRENT_CONVERSATION_KEY_PREFIX + userId;
        
        redisTemplate.delete(userConversationsKey);
        redisTemplate.delete(currentConversationKey);
        
        log.info("Cleared all conversations for user {}", userId);
    }
    
    /**
     * 生成会话ID
     * 格式: conv_{userId}_{timestamp}_{uuid}
     */
    private String generateConversationId(String userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("conv_%s_%s_%s", userId, timestamp, uuid);
    }
    
    /**
     * 从会话ID中提取用户ID
     */
    private String extractUserIdFromConversationId(String conversationId) {
        if (conversationId != null && conversationId.startsWith("conv_")) {
            String[] parts = conversationId.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
