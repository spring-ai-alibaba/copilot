package com.alibaba.cloud.ai.copilot.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI模型工厂服务接口
 * 提供统一的OpenAI模型创建和配置方法
 */
public interface OpenAiModelFactory {

    /**
     * 根据模型名称和用户ID创建ChatModel
     * 
     * @param modelName 模型名称
     * @param userId 用户ID
     * @return ChatModel实例
     */
    ChatModel createChatModel(String modelName, String userId);

    /**
     * 根据模型名称创建ChatModel（使用默认配置）
     * 
     * @param modelName 模型名称
     * @return ChatModel实例
     */
    ChatModel createChatModel(String modelName);

    /**
     * 创建标准的OpenAI聊天选项
     * 
     * @param modelName 模型名称
     * @param maxTokens 最大token数
     * @param temperature 温度参数
     * @return OpenAiChatOptions实例
     */
    OpenAiChatOptions createChatOptions(String modelName, Integer maxTokens, Double temperature);

    /**
     * 创建默认的OpenAI聊天选项
     * 
     * @param modelName 模型名称
     * @return OpenAiChatOptions实例
     */
    OpenAiChatOptions createDefaultChatOptions(String modelName);
}
