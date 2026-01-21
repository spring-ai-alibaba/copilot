package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ConversationEntity;
import com.alibaba.cloud.ai.copilot.mapper.ConversationMapper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createConversation(Long userId, CreateConversationRequest request) {
        // 生成会话ID
        String conversationId = UUID.randomUUID().toString().replace("-", "");

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
    @Transactional(rollbackFor = Exception.class)
    public void updateConversationTitle(String conversationId, String title) {
        update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .set(ConversationEntity::getTitle, title)
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId) {
        // 软删除
        update(new LambdaUpdateWrapper<ConversationEntity>()
            .eq(ConversationEntity::getConversationId, conversationId)
            .set(ConversationEntity::getDelFlag, 1)
            .set(ConversationEntity::getUpdatedTime, LocalDateTime.now()));
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

