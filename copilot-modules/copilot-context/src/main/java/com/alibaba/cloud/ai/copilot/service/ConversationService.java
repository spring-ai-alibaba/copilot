package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;

import java.util.List;

/**
 * 会话服务接口
 *
 * @author better
 */
public interface ConversationService {

    /**
     * 创建会话
     *
     * @param userId 用户ID
     * @param request 创建请求
     * @return 会话ID
     */
    String createConversation(Long userId, CreateConversationRequest request);

    /**
     * 获取会话信息
     *
     * @param conversationId 会话ID
     * @return 会话DTO
     */
    ConversationDTO getConversation(String conversationId);

    /**
     * 分页查询用户会话列表
     *
     * @param userId 用户ID
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<ConversationDTO> listConversations(Long userId, int page, int size);

    /**
     * 获取会话历史消息（包含权限验证）
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 消息列表
     * @throws IllegalArgumentException 如果会话不存在或用户无权限
     */
    List<ChatMessage> getConversationMessages(String conversationId, Long userId);

    /**
     * 验证用户是否有权限访问会话
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @throws IllegalArgumentException 如果会话不存在或用户无权限
     */
    void checkConversationPermission(String conversationId, Long userId);

    /**
     * 更新会话标题（包含权限验证）
     *
     * @param conversationId 会话ID
     * @param title 标题
     * @param userId 用户ID
     * @throws IllegalArgumentException 如果会话不存在或用户无权限
     */
    void updateConversationTitle(String conversationId, String title, Long userId);

    /**
     * 删除会话（软删除，包含权限验证）
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @throws IllegalArgumentException 如果会话不存在或用户无权限
     */
    void deleteConversation(String conversationId, Long userId);

    /**
     * 增加消息计数
     *
     * @param conversationId 会话ID
     */
    void incrementMessageCount(String conversationId);
}

