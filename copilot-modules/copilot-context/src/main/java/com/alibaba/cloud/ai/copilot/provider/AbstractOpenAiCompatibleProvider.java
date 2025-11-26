package com.alibaba.cloud.ai.copilot.provider;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.copilot.dto.DiscoveredModelInfo;
import com.alibaba.cloud.ai.copilot.dto.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.entity.LlmEntity;
import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI 兼容协议供应商抽象基类
 * 适用于所有兼容 OpenAI API 协议的供应商：OpenAI、DeepSeek、Siliconflow、Moonshot 等
 *
 * @author Robust_H
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOpenAiCompatibleProvider implements ModelProvider {

    protected final LlmService llmService;

    // ==================== 默认配置常量 ====================
    protected static final int DEFAULT_MAX_TOKENS = 4096;
    protected static final double DEFAULT_TEMPERATURE = 0.7;
    protected static final double DEFAULT_TOP_P = 1.0;
    protected static final double DEFAULT_FREQUENCY_PENALTY = 0.0;
    protected static final double DEFAULT_PRESENCE_PENALTY = 0.0;

    // ==================== 核心方法：创建 ChatModel ====================

    @Override
    public ChatModel createChatModel(ModelConfigEntity config) {
        return createChatModel(config, null);
    }

    /**
     * 创建 ChatModel（支持自定义 ChatOptions）
     *
     * @param config  模型配置
     * @param options 自定义选项，为 null 则使用默认选项
     * @return ChatModel 实例
     */
    public ChatModel createChatModel(ModelConfigEntity config, ChatOptions options) {
        validateConfig(config);

        OpenAiApi api = buildOpenAiApi(config);
        OpenAiChatOptions defaultOptions = (options instanceof OpenAiChatOptions)
                ? (OpenAiChatOptions) options
                : buildDefaultChatOptions(config);

        log.debug("创建 ChatModel: provider={}, model={}, baseUrl={}",
                getProviderName(), config.getModelName(), resolveBaseUrl(config));

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions)
                .toolCallingManager(buildToolCallingManager(config))
                .retryTemplate(buildRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    // ==================== ChatOptions 构建方法 ====================

    @Override
    public ChatOptions createChatOptions(ModelConfigEntity config, Integer maxTokens, Double temperature) {
        return OpenAiChatOptions.builder()
                .model(config.getModelName())
                .maxTokens(maxTokens != null ? maxTokens : getDefaultMaxTokens(config))
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .topP(DEFAULT_TOP_P)
                .frequencyPenalty(DEFAULT_FREQUENCY_PENALTY)
                .presencePenalty(DEFAULT_PRESENCE_PENALTY)
                .build();
    }

    @Override
    public ChatOptions createDefaultChatOptions(ModelConfigEntity config) {
        return createChatOptions(config, null, null);
    }

    /**
     * 构建默认的 ChatOptions
     */
    protected OpenAiChatOptions buildDefaultChatOptions(ModelConfigEntity config) {
        return OpenAiChatOptions.builder()
                .model(config.getModelName())
                .maxTokens(getDefaultMaxTokens(config))
                .temperature(DEFAULT_TEMPERATURE)
                .build();
    }

    // ==================== OpenAiApi 构建 ====================

    /**
     * 构建 OpenAiApi 实例
     */
    protected OpenAiApi buildOpenAiApi(ModelConfigEntity config) {
        return OpenAiApi.builder()
                .baseUrl(resolveBaseUrl(config))
                .apiKey(config.getApiKey())
                .build();
    }

    /**
     * 解析 Base URL（优先使用配置值，否则使用默认值）
     */
    protected String resolveBaseUrl(ModelConfigEntity config) {
        if (StringUtils.hasText(config.getApiUrl())) {
            return config.getApiUrl();
        }
        String defaultUrl = getDefaultBaseUrl();
        if (!StringUtils.hasText(defaultUrl)) {
            throw new IllegalStateException("未配置 Provider 的默认 Base URL: " + getProviderName());
        }
        return defaultUrl;
    }

    // ==================== 辅助组件构建 ====================

    /**
     * 构建工具调用管理器
     */
    protected ToolCallingManager buildToolCallingManager(ModelConfigEntity config) {
        return ToolCallingManager.builder().build();
    }

    /**
     * 构建重试模板
     */
    protected RetryTemplate buildRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2, 10000)
                .build();
    }

    // ==================== 配置校验 ====================

    /**
     * 校验配置有效性
     */
    protected void validateConfig(ModelConfigEntity config) {
        if (config == null) {
            throw new IllegalArgumentException("模型配置不能为空 (ModelConfigEntity)");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new IllegalArgumentException("创建模型失败：缺少 API Key，provider=" + getProviderName());
        }
        if (!StringUtils.hasText(config.getModelName())) {
            throw new IllegalArgumentException("创建模型失败：缺少 modelName");
        }
    }

    /**
     * 获取默认 maxTokens（子类可覆盖）
     */
    protected int getDefaultMaxTokens(ModelConfigEntity config) {
        if (config.getMaxToken() != null && config.getMaxToken() > 0) {
            return config.getMaxToken();
        }
        return DEFAULT_MAX_TOKENS;
    }

    // ==================== 接口默认实现 ====================

    @Override
    public List<DiscoveredModelInfo> discoverModels() {
        return llmService.getModelsByFactoryId(getProviderName()).stream()
                .map(e -> BeanUtil.copyProperties(e, DiscoveredModelInfo.class))
                .collect(Collectors.toList());
    }

    @Override
    public HealthCheckResult checkHealth(String apiKey) {
        String providerName = getProviderName();

        // 1. 从数据库获取该供应商下的模型
        List<LlmEntity> models = llmService.getEnabledModelsByFactoryId(providerName);
        if (models == null || models.isEmpty()) {
            return HealthCheckResult.failure(providerName, "健康检测失败", "该供应商下没有可用的模型");
        }

        // 2. 只选择 Chat 类型模型（modelType = "CHAT"）
        List<LlmEntity> chatModels = models.stream()
                .filter(m -> "CHAT".equalsIgnoreCase(m.getModelType()))
                .toList();

        if (chatModels.isEmpty()) {
            return HealthCheckResult.failure(providerName, "健康检测失败", "该供应商下没有可用的 Chat 模型");
        }

        // 3. 选择 maxTokens 最小的 Chat 模型（通常更便宜）
        LlmEntity cheapestModel = chatModels.stream()
                .filter(m -> m.getMaxTokens() != null && m.getMaxTokens() > 0)
                .min((a, b) -> a.getMaxTokens().compareTo(b.getMaxTokens()))
                .orElse(chatModels.get(0));

        String testModelName = cheapestModel.getLlmName();
        Integer maxTokens = cheapestModel.getMaxTokens();

        // 4. 构建临时配置进行测试
        ModelConfigEntity testConfig = new ModelConfigEntity();
        testConfig.setApiKey(apiKey);
        testConfig.setModelName(testModelName);
        testConfig.setMaxToken(100); // 测试时使用较小的 token 限制

        long startTime = System.currentTimeMillis();
        try {
            // 5. 创建模型并发送测试请求
            ChatModel chatModel = createChatModel(testConfig);
            String response = chatModel.call("hi");
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("健康检测成功，provider={}, model={}, maxTokens={}, 响应时间={}ms",
                    providerName, testModelName, maxTokens, responseTime);

            return HealthCheckResult.success(providerName, testModelName, maxTokens, responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("健康检测失败，provider={}, model={}, 耗时={}ms, 错误={}",
                    providerName, testModelName, responseTime, e.getMessage());

            return HealthCheckResult.failure(providerName, "API 连接失败", e.getMessage());
        }
    }

    @Override
    public HealthCheckResult checkModelHealth(String apiKey, String modelName) {
        String providerName = getProviderName();

        if (!StringUtils.hasText(modelName)) {
            return HealthCheckResult.failure(providerName, "模型健康检测失败", "模型名称不能为空");
        }

        // 构建临时配置进行测试
        ModelConfigEntity testConfig = new ModelConfigEntity();
        testConfig.setApiKey(apiKey);
        testConfig.setModelName(modelName);
        testConfig.setMaxToken(100); // 测试时使用较小的 token 限制

        long startTime = System.currentTimeMillis();
        try {
            // 创建模型并发送测试请求
            ChatModel chatModel = createChatModel(testConfig);
            String response = chatModel.call("hi");
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("模型健康检测成功，provider={}, model={}, 响应时间={}ms",
                    providerName, modelName, responseTime);

            return HealthCheckResult.success(providerName, modelName, null, responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("模型健康检测失败，provider={}, model={}, 耗时={}ms, 错误={}",
                    providerName, modelName, responseTime, e.getMessage());

            return HealthCheckResult.failure(providerName, "模型连接失败", e.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }
}