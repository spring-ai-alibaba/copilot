package com.alibaba.cloud.ai.copilot.memory.token;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;

import java.util.List;

/**
 * Token 计数服务接口
 * 用于计算消息列表的 Token 数量
 *
 * @author better
 */
public interface TokenCounterService {

    /**
     * 计算消息列表的总 Token 数
     *
     * @param messages 消息列表
     * @param modelName 模型名称
     * @return Token 数量
     */
    int countTokens(List<Message> messages, String modelName);

    /**
     * 计算单个消息的 Token 数
     *
     * @param message 消息
     * @param modelName 模型名称
     * @return Token 数量
     */
    int countTokens(Message message, String modelName);

    /**
     * 计算文本的 Token 数
     *
     * @param text 文本内容
     * @param modelName 模型名称
     * @return Token 数量
     */
    int countTokens(String text, String modelName);

    /**
     * 获取模型的 Token 限制
     *
     * @param modelName 模型名称
     * @return Token 限制数量
     */
    int getModelTokenLimit(String modelName);
}

