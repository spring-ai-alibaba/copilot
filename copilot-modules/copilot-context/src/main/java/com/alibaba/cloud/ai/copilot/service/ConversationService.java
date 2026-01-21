package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;

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
     * 更新会话标题
     *
     * @param conversationId 会话ID
     * @param title 标题
     */
    void updateConversationTitle(String conversationId, String title);

    /**
     * 删除会话（软删除）
     *
     * @param conversationId 会话ID
     */
    void deleteConversation(String conversationId);

    /**
     * 增加消息计数
     *
     * @param conversationId 会话ID
     */
    void incrementMessageCount(String conversationId);
}

