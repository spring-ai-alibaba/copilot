package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.domain.dto.model.DiscoveredModelInfo;
import com.alibaba.cloud.ai.copilot.domain.dto.model.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ModelProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商健康检测服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderHealthCheckService {

    private final ProviderRegistry providerRegistry;

    private final ModelConfigMapper modelConfigMapper;

    /**
     * 检测指定供应商的健康状态, 并且添加到用户配置中
     * @param providerCode 供应商代码
     * @param apiKey API密钥
     * @return 健康检测结果
     */
    public HealthCheckResult checkHealth(String providerCode, String apiKey) {
        // 1. 从注册表获取 Provider
        if (!providerRegistry.hasProvider(providerCode)) {
            log.warn("供应商不存在，providerCode={}", providerCode);
            return HealthCheckResult.failure(providerCode, "供应商不存在", "未找到供应商: " + providerCode);
        }

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 2. 执行健康检测
        log.info("开始健康检测，provider={}", providerCode);
        HealthCheckResult healthCheckResult = provider.checkHealth(apiKey);

        // 3. 检测成功，将模型配置保存到用户配置中
        if (healthCheckResult.isHealthy()) {
            try {
                saveModelsToUserConfig(provider, providerCode, apiKey);
                log.info("健康检测成功，已保存模型配置，provider={}, testModel={}, maxTokens={}, 响应时间={}ms",
                        providerCode, healthCheckResult.getTestModelName(),
                        healthCheckResult.getMaxTokens(), healthCheckResult.getResponseTime());
            } catch (Exception e) {
                log.error("保存模型配置失败，provider={}, 错误={}", providerCode, e.getMessage());
                // 保存失败不影响健康检测结果
            }
        } else {
            log.warn("健康检测失败，provider={}, 错误={}", providerCode, healthCheckResult.getError());
        }

        return healthCheckResult;
    }

    /**
     * 将供应商下的模型保存到用户配置
     */
    private void saveModelsToUserConfig(ModelProvider provider, String providerCode, String apiKey) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        List<DiscoveredModelInfo> discoveredModels = provider.discoverModels();

        if (discoveredModels == null || discoveredModels.isEmpty()) {
            log.warn("该供应商下没有可用模型，provider={}", providerCode);
            return;
        }

        List<ModelConfigEntity> modelConfigEntities = new ArrayList<>();
        for (DiscoveredModelInfo model : discoveredModels) {
            ModelConfigEntity config = new ModelConfigEntity();
            config.setProvider(providerCode);
            config.setApiKey(apiKey);
            config.setUserId(loginUser.getUserId());
            config.setMaxToken(model.getMaxTokens());
            config.setModelName(model.getLlmName());
            config.setModelKey(model.getLlmName());
            config.setEnabled(false);
            config.setFunctionCall(model.getIsTools());
            config.setModelType(model.getModelType());
            modelConfigEntities.add(config);
        }

        modelConfigMapper.insert(modelConfigEntities);
        log.info("已保存 {} 个模型配置到用户配置，provider={}, userId={}",
                modelConfigEntities.size(), providerCode, loginUser.getUserId());
    }

    /**
     * 检测指定供应商下指定模型的健康状态
     */
    public HealthCheckResult checkModelHealth(String providerCode, String modelName) {
        // 1. 从注册表获取 Provider
        if (!providerRegistry.hasProvider(providerCode)) {
            log.warn("供应商不存在，providerCode={}", providerCode);
            return HealthCheckResult.failure(providerCode, "供应商不存在", "未找到供应商: " + providerCode);
        }

        // 2. 从用户配置中获取 API Key
        LoginUser loginUser = LoginHelper.getLoginUser();
        Long userId = loginUser.getUserId();
        String apiKey = getApiKeyByProvider(userId, providerCode);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("用户未配置该供应商的API Key，providerCode={}, userId={}", providerCode, userId);
            return HealthCheckResult.failure(providerCode, "API Key未配置", "请先配置该供应商的API Key");
        }

        // 3. 检查该模型是否已在用户配置中
        boolean alreadyConfigured = isModelConfigured(userId, providerCode, modelName);

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 4. 执行模型健康检测
        log.info("开始模型健康检测，provider={}, model={}", providerCode, modelName);
        HealthCheckResult healthCheckResult = provider.checkModelHealth(apiKey, modelName);

        if (healthCheckResult.isHealthy()) {
            log.info("模型健康检测成功，provider={}, model={}, 响应时间={}ms",
                    providerCode, modelName, healthCheckResult.getResponseTime());

            // 设置模型配置状态
            healthCheckResult.setAlreadyConfigured(alreadyConfigured);

            if (alreadyConfigured) {
                // 模型已配置，提醒用户
                healthCheckResult.setMessage(providerCode + " 模型 " + modelName + " 检测成功，该模型已在您的配置中");
                healthCheckResult.setNewlyAdded(false);
                log.info("模型已存在于用户配置中，provider={}, model={}, userId={}", providerCode, modelName, userId);
            } else {
                // 模型未配置，自动添加到用户配置
                try {
                    saveModelToUserConfig(userId, providerCode, modelName, apiKey);
                    healthCheckResult.setMessage(providerCode + " 模型 " + modelName + " 检测成功，已自动添加到您的配置中");
                    healthCheckResult.setNewlyAdded(true);
                    log.info("模型检测成功并已添加到用户配置，provider={}, model={}, userId={}", providerCode, modelName, userId);
                } catch (Exception e) {
                    log.error("保存模型配置失败，provider={}, model={}, userId={}, 错误={}",
                            providerCode, modelName, userId, e.getMessage());
                    healthCheckResult.setMessage(providerCode + " 模型 " + modelName + " 检测成功，但保存配置失败");
                    healthCheckResult.setNewlyAdded(false);
                }
            }
        } else {
            log.warn("模型健康检测失败，provider={}, model={}, 错误={}",
                    providerCode, modelName, healthCheckResult.getError());
        }

        return healthCheckResult;
    }

    /**
     * 检查指定模型是否已在用户配置中
     */
    private boolean isModelConfigured(Long userId, String providerCode, String modelName) {
        LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getProvider, providerCode)
                .eq(ModelConfigEntity::getModelName, modelName);
        return modelConfigMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 将单个模型保存到用户配置
     */
    private void saveModelToUserConfig(Long userId, String providerCode, String modelName, String apiKey) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider(providerCode);
        config.setApiKey(apiKey);
        config.setUserId(userId);
        config.setModelName(modelName);
        config.setModelKey(modelName);
        config.setEnabled(false);
        modelConfigMapper.insert(config);
    }

    /**
     * 根据供应商代码获取用户配置的 API Key
     */
    private String getApiKeyByProvider(Long userId, String providerCode) {
        LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getProvider, providerCode)
                .isNotNull(ModelConfigEntity::getApiKey)
                .last("LIMIT 1");
        ModelConfigEntity config = modelConfigMapper.selectOne(queryWrapper);
        return config != null ? config.getApiKey() : null;
    }

    /**
     * 获取所有可用的供应商
     */
    public List<String> getAvailableProviders() {
        return providerRegistry.getAllProviders().keySet().stream().sorted().toList();
    }

    /**
     * 获取 Provider 注册表
     */
    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    /**
     * 检测 OpenAI Compatible 供应商的健康状态
     */
    public HealthCheckResult checkOpenAiCompatibleHealth(String apiUrl, String apiKey, String testModelName) {
        String providerCode = "OpenAiCompatible";

        // 1. 从注册表获取 OpenAiCompatible Provider
        if (!providerRegistry.hasProvider(providerCode)) {
            log.warn("OpenAiCompatible 供应商未注册，providerCode={}", providerCode);
            return HealthCheckResult.failure(providerCode, "供应商未注册", "未找到 OpenAiCompatible 供应商");
        }

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 2. 构建测试配置（包含自定义 URL）
        ModelConfigEntity testConfig = new ModelConfigEntity();
        testConfig.setApiKey(apiKey);
        testConfig.setApiUrl(apiUrl);
        testConfig.setModelKey(testModelName);
        testConfig.setMaxToken(100);

        // 3. 执行健康检测
        log.info("开始 OpenAiCompatible 健康检测，url={}, model={}", apiUrl, testModelName);
        long startTime = System.currentTimeMillis();

        try {
            org.springframework.ai.chat.model.ChatModel chatModel = provider.createChatModel(testConfig);
            String response = chatModel.call("hi");
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("OpenAiCompatible 健康检测成功，url={}, model={}, 响应时间={}ms",
                    apiUrl, testModelName, responseTime);

            HealthCheckResult result = HealthCheckResult.success(providerCode, testModelName, null, responseTime);

            // 4. 检测成功，将模型配置保存到用户配置中
            try {
                LoginUser loginUser = LoginHelper.getLoginUser();
                saveOpenAiCompatibleModelToUserConfig(loginUser.getUserId(), apiUrl, apiKey, testModelName);
                result.setMessage(providerCode + " 连接正常，已添加到您的配置中");
                log.info("OpenAiCompatible 模型已保存到用户配置，userId={}, url={}, model={}",
                        loginUser.getUserId(), apiUrl, testModelName);
            } catch (Exception e) {
                log.error("保存 OpenAiCompatible 模型配置失败，错误={}", e.getMessage());
                result.setMessage(providerCode + " 连接正常，但保存配置失败");
            }

            return result;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("OpenAiCompatible 健康检测失败，url={}, model={}, 耗时={}ms, 错误={}",
                    apiUrl, testModelName, responseTime, e.getMessage());
            return HealthCheckResult.failure(providerCode, "API 连接失败", e.getMessage());
        }
    }

    /**
     * 将 OpenAiCompatible 模型保存到用户配置
     */
    private void saveOpenAiCompatibleModelToUserConfig(Long userId, String apiUrl, String apiKey, String testModelName) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider("OpenAiCompatible");
        config.setApiUrl(apiUrl);
        config.setApiKey(apiKey);
        config.setUserId(userId);
        config.setModelName(testModelName);
        config.setModelKey(testModelName);
        config.setEnabled(false);
        modelConfigMapper.insert(config);
    }
}
