package com.alibaba.cloud.ai.copilot.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * 动态模型服务接口
 * 根据配置动态创建和获取 AI 模型实例
 */
public interface DynamicModelService {

    // ==================== 根据模型名称获取 ====================

    /**
     * 根据模型名称和用户 ID 获取 ChatModel
     *
     * @param modelName 模型名称（如 gpt-4, deepseek-chat）
     * @param userId    用户 ID，用于获取用户特定的配置
     * @return ChatModel 实例
     */
    ChatModel getChatModel(String modelName, String userId);

    /**
     * 根据模型名称获取默认的 ChatModel
     *
     * @param modelName 模型名称
     * @return ChatModel 实例
     */
    default ChatModel getChatModel(String modelName) {
        return getChatModel(modelName, null);
    }

    // ==================== 根据配置 ID 获取 ====================

    /**
     * 根据配置 ID 获取 ChatModel
     *
     * @param configId 配置 ID（model_config.id）
     * @return ChatModel 实例
     */
    ChatModel getChatModelWithConfigId(String configId);

    /**
     * 根据配置 ID 获取 ChatModel（支持自定义选项）
     *
     * @param configId 配置 ID
     * @param options  自定义 ChatOptions，为 null 则使用默认选项
     * @return ChatModel 实例
     */
    ChatModel getChatModelWithConfigId(String configId, ChatOptions options);

    // ==================== 缓存管理 ====================

    /**
     * 刷新所有模型缓存
     */
    void refreshModelCache();

    /**
     * 根据配置 ID 刷新单个模型缓存
     *
     * @param configId 配置 ID
     */
    void refreshModelCacheById(String configId);

    // ==================== 状态查询 ====================

    /**
     * 检查模型是否可用
     *
     * @param modelName 模型名称
     * @param userId    用户 ID
     * @return true 如果模型可用
     */
    boolean isModelAvailable(String modelName, String userId);
}
