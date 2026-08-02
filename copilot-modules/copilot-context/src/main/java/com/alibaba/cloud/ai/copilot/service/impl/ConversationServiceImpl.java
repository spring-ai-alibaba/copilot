package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.ConversationEntity;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final String AGENT_SCOPE_TABLE = "agentscope_sessions";
    private static final String ANONYMOUS_USER = "__anon__";

    private final ChatMessageMapper chatMessageMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SessionRunGuard sessionRunGuard;
    private final PlatformTransactionManager transactionManager;

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
        return persistConversation(userId, request, conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 15)
    public String createConversation(
            Long userId,
            CreateConversationRequest request,
            String conversationId,
            SessionRunGuard.Lease lease) {
        lease.fenceCurrentTransaction();
        return persistConversation(userId, request, conversationId);
    }

    private String persistConversation(
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
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw new ServiceException("无法获取会话删除锁", 503)
                    .setDetailMessage(e.getMessage());
        }
        try {
            // Start the DB transaction only after acquiring the lease. Starting it earlier can
            // pin one pool connection per request while the guard waits for another connection.
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            // execute() must return only after this deletion commits/rolls back; otherwise an
            // ambient transaction could keep the fence row locked after the lease is released.
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setTimeout(15);
            transaction.executeWithoutResult(status -> {
                // Permission/existence must be checked while holding the lease. A run or delete
                // may have passed its first check and then waited behind another operation.
                checkConversationPermission(conversationId, userId);
                deleteConversationWithState(conversationId, userId, lease);
            });
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw new ServiceException("会话删除期间锁已失效", 503)
                    .setDetailMessage(e.getMessage());
        } finally {
            // TransactionTemplate has completed commit or rollback before returning.
            lease.close();
        }
    }

    private void deleteConversationWithState(
            String conversationId,
            Long userId,
            SessionRunGuard.Lease lease) {

        // Lock the exact lease row in this Spring transaction before its first mutation. The row
        // lock is held through commit/rollback, so a replica takeover cannot interleave with these
        // business and AgentScope state writes after a long JVM pause.
        // Deletion is terminal for this lease, so it does not need post-transaction renewal.
        lease.stopRenewal();
        lease.fenceCurrentTransaction();

        // 软删除
        lease.assertOwned();
        boolean deleted = update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .eq(ConversationEntity::getUserId, userId)
            .eq(ConversationEntity::getDelFlag, 0)
            .set(ConversationEntity::getDelFlag, 1)
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
        if (!deleted) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        lease.assertOwned();

        try {
            // JdbcTemplate participates in the surrounding Spring transaction, so the business
            // soft delete and all AgentScope namespaces commit or roll back together.
            lease.assertOwned();
            String userKey = String.valueOf(userId);
            jdbcTemplate.update(
                    "DELETE FROM " + AGENT_SCOPE_TABLE + " WHERE session_id IN (?, ?, ?)",
                    agentScopeSlot(ANONYMOUS_USER, conversationId),
                    agentScopeSlot(userKey, conversationId),
                    agentScopeSlot(
                            userKey,
                            ConversationStateNamespace.contextMetaSessionId(conversationId)));
            lease.assertOwned();
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除会话 AgentScope 状态失败: conversationId={}, userId={}", conversationId, userId, e);
            throw new IllegalStateException("删除会话上下文失败", e);
        }
    }

    private static String agentScopeSlot(String userId, String sessionId) {
        return userId + ":" + sessionId;
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

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 15)
    public void appendUserMessage(
            String conversationId,
            String content,
            String modelConfigId,
            Long userId,
            SessionRunGuard.Lease lease) {
        lease.fenceCurrentTransaction();

        final Long requestedModelId;
        try {
            requestedModelId = Long.valueOf(modelConfigId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("模型配置无效", e);
        }

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole("user");
        entity.setContent(content);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        chatMessageMapper.insert(entity);

        boolean updated = update(new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getConversationId, conversationId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::getDelFlag, 0)
                // Legacy clients could pre-create model-less conversations. Bind exactly once;
                // an already-bound conversation must keep using the same model.
                .and(wrapper -> wrapper
                        .eq(ConversationEntity::getModelConfigId, requestedModelId)
                        .or()
                        .isNull(ConversationEntity::getModelConfigId))
                .set(ConversationEntity::getModelConfigId, requestedModelId)
                .setSql("message_count = IFNULL(message_count, 0) + 1")
                .set(ConversationEntity::getLastMessageTime, LocalDateTime.now())
                .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
        if (!updated) {
            throw new IllegalArgumentException("会话不存在、无权访问或模型绑定不一致");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 15)
    public void persistSuccessfulAssistantTurn(
            String conversationId,
            String content,
            String firstUserMessage,
            Long userId,
            SessionRunGuard.Lease lease) {
        lease.fenceCurrentTransaction();

        if (content != null && !content.isBlank()) {
            ChatMessageEntity entity = new ChatMessageEntity();
            entity.setConversationId(conversationId);
            entity.setMessageId(UUID.randomUUID().toString());
            entity.setRole("assistant");
            entity.setContent(content);
            entity.setCreatedTime(LocalDateTime.now());
            entity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(entity);
        }

        String title = firstUserMessage.length() > 50
                ? firstUserMessage.substring(0, 50) + "..."
                : firstUserMessage;
        update(new LambdaUpdateWrapper<ConversationEntity>()
                .eq(ConversationEntity::getConversationId, conversationId)
                .eq(ConversationEntity::getUserId, userId)
                .eq(ConversationEntity::getDelFlag, 0)
                .and(wrapper -> wrapper
                        .eq(ConversationEntity::getTitle, "新对话")
                        .or()
                        .isNull(ConversationEntity::getTitle))
                .set(ConversationEntity::getTitle, title)
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

