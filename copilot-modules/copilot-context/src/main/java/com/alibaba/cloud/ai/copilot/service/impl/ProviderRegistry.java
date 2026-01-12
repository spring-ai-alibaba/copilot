package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.ModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册表
 * <p>
 * 统一管理所有 ModelProvider 实例，提供按名称快速查找功能
 * </p>
 *
 * @author Copilot Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProviderRegistry {

    /**
     * Provider 映射表：providerName -> ModelProvider
     */
    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();

    public ProviderRegistry(Map<String, ModelProvider> providerBeans) {
        // 将所有 Provider Bean 按 providerName 索引
        providerBeans.values().forEach(provider -> {
            String name = provider.getProviderName();
            if (providers.containsKey(name)) {
                log.warn("检测到重复的 Provider 名称: {}, 旧实现: {}, 新实现: {}",
                        name, providers.get(name).getClass().getSimpleName(), provider.getClass().getSimpleName());
            }
            providers.put(name, provider);
            log.info("注册 Provider: {} -> {}", name, provider.getClass().getSimpleName());
        });
        log.info("ProviderRegistry 初始化完成，当前 Provider 总数: {}", providers.size());
    }

    /**
     * 获取指定供应商的 Provider
     *
     * @param providerName 供应商名称
     * @return Optional 包装的 Provider
     */
    public Optional<ModelProvider> getProvider(String providerName) {
        return Optional.ofNullable(providers.get(providerName));
    }

    /**
     * 获取 Provider，不存在则抛异常
     *
     * @param providerName 供应商名称
     * @return Provider 实例
     * @throws IllegalArgumentException 如果 Provider 不存在
     */
    public ModelProvider getProviderOrThrow(String providerName) {
        return getProvider(providerName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "未找到指定的 Provider: " + providerName + "，可用 Provider 列表: " + providers.keySet()));
    }

    /**
     * 获取所有已注册的 Provider（只读视图）
     *
     * @return 不可修改的 Provider Map
     */
    public Map<String, ModelProvider> getAllProviders() {
        return Collections.unmodifiableMap(providers);
    }

    /**
     * 检查供应商是否已注册
     *
     * @param providerName 供应商名称
     * @return true 如果已注册
     */
    public boolean hasProvider(String providerName) {
        return providers.containsKey(providerName);
    }

    /**
     * 获取已注册供应商数量
     */
    public int getProviderCount() {
        return providers.size();
    }
}
