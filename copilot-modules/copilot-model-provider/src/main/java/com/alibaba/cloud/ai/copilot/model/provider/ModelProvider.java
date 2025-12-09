package com.alibaba.cloud.ai.copilot.model.provider;

import com.alibaba.cloud.ai.copilot.model.dto.DiscoveredModelInfo;
import com.alibaba.cloud.ai.copilot.model.dto.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.model.entity.ModelConfigEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * 统一的模型供应商接口
 * 支持多平台：OpenAI、通义千问、DeepSeek、Kimi、智谱AI、Siliconflow、Ollama 等
 *
 * @author Robust_H
 */
public interface ModelProvider {

    // ==================== 供应商信息 ====================

    /**
     * 获取供应商唯一标识
     * <p>必须与数据库 llm_factories.name 完全一致</p>
     *
     * @return 供应商标识，如 "OpenAI", "DeepSeek"
     */
    String getProviderName();

    /**
     * 获取默认的 API 基础 URL
     *
     * @return 默认 URL，如 "https://api.openai.com/v1"
     */
    default String getDefaultBaseUrl() {
        return null;
    }

    // ==================== 模型创建 ====================

    /**
     * 创建 ChatModel 实例
     *
     * @param config 模型配置（包含 apiKey, modelName, apiUrl 等）
     * @return ChatModel 实例
     */
    ChatModel createChatModel(ModelConfigEntity config);

    /**
     * 创建 ChatModel 实例（支持自定义选项）
     *
     * @param config  模型配置
     * @param options 自定义 ChatOptions，为 null 则使用默认选项
     * @return ChatModel 实例
     */
    default ChatModel createChatModel(ModelConfigEntity config, ChatOptions options) {
        return createChatModel(config);
    }

    // ==================== ChatOptions 构建 ====================

    /**
     * 创建 ChatOptions（自定义参数）
     *
     * @param config      模型配置
     * @param maxTokens   最大 token 数，null 则使用默认值
     * @param temperature 温度参数，null 则使用默认值
     * @return ChatOptions 实例
     */
    ChatOptions createChatOptions(ModelConfigEntity config, Integer maxTokens, Double temperature);

    /**
     * 创建默认的 ChatOptions
     *
     * @param config 模型配置
     * @return 默认 ChatOptions 实例
     */
    ChatOptions createDefaultChatOptions(ModelConfigEntity config);

    // ==================== 健康检查与模型发现 ====================

    /**
     * 健康检测（验证 API Key 是否有效）
     *
     * @param apiKey API 密钥
     * @return 健康检测结果
     */
    HealthCheckResult checkHealth(String apiKey);

    /**
     * 检测指定模型的健康状态
     * <p>用于验证用户自定义模型名称是否可用</p>
     *
     * @param apiKey    API 密钥
     * @param modelName 模型名称
     * @return 健康检测结果
     */
    HealthCheckResult checkModelHealth(String apiKey, String modelName);

    /**
     * 发现可用模型列表
     *
     * @return 模型信息列表
     */
    List<DiscoveredModelInfo> discoverModels();

    // ==================== 能力查询 ====================

    /**
     * 是否支持函数调用 (Function Calling / Tool Use)
     */
    default boolean supportsFunctionCalling() {
        return false;
    }

    /**
     * 是否支持多模态（图像、音频等）
     */
    default boolean supportsMultimodal() {
        return false;
    }

    /**
     * 是否支持流式输出
     */
    default boolean supportsStreaming() {
        return true;
    }
}
