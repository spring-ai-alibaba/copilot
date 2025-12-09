package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.model.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.service.OpenAiModelFactory;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * OpenAI模型工厂服务实现类
 * 提供统一的OpenAI模型创建和配置方法
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
    public ChatModel createChatModel(String modelName, String userId) {
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

            // 构建OpenAI API
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                    .apiKey(apiKey);

            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                apiBuilder.baseUrl(baseUrl);
            }

            OpenAiApi openAiApi = apiBuilder.build();

            // 创建默认的ChatOptions
            OpenAiChatOptions defaultOptions = createDefaultChatOptions(modelName);

            // 创建必需的组件
            ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
            RetryTemplate retryTemplate = RetryTemplate.builder().build();
            ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

            return new OpenAiChatModel(openAiApi, defaultOptions, toolCallingManager, retryTemplate, observationRegistry);

        } catch (Exception e) {
            log.error("Failed to create OpenAI model for {}, user: {}", modelName, userId, e);
            throw new RuntimeException("Failed to create OpenAI model", e);
        }
    }

    @Override
    public ChatModel createChatModel(String modelName) {
        return createChatModel(modelName, null);
    }

    @Override
    public OpenAiChatOptions createChatOptions(String modelName, Integer maxTokens, Double temperature) {
        return OpenAiChatOptions.builder()
                .model(modelName)
                .maxTokens(maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .build();
    }

    @Override
    public OpenAiChatOptions createDefaultChatOptions(String modelName) {
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
