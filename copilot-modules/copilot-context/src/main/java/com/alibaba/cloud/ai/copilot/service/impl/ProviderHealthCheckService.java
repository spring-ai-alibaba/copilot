package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.domain.dto.model.DiscoveredModelInfo;
import com.alibaba.cloud.ai.copilot.domain.dto.model.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
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

    private final DynamicModelService dynamicModelService;

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

        // 2. 从用户配置中获取完整连接配置（API Key + 自定义 Base URL）
        LoginUser loginUser = LoginHelper.getLoginUser();
        Long userId = loginUser.getUserId();
        ModelConfigEntity providerConfig = getConfigByProvider(userId, providerCode);
        if (providerConfig == null
                || providerConfig.getApiKey() == null
                || providerConfig.getApiKey().isBlank()) {
            log.warn("用户未配置该供应商的API Key，providerCode={}, userId={}", providerCode, userId);
            return HealthCheckResult.failure(providerCode, "API Key未配置", "请先配置该供应商的API Key");
        }

        // 3. 检查该模型是否已在用户配置中
        boolean alreadyConfigured = isModelConfigured(userId, providerCode, modelName);

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 4. 执行模型健康检测
        log.info("开始模型健康检测，provider={}, model={}", providerCode, modelName);
        ModelConfigEntity testConfig = new ModelConfigEntity();
        testConfig.setProvider(providerCode);
        testConfig.setApiKey(providerConfig.getApiKey());
        testConfig.setApiUrl(providerConfig.getApiUrl());
        testConfig.setModelName(modelName);
        testConfig.setModelKey(modelName);
        testConfig.setMaxToken(100);
        HealthCheckResult healthCheckResult =
                checkModelWithConfig(provider, providerCode, modelName, testConfig);

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
                    saveModelToUserConfig(userId, providerCode, modelName, providerConfig);
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
    private void saveModelToUserConfig(
            Long userId,
            String providerCode,
            String modelName,
            ModelConfigEntity providerConfig) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider(providerCode);
        config.setApiKey(providerConfig.getApiKey());
        config.setApiUrl(providerConfig.getApiUrl());
        config.setUserId(userId);
        config.setModelName(modelName);
        config.setModelKey(modelName);
        config.setMaxToken(4096);
        config.setModelType("CHAT");
        config.setEnabled(false);
        modelConfigMapper.insert(config);
    }

    /**
     * 根据供应商代码获取用户的一条完整连接配置。
     */
    private ModelConfigEntity getConfigByProvider(Long userId, String providerCode) {
        LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getProvider, providerCode)
                .isNotNull(ModelConfigEntity::getApiKey)
                .last("LIMIT 1");
        return modelConfigMapper.selectOne(queryWrapper);
    }

    private HealthCheckResult checkModelWithConfig(
            ModelProvider provider,
            String providerCode,
            String modelName,
            ModelConfigEntity testConfig) {
        long startTime = System.currentTimeMillis();
        try {
            io.agentscope.core.model.Model model = provider.createChatModel(testConfig);
            callSyncHealth(model);
            long responseTime = System.currentTimeMillis() - startTime;
            return HealthCheckResult.success(providerCode, modelName, null, responseTime);
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("模型健康检测失败，provider={}, model={}, 耗时={}ms, 错误={}",
                    providerCode, modelName, responseTime, e.getMessage());
            return HealthCheckResult.failure(providerCode, "API 连接失败", e.getMessage());
        }
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
     * 检测使用自定义 Base URL 的供应商。
     *
     * <p>providerCode 决定协议实现，apiUrl 决定实际请求端点。</p>
     */
    public HealthCheckResult checkCustomProviderHealth(
            String providerCode, String apiUrl, String apiKey, String testModelName) {
        // 1. 从注册表获取指定协议的 Provider
        if (!providerRegistry.hasProvider(providerCode)) {
            log.warn("自定义端点供应商未注册，providerCode={}", providerCode);
            return HealthCheckResult.failure(
                    providerCode, "供应商未注册", "未找到供应商: " + providerCode);
        }

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 2. 构建测试配置（包含自定义 URL）
        ModelConfigEntity testConfig = new ModelConfigEntity();
        testConfig.setApiKey(apiKey);
        testConfig.setApiUrl(apiUrl);
        testConfig.setModelKey(testModelName);
        testConfig.setMaxToken(100);

        // 3. 执行健康检测
        log.info("开始自定义端点健康检测，provider={}, url={}, model={}",
                providerCode, apiUrl, testModelName);
        long startTime = System.currentTimeMillis();

        try {
            io.agentscope.core.model.Model model = provider.createChatModel(testConfig);
            String response = callSyncHealth(model);
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("自定义端点健康检测成功，provider={}, url={}, model={}, 响应时间={}ms",
                    providerCode, apiUrl, testModelName, responseTime);

            HealthCheckResult result = HealthCheckResult.success(providerCode, testModelName, null, responseTime);

            // 4. 检测成功，将模型配置保存到用户配置中
            try {
                LoginUser loginUser = LoginHelper.getLoginUser();
                saveCustomProviderModelToUserConfig(
                        loginUser.getUserId(), providerCode, apiUrl, apiKey, testModelName);
                result.setMessage(providerCode + " 连接正常，已添加到您的配置中");
                log.info("自定义端点模型已保存到用户配置，provider={}, userId={}, url={}, model={}",
                        providerCode, loginUser.getUserId(), apiUrl, testModelName);
            } catch (Exception e) {
                log.error("保存自定义端点模型配置失败，provider={}, 错误={}",
                        providerCode, e.getMessage());
                result.setMessage(providerCode + " 连接正常，但保存配置失败");
            }

            return result;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("自定义端点健康检测失败，provider={}, url={}, model={}, 耗时={}ms, 错误={}",
                    providerCode, apiUrl, testModelName, responseTime, e.getMessage());
            return HealthCheckResult.failure(providerCode, "API 连接失败", e.getMessage());
        }
    }

    /**
     * 将自定义端点模型保存到用户配置；同一用户、供应商、模型重复检测时执行更新。
     */
    private void saveCustomProviderModelToUserConfig(
            Long userId, String providerCode, String apiUrl, String apiKey, String testModelName) {
        LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getProvider, providerCode)
                .eq(ModelConfigEntity::getModelKey, testModelName)
                .last("LIMIT 1");
        ModelConfigEntity config = modelConfigMapper.selectOne(queryWrapper);
        boolean isNew = config == null;
        if (isNew) {
            config = new ModelConfigEntity();
            config.setUserId(userId);
            config.setProvider(providerCode);
            config.setEnabled(false);
            config.setModelType("CHAT");
            config.setMaxToken(4096);
        }

        config.setApiUrl(apiUrl);
        config.setApiKey(apiKey);
        config.setModelName(testModelName);
        config.setModelKey(testModelName);

        if (isNew) {
            modelConfigMapper.insert(config);
        } else {
            modelConfigMapper.updateById(config);
            dynamicModelService.refreshModelCacheById(String.valueOf(config.getId()));
        }
    }

    /**
     * 健康检查同步调用：agentscope 的 {@link io.agentscope.core.model.Model} 只暴露流式 stream(...)，
     * 这里 block 取首个非空 ChatResponse 的文本。
     */
    private String callSyncHealth(io.agentscope.core.model.Model model) {
        io.agentscope.core.message.Msg userMsg = io.agentscope.core.message.Msg.builder().textContent("hi").build();
        io.agentscope.core.model.ChatResponse resp = model.stream(
                java.util.List.of(userMsg), java.util.List.of(), null)
            .filter(r -> r != null && r.getContent() != null && !r.getContent().isEmpty())
            .blockFirst();
        if (resp == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var block : resp.getContent()) {
            if (block instanceof io.agentscope.core.message.TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }
}
