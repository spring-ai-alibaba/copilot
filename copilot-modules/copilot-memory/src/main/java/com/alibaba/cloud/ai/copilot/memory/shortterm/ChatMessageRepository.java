package com.alibaba.cloud.ai.copilot.memory.shortterm;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;

import java.util.List;

/**
 * 聊天消息仓储接口
 * 负责消息的持久化存储
 *
 * @author better
 */
public interface ChatMessageRepository {

    /**
     * 保存消息
     */
    void save(String conversationId, Message message);

    /**
     * 加载对话的所有消息
     */
    List<Message> load(String conversationId);

    /**
     * 替换对话的所有消息
     */
    void replace(String conversationId, List<Message> messages);

    /**
     * 删除对话的所有消息
     */
    void delete(String conversationId);
}

