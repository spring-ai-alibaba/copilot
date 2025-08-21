package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.ChatMessage;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SimpleChatMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 简单聊天记忆服务实现
 * 使用JDBC直接操作数据库存储聊天记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimpleChatMemoryServiceImpl implements SimpleChatMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ConversationService conversationService;

    private static final int DEFAULT_MESSAGE_LIMIT = 20;

    @Override
    public void addUserMessage(String userId, String content) {
        addMessage(userId, "user", content);
    }

    @Override
    public void addAssistantMessage(String userId, String content) {
        addMessage(userId, "assistant", content);
    }

    @Override
    public void addSystemMessage(String userId, String content) {
        addMessage(userId, "system", content);
    }

    @Transactional
    protected void addMessage(String userId, String role, String content) {
        String conversationId = conversationService.getOrCreateConversationId(userId);

        try {
            jdbcTemplate.update(
                "INSERT INTO chat_messages (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                conversationId, role, content, Timestamp.valueOf(LocalDateTime.now())
            );

            log.debug("Added {} message to conversation {}: {}", role, conversationId,
                     content.length() > 100 ? content.substring(0, 100) + "..." : content);

            // 清理超出限制的旧消息
            cleanupOldMessages(conversationId);

        } catch (Exception e) {
            log.error("Failed to add message to conversation {}: {}", conversationId, e.getMessage());
            throw new RuntimeException("Failed to add message", e);
        }
    }

    @Override
    public List<ChatMessage> getConversationHistory(String userId) {
        return getConversationHistory(userId, DEFAULT_MESSAGE_LIMIT);
    }

    @Override
    public List<ChatMessage> getConversationHistory(String userId, int limit) {
        String conversationId = conversationService.getOrCreateConversationId(userId);

        try {
            return jdbcTemplate.query(
                "SELECT role, content, created_at FROM chat_messages " +
                "WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?",
                new Object[]{conversationId, limit},
                (rs, rowNum) -> new ChatMessage(
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getTimestamp("created_at").toLocalDateTime()
                )
            );
        } catch (Exception e) {
            log.error("Failed to get conversation history for {}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional
    public void clearConversationHistory(String userId) {
        String conversationId = conversationService.getOrCreateConversationId(userId);

        try {
            int deletedCount = jdbcTemplate.update(
                "DELETE FROM chat_messages WHERE conversation_id = ?",
                conversationId
            );
            log.info("Cleared {} messages from conversation {}", deletedCount, conversationId);
        } catch (Exception e) {
            log.error("Failed to clear conversation history for {}: {}", conversationId, e.getMessage());
            throw new RuntimeException("Failed to clear conversation history", e);
        }
    }

    @Override
    public int getMessageCount(String userId) {
        String conversationId = conversationService.getOrCreateConversationId(userId);

        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?",
                Integer.class,
                conversationId
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Failed to get message count for {}: {}", conversationId, e.getMessage());
            return 0;
        }
    }

    /**
     * 清理超出限制的旧消息
     * 保留最近的消息，删除超出限制的旧消息
     */
    private void cleanupOldMessages(String conversationId) {
        try {
            // 使用子查询删除超出限制的旧消息
            int deletedCount = jdbcTemplate.update(
                "DELETE FROM chat_messages WHERE conversation_id = ? " +
                "AND id NOT IN (" +
                "  SELECT id FROM (" +
                "    SELECT id FROM chat_messages " +
                "    WHERE conversation_id = ? " +
                "    ORDER BY created_at DESC " +
                "    LIMIT ?" +
                "  ) t" +
                ")",
                conversationId, conversationId, DEFAULT_MESSAGE_LIMIT
            );

            if (deletedCount > 0) {
                log.debug("Cleaned up {} old messages from conversation {}", deletedCount, conversationId);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old messages for conversation {}: {}", conversationId, e.getMessage());
        }
    }
}
