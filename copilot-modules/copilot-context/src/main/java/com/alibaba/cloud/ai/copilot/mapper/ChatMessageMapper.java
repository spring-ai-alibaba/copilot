package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天消息 Mapper
 *
 * @author better
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    /**
     * 根据会话ID查询历史消息（按创建时间升序）
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @Select("SELECT * FROM chat_message WHERE conversation_id = #{conversationId} " +
            "ORDER BY created_time ASC")
    List<ChatMessageEntity> selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 根据会话ID分页查询历史消息（按创建时间倒序，返回最新的消息）
     *
     * @param conversationId 会话ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 消息列表（按创建时间倒序）
     */
    @Select("SELECT * FROM chat_message WHERE conversation_id = #{conversationId} " +
            "ORDER BY created_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ChatMessageEntity> selectByConversationIdWithPagination(
            @Param("conversationId") String conversationId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 统计会话的消息总数
     *
     * @param conversationId 会话ID
     * @return 消息总数
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE conversation_id = #{conversationId}")
    int countByConversationId(@Param("conversationId") String conversationId);

    /**
     * 根据会话ID删除所有消息
     *
     * @param conversationId 会话ID
     * @return 删除数量
     */
    @Select("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}

