package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.service.OpenAiModelFactory;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OpenAI模型工厂服务实现类
 * 提供统一的OpenAI兼容模型创建和配置方法（agentscope {@link Model}）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiModelFactoryImpl implements OpenAiModelFactory {

    private final ModelConfigService modelConfigService;

    // 默认配置常量 - 根据不同模型设置合理的token限制
    private static final int DEFAULT_MAX_TOKENS = 64000;  // 通用默认值，适用于大多数模型
    private static final int DEEPSEEK_MAX_TOKENS = 64000;  // DeepSeek 模型的最大输出 token 限制
    private static final int OPENAI_MAX_TOKENS = 64000;    // OpenAI 模型的默认值
    private static final double DEFAULT_TEMPERATURE = 0.7;

    @Override
    public Model createChatModel(String modelName, String userId) {
        try {
            ModelConfigEntity modelEntity = modelConfigService.getModelEntityByName(modelName);

            if (modelEntity == null) {
                throw new IllegalArgumentException("Model configuration not found for: " + modelName);
            }

            String apiKey = modelEntity.getApiKey();
            String baseUrl = modelEntity.getApiUrl();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("API key not found for model: " + modelName);
            }

            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(true)
                    .generateOptions(createDefaultChatOptions(modelName));

            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                builder.baseUrl(baseUrl);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to create agentscope model for {}, user: {}", modelName, userId, e);
            throw new RuntimeException("Failed to create agentscope model", e);
        }
    }

    @Override
    public Model createChatModel(String modelName) {
        return createChatModel(modelName, null);
    }

    @Override
    public GenerateOptions createChatOptions(String modelName, Integer maxTokens, Double temperature) {
        return GenerateOptions.builder()
                .modelName(modelName)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .build();
    }

    @Override
    public GenerateOptions createDefaultChatOptions(String modelName) {
        // 根据模型名称智能选择合适的 max_tokens
        int maxTokens = getMaxTokensForModel(modelName);
        return createChatOptions(modelName, maxTokens, DEFAULT_TEMPERATURE);
    }

    /**
     * 根据模型名称获取合适的 max_tokens 值
     * 避免超出各个模型 API 的限制
     */
    private int getMaxTokensForModel(String modelName) {
        if (modelName == null) {
            return DEFAULT_MAX_TOKENS;
        }

        String lowerModelName = modelName.toLowerCase();

        // DeepSeek 模型
        if (lowerModelName.contains("deepseek")) {
            return DEEPSEEK_MAX_TOKENS;
        }

        // OpenAI 模型
        if (lowerModelName.contains("gpt")) {
            return OPENAI_MAX_TOKENS;
        }

        // 其他模型使用默认值
        return DEFAULT_MAX_TOKENS;
    }
}
