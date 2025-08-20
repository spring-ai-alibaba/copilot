package com.alibaba.cloud.ai.copilot.service;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 动态模型服务接口
 * 用于根据配置动态创建和获取AI模型
 */
public interface DynamicModelService {

    /**
     * 根据模型名称和用户ID获取对应的ChatModel
     * 
     * @param modelName 模型名称 (如: gpt-3.5-turbo, gpt-4, etc.)
     * @param userId 用户ID，用于获取用户特定的API配置
     * @return ChatModel实例
     */
    ChatModel getChatModel(String modelName, String userId);

    /**
     * 根据模型名称获取默认的ChatModel
     * 
     * @param modelName 模型名称
     * @return ChatModel实例
     */
    ChatModel getChatModel(String modelName);

    /**
     * 刷新模型缓存
     * 当数据库中的配置发生变化时调用
     */
    void refreshModelCache();

    /**
     * 检查模型是否可用
     * 
     * @param modelName 模型名称
     * @param userId 用户ID
     * @return 是否可用
     */
    boolean isModelAvailable(String modelName, String userId);
}
