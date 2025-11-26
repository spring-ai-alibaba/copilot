package com.alibaba.cloud.ai.copilot.provider;

import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.dto.DiscoveredModelInfo;
import com.alibaba.cloud.ai.copilot.dto.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
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
            modelConfigEntities.add(config);
        }

        modelConfigMapper.insert(modelConfigEntities);
        log.info("已保存 {} 个模型配置到用户配置，provider={}, userId={}",
                modelConfigEntities.size(), providerCode, loginUser.getUserId());
    }

    /**
     * 检测指定供应商下指定模型的健康状态
     * <p>用于用户新增自定义模型名称后的健康检测</p>
     * <p>API Key 从当前用户的配置中自动获取</p>
     *
     * @param providerCode 供应商代码
     * @param modelName    模型名称
     * @return 健康检测结果
     */
    public HealthCheckResult checkModelHealth(String providerCode, String modelName) {
        // 1. 从注册表获取 Provider
        if (!providerRegistry.hasProvider(providerCode)) {
            log.warn("供应商不存在，providerCode={}", providerCode);
            return HealthCheckResult.failure(providerCode, "供应商不存在", "未找到供应商: " + providerCode);
        }

        // 2. 从用户配置中获取 API Key
        LoginUser loginUser = LoginHelper.getLoginUser();
        String apiKey = getApiKeyByProvider(loginUser.getUserId(), providerCode);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("用户未配置该供应商的API Key，providerCode={}, userId={}", providerCode, loginUser.getUserId());
            return HealthCheckResult.failure(providerCode, "API Key未配置", "请先配置该供应商的API Key");
        }

        ModelProvider provider = providerRegistry.getProviderOrThrow(providerCode);

        // 3. 执行模型健康检测
        log.info("开始模型健康检测，provider={}, model={}", providerCode, modelName);
        HealthCheckResult healthCheckResult = provider.checkModelHealth(apiKey, modelName);

        if (healthCheckResult.isHealthy()) {
            log.info("模型健康检测成功，provider={}, model={}, 响应时间={}ms",
                    providerCode, modelName, healthCheckResult.getResponseTime());
        } else {
            log.warn("模型健康检测失败，provider={}, model={}, 错误={}",
                    providerCode, modelName, healthCheckResult.getError());
        }

        return healthCheckResult;
    }

    /**
     * 根据供应商代码获取用户配置的 API Key
     *
     * @param userId       用户ID
     * @param providerCode 供应商代码
     * @return API Key，如果未配置则返回 null
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
     * @return 供应商列表
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
}

