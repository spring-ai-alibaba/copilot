package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.ConversationEntity;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.mapper.ConversationMapper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话服务实现
 *
 * @author better
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, ConversationEntity> 
        implements ConversationService {

    private final ChatMessageMapper chatMessageMapper;
    private final AgentStateStore agentStateStore;
    private final SessionRunGuard sessionRunGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createConversation(Long userId, CreateConversationRequest request) {
        String conversationId = UUID.randomUUID().toString().replace("-", "");
        return createConversation(userId, request, conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createConversation(
            Long userId,
            CreateConversationRequest request,
            String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }

        // 创建会话实体
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(conversationId);
        entity.setUserId(userId);
        entity.setModelConfigId(request.getModelConfigId() != null 
            ? Long.parseLong(request.getModelConfigId()) : null);
        entity.setTitle("新对话");
        entity.setMessageCount(0);
        entity.setDelFlag(0);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());

        // 保存到数据库
        save(entity);

        log.info("创建会话成功: conversationId={}, userId={}", conversationId, userId);
        return conversationId;
    }

    @Override
    public ConversationDTO getConversation(String conversationId) {
        ConversationEntity entity = getOne(new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .eq(ConversationEntity::getDelFlag, 0));

        if (entity == null) {
            return null;
        }

        return convertToDTO(entity);
    }

    @Override
    public PageResult<ConversationDTO> listConversations(Long userId, int page, int size) {
        // 构建分页查询
        Page<ConversationEntity> pageParam = new Page<>(page, size);
        IPage<ConversationEntity> pageResult = page(pageParam, new LambdaQueryWrapper<ConversationEntity>()
            .eq(ConversationEntity::getUserId, userId)
            .eq(ConversationEntity::getDelFlag, 0)
            .orderByDesc(ConversationEntity::getUpdatedTime));

        // 转换为DTO列表
        return PageResult.<ConversationDTO>builder()
            .records(pageResult.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()))
            .total(pageResult.getTotal())
            .current(pageResult.getCurrent())
            .size(pageResult.getSize())
            .build();
    }

    @Override
    public List<ChatMessage> getConversationMessages(String conversationId, Long userId) {
        // 验证权限
        checkConversationPermission(conversationId, userId);

        // 从数据库读取历史消息（已在SQL中过滤tool/system角色和空内容）
        List<ChatMessageEntity> entities = chatMessageMapper.selectByConversationIdForDisplay(conversationId);

        // 转换为 DTO
        return entities.stream()
            .map(entity -> {
                ChatMessage dto = new ChatMessage();
                dto.setRole(entity.getRole());
                dto.setContent(entity.getContent());
                dto.setConversationId(conversationId);
                dto.setCreatedAt(entity.getCreatedTime());
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Override
    public void checkConversationPermission(String conversationId, Long userId) {
        ConversationDTO conversation = getConversation(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConversationTitle(String conversationId, String title, Long userId) {
        // 验证权限
        checkConversationPermission(conversationId, userId);

        update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .set(ConversationEntity::getTitle, title)
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId, Long userId) {
        // 验证权限
        checkConversationPermission(conversationId, userId);

        // Serialize deletion with inference and state persistence. Acquiring a short
        // lease also closes the check-then-delete race with a run that starts concurrently.
        SessionRunGuard.Lease lease;
        try {
            lease = sessionRunGuard.acquire(
                    String.valueOf(userId), conversationId, "delete-" + UUID.randomUUID());
        } catch (SessionRunGuard.SessionRunConflictException e) {
            throw new ServiceException("会话正在处理中，请稍后重试", 409);
        }
        try {
            // Permission/existence must be checked while holding the lease. A run or delete
            // may have passed its first check and then waited behind another operation.
            checkConversationPermission(conversationId, userId);
            deleteConversationWithState(conversationId, userId);
        } finally {
            lease.close();
        }
    }

    private void deleteConversationWithState(String conversationId, Long userId) {

        // 软删除
        boolean deleted = update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .eq(ConversationEntity::getUserId, userId)
            .eq(ConversationEntity::getDelFlag, 0)
            .set(ConversationEntity::getDelFlag, 1)
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
        if (!deleted) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        try {
            // Clean up the pre-authentication AgentScope key after ownership has been
            // verified. New requests never read this legacy namespace.
            agentStateStore.delete(null, conversationId);
            agentStateStore.delete(String.valueOf(userId), conversationId);
        } catch (Exception e) {
            log.error("删除会话 AgentScope 状态失败: conversationId={}, userId={}", conversationId, userId, e);
            throw new IllegalStateException("删除会话上下文失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementMessageCount(String conversationId) {
        // 使用数据库原子操作，避免并发问题
        update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .setSql("message_count = IFNULL(message_count, 0) + 1")
            .set(ConversationEntity::getLastMessageTime, LocalDateTime.now())
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
    }

    /**
     * 实体转DTO
     */
    private ConversationDTO convertToDTO(ConversationEntity entity) {
        return ConversationDTO.builder()
            .conversationId(entity.getConversationId())
            .userId(entity.getUserId())
            .title(entity.getTitle())
            .modelConfigId(entity.getModelConfigId())
            .messageCount(entity.getMessageCount())
            .lastMessageTime(entity.getLastMessageTime())
            .createdTime(entity.getCreatedTime())
            .updatedTime(entity.getUpdatedTime())
            .build();
    }
}

