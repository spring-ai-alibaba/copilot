package com.alibaba.cloud.ai.copilot.memory.shortterm.impl;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.memory.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.memory.shortterm.ChatMessageRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聊天消息仓储实现
 *
 * @author better
 */
@Slf4j
@Repository
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    public ChatMessageRepositoryImpl(ChatMessageMapper chatMessageMapper, ObjectMapper objectMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String conversationId, Message message) {
        try {
            // 检查内容长度（TEXT 类型最大约 65KB）
            if (message.getContent() != null && message.getContent().length() > 60000) {
                log.warn("Message content is very long ({} chars) for conversation {}, may cause issues",
                        message.getContent().length(), conversationId);
            }

            ChatMessageEntity entity = convertToEntity(conversationId, message);
            chatMessageMapper.insert(entity);

            if (log.isDebugEnabled()) {
                log.debug("Saved message {} for conversation {} (content length: {} chars)",
                        message.getId(), conversationId,
                        message.getContent() != null ? message.getContent().length() : 0);
            }
        } catch (Exception e) {
            log.error("Failed to save message for conversation " + conversationId, e);
            throw new RuntimeException("Failed to save message", e);
        }
    }

    @Override
    public List<Message> load(String conversationId) {
        try {
            LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessageEntity::getConversationId, conversationId)
                   .orderByAsc(ChatMessageEntity::getCreatedTime);

            List<ChatMessageEntity> entities = chatMessageMapper.selectList(wrapper);
            return entities.stream()
                    .map(this::convertToMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to load messages for conversation " + conversationId, e);
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replace(String conversationId, List<Message> messages) {
        try {
            // 获取新消息的 ID 集合
            Set<String> newMessageIds = messages.stream()
                    .map(Message::getId)
                    .collect(Collectors.toSet());

            // 加载现有消息
            List<ChatMessageEntity> existingEntities = chatMessageMapper.selectList(
                    new LambdaQueryWrapper<ChatMessageEntity>()
                            .eq(ChatMessageEntity::getConversationId, conversationId)
            );

            // 找出需要删除的消息（在新列表中不存在的）
            List<String> toDelete = existingEntities.stream()
                    .map(ChatMessageEntity::getMessageId)
                    .filter(id -> !newMessageIds.contains(id))
                    .collect(Collectors.toList());

            // 删除不再需要的消息
            if (!toDelete.isEmpty()) {
                LambdaQueryWrapper<ChatMessageEntity> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(ChatMessageEntity::getConversationId, conversationId)
                           .in(ChatMessageEntity::getMessageId, toDelete);
                chatMessageMapper.delete(deleteWrapper);
                log.debug("Deleted {} old messages for conversation {}", toDelete.size(), conversationId);
            }

            // 保存或更新新消息
            for (Message message : messages) {
                ChatMessageEntity entity = convertToEntity(conversationId, message);

                // 检查消息是否已存在
                LambdaQueryWrapper<ChatMessageEntity> checkWrapper = new LambdaQueryWrapper<>();
                checkWrapper.eq(ChatMessageEntity::getConversationId, conversationId)
                           .eq(ChatMessageEntity::getMessageId, message.getId());

                ChatMessageEntity existing = chatMessageMapper.selectOne(checkWrapper);
                if (existing != null) {
                    // 更新现有消息
                    entity.setId(existing.getId());
                    chatMessageMapper.updateById(entity);
                } else {
                    // 插入新消息
                    chatMessageMapper.insert(entity);
                }
            }

            log.debug("Replaced messages for conversation {}: {} messages", conversationId, messages.size());
        } catch (Exception e) {
            log.error("Failed to replace messages for conversation " + conversationId, e);
            throw new RuntimeException("Failed to replace messages", e);
        }
    }

    @Override
    public void delete(String conversationId) {
        try {
            LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessageEntity::getConversationId, conversationId);
            chatMessageMapper.delete(wrapper);
        } catch (Exception e) {
            log.error("Failed to delete messages for conversation " + conversationId, e);
        }
    }

    /**
     * 转换为实体
     */
    private ChatMessageEntity convertToEntity(String conversationId, Message message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(message.getId());
        entity.setRole(message.getRole());
        entity.setContent(message.getContent());
        entity.setCreatedTime(message.getTimestamp() != null ? message.getTimestamp() : LocalDateTime.now());

        // 序列化元数据
        if (message.getMetadata() != null) {
            try {
                entity.setMetadata(objectMapper.writeValueAsString(message.getMetadata()));
            } catch (Exception e) {
                log.warn("Failed to serialize message metadata", e);
            }
        }

        // 检查是否为压缩消息
        if (message.getMetadata() != null && "compression".equals(message.getMetadata().getSource())) {
            entity.setIsCompressed(true);
        }

        return entity;
    }

    /**
     * 转换为消息
     */
    private Message convertToMessage(ChatMessageEntity entity) {
        Message message = new Message();
        message.setId(entity.getMessageId());
        message.setRole(entity.getRole());
        message.setContent(entity.getContent());
        message.setTimestamp(entity.getCreatedTime());

        // 反序列化元数据
        if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
            try {
                Message.MessageMetadata metadata = objectMapper.readValue(
                        entity.getMetadata(),
                        new TypeReference<Message.MessageMetadata>() {}
                );
                message.setMetadata(metadata);
            } catch (Exception e) {
                log.warn("Failed to deserialize message metadata", e);
            }
        }

        return message;
    }
}

