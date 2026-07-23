package com.alibaba.cloud.ai.copilot.service;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

/**
 * OpenAI模型工厂服务接口
 * 提供统一的OpenAI兼容模型创建和配置方法（agentscope {@link Model}）
 */
public interface OpenAiModelFactory {

    /**
     * 根据模型名称和用户ID创建 agentscope {@link Model}
     *
     * @param modelName 模型名称
     * @param userId 用户ID
     * @return agentscope {@link Model} 实例
     */
    Model createChatModel(String modelName, String userId);

    /**
     * 根据模型名称创建 agentscope {@link Model}（使用默认配置）
     *
     * @param modelName 模型名称
     * @return agentscope {@link Model} 实例
     */
    Model createChatModel(String modelName);

    /**
     * 创建标准的 GenerateOptions
     *
     * @param modelName 模型名称
     * @param maxTokens 最大token数
     * @param temperature 温度参数
     * @return GenerateOptions 实例
     */
    GenerateOptions createChatOptions(String modelName, Integer maxTokens, Double temperature);

    /**
     * 创建默认的 GenerateOptions
     *
     * @param modelName 模型名称
     * @return GenerateOptions 实例
     */
    GenerateOptions createDefaultChatOptions(String modelName);
}
