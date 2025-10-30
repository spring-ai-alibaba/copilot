package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.OpenAiModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态模型服务实现类
 * 支持从数据库动态获取API配置并创建对应的模型实例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicModelServiceImpl implements DynamicModelService {

    // 模型缓存，避免重复创建
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();


    private final OpenAiModelFactory openAiModelFactory;

    @Override
    public ChatModel getChatModel(String modelName, String userId) {
        String cacheKey = generateCacheKey(modelName, userId);

        return modelCache.computeIfAbsent(cacheKey, key -> {
            try {
                return createChatModel(modelName, userId);
            } catch (Exception e) {
                log.error("Failed to create chat model for model: {}, user: {}", modelName, userId, e);
                throw new RuntimeException("Failed to create chat model", e);
            }
        });
    }

    @Override
    public ChatModel getChatModel(String modelName) {
        return getChatModel(modelName, null);
    }

    @Override
    public void refreshModelCache() {
        log.info("Refreshing model cache");
        modelCache.clear();
    }

    @Override
    public boolean isModelAvailable(String modelName, String userId) {
        try {
            ChatModel model = getChatModel(modelName, userId);
            return model != null;
        } catch (Exception e) {
            log.warn("Model {} is not available for user {}: {}", modelName, userId, e.getMessage());
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
            log.error("Failed to create chat model for model: {}, user: {}", modelName, userId, e);
            throw new RuntimeException("Failed to create chat model", e);
        }
    }



    /**
     * 生成缓存键
     */
    private String generateCacheKey(String modelName, String userId) {
        return modelName + ":" + (userId != null ? userId : "default");
    }
}
