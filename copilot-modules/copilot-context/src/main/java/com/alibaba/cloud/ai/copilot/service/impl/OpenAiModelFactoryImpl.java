package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
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

    // 默认配置常量 - 增加token限制以支持完整代码生成
    private static final int DEFAULT_MAX_TOKENS = 100000;  // 增加到32K
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
        return createChatOptions(modelName, DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE);
    }
}
