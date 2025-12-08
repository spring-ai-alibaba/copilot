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

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Set;
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

    // ==================== 健康检查重试配置 ====================
    /** 健康检查最大重试次数 */
    protected static final int HEALTH_CHECK_MAX_RETRIES = 3;
    /** 健康检查重试间隔（毫秒） */
    protected static final long HEALTH_CHECK_RETRY_DELAY_MS = 1000;
    /** 健康检查重试间隔倍数（指数退避） */
    protected static final double HEALTH_CHECK_RETRY_MULTIPLIER = 2.0;
    /** 健康检查最大模型切换次数 */
    protected static final int HEALTH_CHECK_MAX_MODEL_SWITCHES = 3;

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
                .model(config.getModelKey())
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
                .model(config.getModelKey())
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
        if (!StringUtils.hasText(config.getModelKey())) {
            throw new IllegalArgumentException("创建模型失败：缺少 modelKey");
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

        // 3. 按 maxTokens 排序（从小到大，优先测试便宜的模型）
        List<LlmEntity> sortedModels = chatModels.stream()
                .sorted((a, b) -> {
                    Integer tokensA = a.getMaxTokens() != null ? a.getMaxTokens() : Integer.MAX_VALUE;
                    Integer tokensB = b.getMaxTokens() != null ? b.getMaxTokens() : Integer.MAX_VALUE;
                    return tokensA.compareTo(tokensB);
                })
                .toList();

        // 4. 使用动态模型切换的重试机制执行健康检测
        return executeHealthCheckWithModelFallback(providerName, apiKey, sortedModels);
    }

    /**
     * 执行带模型切换的健康检测（动态重试机制）
     * <p>当一个模型测试失败时，自动切换到该供应商下的其他模型继续测试</p>
     *
     * @param providerName 供应商名称
     * @param apiKey       API 密钥
     * @param candidateModels 候选模型列表（按优先级排序）
     * @return 健康检测结果
     */
    protected HealthCheckResult executeHealthCheckWithModelFallback(String providerName, String apiKey,
                                                                    List<LlmEntity> candidateModels) {
        int maxRetriesPerModel = getHealthCheckMaxRetries();
        int maxModelSwitches = getHealthCheckMaxModelSwitches();
        long totalStartTime = System.currentTimeMillis();
        StringBuilder failedModelsInfo = new StringBuilder();
        int totalAttempts = 0;

        // 限制测试的模型数量
        int modelsToTest = Math.min(candidateModels.size(), maxModelSwitches);

        for (int modelIndex = 0; modelIndex < modelsToTest; modelIndex++) {
            LlmEntity currentModel = candidateModels.get(modelIndex);
            String modelName = currentModel.getLlmName();
            Integer maxTokens = currentModel.getMaxTokens();

            log.info("开始测试模型（第{}/{}个），provider={}, model={}",
                    modelIndex + 1, modelsToTest, providerName, modelName);

            // 构建测试配置
            ModelConfigEntity testConfig = new ModelConfigEntity();
            testConfig.setApiKey(apiKey);
            testConfig.setModelKey(modelName);
            testConfig.setMaxToken(100);

            // 对当前模型进行重试
            HealthCheckResult result = executeHealthCheckWithRetry(
                    providerName, modelName, maxTokens, testConfig, maxRetriesPerModel);

            totalAttempts += maxRetriesPerModel;

            if (result.isHealthy()) {
                if (modelIndex > 0) {
                    log.info("健康检测成功（切换到第{}个模型后成功），provider={}, model={}, 响应时间={}ms",
                            modelIndex + 1, providerName, modelName, result.getResponseTime());
                }
                return result;
            }

            // 记录失败的模型信息
            if (failedModelsInfo.length() > 0) {
                failedModelsInfo.append("; ");
            }
            failedModelsInfo.append(modelName).append(": ").append(result.getError());

            // 如果还有其他模型可以尝试
            if (modelIndex < modelsToTest - 1) {
                log.warn("模型 {} 测试失败，将切换到下一个模型继续测试，provider={}", modelName, providerName);
            }
        }

        // 所有测试的模型都失败了
        long totalTime = System.currentTimeMillis() - totalStartTime;
        log.error("健康检测最终失败（已尝试{}个模型，最大允许{}个），provider={}, 总耗时={}ms, 失败详情: {}",
                modelsToTest, maxModelSwitches, providerName, totalTime, failedModelsInfo);

        return HealthCheckResult.failure(providerName,
                "所有模型连接失败（已尝试" + modelsToTest + "个模型）",
                failedModelsInfo.toString());
    }

    /**
     * 执行带重试机制的健康检测（单个模型）
     *
     * @param providerName 供应商名称
     * @param testModelName 测试模型名称
     * @param maxTokens     最大 token 数
     * @param testConfig    测试配置
     * @param maxRetries    最大重试次数
     * @return 健康检测结果
     */
    protected HealthCheckResult executeHealthCheckWithRetry(String providerName, String testModelName,
                                                            Integer maxTokens, ModelConfigEntity testConfig,
                                                            int maxRetries) {
        long retryDelay = getHealthCheckRetryDelayMs();
        Exception lastException = null;
        long totalStartTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long attemptStartTime = System.currentTimeMillis();
            try {
                // 创建模型并发送测试请求
                ChatModel chatModel = createChatModel(testConfig);
                String response = chatModel.call("hi");
                long responseTime = System.currentTimeMillis() - attemptStartTime;

                if (attempt > 1) {
                    log.info("健康检测成功（第{}次重试），provider={}, model={}, maxTokens={}, 响应时间={}ms",
                            attempt, providerName, testModelName, maxTokens, responseTime);
                } else {
                    log.info("健康检测成功，provider={}, model={}, maxTokens={}, 响应时间={}ms",
                            providerName, testModelName, maxTokens, responseTime);
                }

                return HealthCheckResult.success(providerName, testModelName, maxTokens, responseTime);

            } catch (Exception e) {
                lastException = e;
                long attemptTime = System.currentTimeMillis() - attemptStartTime;

                // 判断是否为可重试的错误
                if (!isRetryableException(e)) {
                    log.warn("健康检测失败（不可重试的错误），provider={}, model={}, 耗时={}ms, 错误={}",
                            providerName, testModelName, attemptTime, e.getMessage());
                    return HealthCheckResult.failure(providerName, "模型连接失败（不可重试）",
                            extractErrorMessage(e));
                }

                if (attempt < maxRetries) {
                    log.warn("健康检测失败（第{}/{}次尝试），provider={}, model={}, 耗时={}ms, 错误={}，将在{}ms后重试",
                            attempt, maxRetries, providerName, testModelName, attemptTime, e.getMessage(), retryDelay);

                    // 等待后重试（指数退避）
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("健康检测重试被中断，provider={}", providerName);
                        break;
                    }
                    retryDelay = (long) (retryDelay * getHealthCheckRetryMultiplier());
                } else {
                    long totalTime = System.currentTimeMillis() - totalStartTime;
                    log.warn("模型健康检测失败（已重试{}次），provider={}, model={}, 总耗时={}ms, 错误={}",
                            maxRetries, providerName, testModelName, totalTime, e.getMessage());
                }
            }
        }

        return HealthCheckResult.failure(providerName, "模型连接失败",
                lastException != null ? extractErrorMessage(lastException) : "未知错误");
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
        testConfig.setModelKey(modelName);
        testConfig.setMaxToken(100); // 测试时使用较小的 token 限制

        // 使用重试机制执行模型健康检测
        return executeModelHealthCheckWithRetry(providerName, modelName, testConfig);
    }

    /**
     * 执行带重试机制的模型健康检测
     *
     * @param providerName 供应商名称
     * @param modelName    模型名称
     * @param testConfig   测试配置
     * @return 健康检测结果
     */
    protected HealthCheckResult executeModelHealthCheckWithRetry(String providerName, String modelName,
                                                                 ModelConfigEntity testConfig) {
        int maxRetries = getHealthCheckMaxRetries();
        long retryDelay = getHealthCheckRetryDelayMs();
        Exception lastException = null;
        long totalStartTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long attemptStartTime = System.currentTimeMillis();
            try {
                // 创建模型并发送测试请求
                ChatModel chatModel = createChatModel(testConfig);
                String response = chatModel.call("hi");
                long responseTime = System.currentTimeMillis() - attemptStartTime;

                if (attempt > 1) {
                    log.info("模型健康检测成功（第{}次重试），provider={}, model={}, 响应时间={}ms",
                            attempt, providerName, modelName, responseTime);
                } else {
                    log.info("模型健康检测成功，provider={}, model={}, 响应时间={}ms",
                            providerName, modelName, responseTime);
                }

                return HealthCheckResult.success(providerName, modelName, null, responseTime);

            } catch (Exception e) {
                lastException = e;
                long attemptTime = System.currentTimeMillis() - attemptStartTime;

                // 判断是否为可重试的错误
                if (!isRetryableException(e)) {
                    log.warn("模型健康检测失败（不可重试的错误），provider={}, model={}, 耗时={}ms, 错误={}",
                            providerName, modelName, attemptTime, e.getMessage());
                    return HealthCheckResult.failure(providerName, "模型连接失败（不可重试）",
                            extractErrorMessage(e));
                }

                if (attempt < maxRetries) {
                    log.warn("模型健康检测失败（第{}/{}次尝试），provider={}, model={}, 耗时={}ms, 错误={}，将在{}ms后重试",
                            attempt, maxRetries, providerName, modelName, attemptTime, e.getMessage(), retryDelay);

                    // 等待后重试（指数退避）
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("模型健康检测重试被中断，provider={}, model={}", providerName, modelName);
                        break;
                    }
                    retryDelay = (long) (retryDelay * getHealthCheckRetryMultiplier());
                } else {
                    long totalTime = System.currentTimeMillis() - totalStartTime;
                    log.error("模型健康检测最终失败（已重试{}次），provider={}, model={}, 总耗时={}ms, 错误={}",
                            maxRetries, providerName, modelName, totalTime, e.getMessage());
                }
            }
        }

        return HealthCheckResult.failure(providerName, "模型连接失败（已重试" + maxRetries + "次）",
                lastException != null ? extractErrorMessage(lastException) : "未知错误");
    }

    // ==================== 健康检查重试配置方法（子类可覆盖） ====================

    /**
     * 获取健康检查最大重试次数（子类可覆盖）
     */
    protected int getHealthCheckMaxRetries() {
        return HEALTH_CHECK_MAX_RETRIES;
    }

    /**
     * 获取健康检查重试间隔（子类可覆盖）
     */
    protected long getHealthCheckRetryDelayMs() {
        return HEALTH_CHECK_RETRY_DELAY_MS;
    }

    /**
     * 获取健康检查重试间隔倍数（子类可覆盖）
     */
    protected double getHealthCheckRetryMultiplier() {
        return HEALTH_CHECK_RETRY_MULTIPLIER;
    }

    /**
     * 获取健康检查最大模型切换次数（子类可覆盖）
     */
    protected int getHealthCheckMaxModelSwitches() {
        return HEALTH_CHECK_MAX_MODEL_SWITCHES;
    }

    // ==================== 错误码判断与重试逻辑 ====================

    /** 不可重试的 HTTP 状态码（客户端错误，重试无意义） */
    private static final Set<Integer> NON_RETRYABLE_STATUS_CODES = Set.of(
            400,  // Bad Request - 请求格式错误
            401,  // Unauthorized - API Key 无效
            403,  // Forbidden - 无权限访问
            404,  // Not Found - 模型不存在
            422   // Unprocessable Entity - 请求参数无效
    );

    /** 可重试的 HTTP 状态码（服务端错误或限流） */
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(
            429,  // Too Many Requests - 限流，可重试
            500,  // Internal Server Error - 服务端错误
            502,  // Bad Gateway - 网关错误
            503,  // Service Unavailable - 服务不可用
            504   // Gateway Timeout - 网关超时
    );

    /**
     * 判断异常是否可重试
     * <p>
     * 可重试的情况：
     * - 5xx 服务端错误（500, 502, 503, 504）
     * - 429 限流错误
     * - 网络连接/超时错误
     * </p>
     * <p>
     * 不可重试的情况：
     * - 4xx 客户端错误（400, 401, 403, 404, 422）
     * - 这些通常是配置问题，重试没有意义
     * </p>
     *
     * @param e 异常
     * @return true 如果可以重试，false 如果不应重试
     */
    protected boolean isRetryableException(Exception e) {
        // 1. 检查 HTTP 客户端错误（4xx）
        if (e instanceof HttpClientErrorException clientError) {
            int statusCode = clientError.getStatusCode().value();
            // 429 限流是可重试的
            if (statusCode == 429) {
                log.debug("检测到限流错误(429)，将进行重试");
                return true;
            }
            // 其他 4xx 错误不可重试
            if (NON_RETRYABLE_STATUS_CODES.contains(statusCode)) {
                log.debug("检测到不可重试的客户端错误，statusCode={}", statusCode);
                return false;
            }
        }

        // 2. 检查 HTTP 服务端错误（5xx）- 可重试
        if (e instanceof HttpServerErrorException serverError) {
            int statusCode = serverError.getStatusCode().value();
            log.debug("检测到服务端错误，statusCode={}，将进行重试", statusCode);
            return true;
        }

        // 3. 检查网络连接错误 - 可重试
        if (e instanceof ResourceAccessException) {
            log.debug("检测到网络连接错误，将进行重试");
            return true;
        }

        // 4. 检查嵌套异常中的 HTTP 状态码
        Integer statusCode = extractHttpStatusCode(e);
        if (statusCode != null) {
            if (NON_RETRYABLE_STATUS_CODES.contains(statusCode)) {
                log.debug("从嵌套异常中检测到不可重试的状态码={}", statusCode);
                return false;
            }
            if (RETRYABLE_STATUS_CODES.contains(statusCode)) {
                log.debug("从嵌套异常中检测到可重试的状态码={}", statusCode);
                return true;
            }
        }

        // 5. 默认：对于未知错误，允许重试（保守策略）
        log.debug("未知错误类型，默认允许重试，errorType={}", e.getClass().getSimpleName());
        return true;
    }

    /**
     * 从异常中提取 HTTP 状态码
     *
     * @param e 异常
     * @return HTTP 状态码，如果无法提取则返回 null
     */
    protected Integer extractHttpStatusCode(Exception e) {
        // 检查异常消息中是否包含状态码信息
        String message = e.getMessage();
        if (message != null) {
            // 匹配常见的状态码模式，如 "401", "status: 401", "code: 401"
            for (Integer code : NON_RETRYABLE_STATUS_CODES) {
                if (message.contains(String.valueOf(code))) {
                    return code;
                }
            }
            for (Integer code : RETRYABLE_STATUS_CODES) {
                if (message.contains(String.valueOf(code))) {
                    return code;
                }
            }
        }

        // 检查 cause 链
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof HttpClientErrorException clientError) {
                return clientError.getStatusCode().value();
            }
            if (cause instanceof HttpServerErrorException serverError) {
                return serverError.getStatusCode().value();
            }
            String causeMessage = cause.getMessage();
            if (causeMessage != null) {
                for (Integer code : NON_RETRYABLE_STATUS_CODES) {
                    if (causeMessage.contains(String.valueOf(code))) {
                        return code;
                    }
                }
                for (Integer code : RETRYABLE_STATUS_CODES) {
                    if (causeMessage.contains(String.valueOf(code))) {
                        return code;
                    }
                }
            }
            cause = cause.getCause();
        }

        return null;
    }

    /**
     * 从异常中提取用户友好的错误消息
     *
     * @param e 异常
     * @return 错误消息
     */
    protected String extractErrorMessage(Exception e) {
        Integer statusCode = extractHttpStatusCode(e);
        String baseMessage = e.getMessage();

        if (statusCode != null) {
            String statusDescription = switch (statusCode) {
                case 400 -> "请求格式错误";
                case 401 -> "API Key 无效或已过期";
                case 403 -> "无权限访问该资源";
                case 404 -> "模型不存在或 API 地址错误";
                case 422 -> "请求参数无效";
                case 429 -> "请求过于频繁，已被限流";
                case 500 -> "服务端内部错误";
                case 502 -> "网关错误";
                case 503 -> "服务暂时不可用";
                case 504 -> "网关超时";
                default -> "HTTP 错误";
            };
            return String.format("[%d] %s: %s", statusCode, statusDescription,
                    baseMessage != null ? baseMessage : "无详细信息");
        }

        return baseMessage != null ? baseMessage : "未知错误";
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }
}