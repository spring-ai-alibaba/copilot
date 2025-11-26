package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.provider.ModelProvider;
import com.alibaba.cloud.ai.copilot.provider.ProviderRegistry;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.service.OpenAiModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态模型服务实现类
 * 支持从数据库动态获取 API 配置并创建对应的模型实例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicModelServiceImpl implements DynamicModelService {

    /**
     * 模型缓存，避免重复创建（key: configId 或 modelName:userId）
     */
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    /**
     * Provider 注册表
     */
    private final ProviderRegistry providerRegistry;

    private final ModelConfigMapper modelConfigMapper;

    private final OpenAiModelFactory openAiModelFactory;

    @Override
    public ChatModel getChatModel(String modelName, String userId) {
        String cacheKey = generateCacheKey(modelName, userId);

        return modelCache.computeIfAbsent(cacheKey, key -> {
            try {
                return createChatModel(modelName, userId);
            } catch (Exception e) {
                log.error("根据模型名称创建 ChatModel 失败，model={}, userId={}", modelName, userId, e);
                throw new RuntimeException("创建 ChatModel 失败", e);
            }
        });
    }

    @Override
    public ChatModel getChatModelWithConfigId(String id) {
        return getChatModelWithConfigId(id, null);
    }

    /**
     * 根据配置 ID 获取 ChatModel（支持自定义选项）
     *
     * @param id      配置 ID
     * @param options 自定义 ChatOptions，为 null 则使用默认选项
     * @return ChatModel 实例
     */
    public ChatModel getChatModelWithConfigId(String id, ChatOptions options) {
        String cacheKey = "config:" + id;

        return modelCache.computeIfAbsent(cacheKey, key -> {
            ModelConfigEntity config = modelConfigMapper.selectById(id);
            if (config == null) {
                log.error("未找到对应的模型配置，id={}", id);
                throw new IllegalArgumentException("未找到对应的模型配置，id=" + id);
            }

            if (!Boolean.TRUE.equals(config.getEnabled())) {
                log.warn("模型配置已被禁用，id={}, model={}", id, config.getModelName());
                throw new IllegalStateException("模型配置已被禁用，id=" + id);
            }

            ModelProvider provider = providerRegistry.getProviderOrThrow(config.getProvider());
            log.info("开始创建 ChatModel，configId={}, provider={}, model={}",
                    id, config.getProvider(), config.getModelName());

            return provider.createChatModel(config, options);
        });
    }

    @Override
    public void refreshModelCache() {
        log.info("刷新全部模型缓存");
        modelCache.clear();
    }

    @Override
    public boolean isModelAvailable(String modelName, String userId) {
        try {
            ChatModel model = getChatModel(modelName, userId);
            return model != null;
        } catch (Exception e) {
            log.warn("模型不可用，modelName={}, userId={}, 错误={}", modelName, userId, e.getMessage());
            return false;
        }
    }

    /**
     * 创建ChatModel实例
     */
    private ChatModel createChatModel(String modelName, String userId) {
        try {
            // 使用OpenAiModelFactory创建模型
            return openAiModelFactory.createChatModel(modelName, userId);
        } catch (Exception e) {
            log.error("通过 OpenAiModelFactory 创建 ChatModel 失败，model={}, userId={}", modelName, userId, e);
            throw new RuntimeException("通过 OpenAiModelFactory 创建 ChatModel 失败", e);
        }
    }

    /**
     * 根据配置 ID 刷新单个模型缓存
     *
     * @param configId 配置 ID
     */
    public void refreshModelCacheById(String configId) {
        String cacheKey = "config:" + configId;
        ChatModel removed = modelCache.remove(cacheKey);
        if (removed != null) {
            log.info("已从缓存中移除模型实例，configId={}", configId);
        }
    }

    /**
     * 获取 Provider 注册表（供外部查询）
     */
    public ProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String modelName, String userId) {
        return modelName + ":" + (userId != null ? userId : "default");
    }
}
