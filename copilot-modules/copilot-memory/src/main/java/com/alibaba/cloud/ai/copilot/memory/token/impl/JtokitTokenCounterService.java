package com.alibaba.cloud.ai.copilot.memory.token.impl;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 jtokkit 的 Token 计数服务实现
 *
 * 优化特性：
 * 1. 消息级别的 Token 缓存（存储在 Message.metadata 中）
 * 2. 内容哈希缓存（用于相同内容的快速查找）
 * 3. 缓存命中率统计
 * 4. 自动缓存失效机制
 *
 * @author better
 */
@Slf4j
@Service
public class JtokitTokenCounterService implements TokenCounterService {

    private final MemoryProperties memoryProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, Encoding> encodingCache = new ConcurrentHashMap<>();

    /**
     * 内容哈希 -> Token数量 的缓存
     * Key: contentHash + ":" + modelName
     * Value: token count
     */
    private final Map<String, Integer> contentHashCache = new ConcurrentHashMap<>();

    /**
     * 缓存统计
     */
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCalculations = new AtomicLong(0);

    /**
     * 缓存大小限制（防止内存溢出）
     */
    private static final int MAX_CONTENT_CACHE_SIZE = 10000;

    public JtokitTokenCounterService(MemoryProperties memoryProperties, ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public int countTokens(List<Message> messages, String modelName) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        Encoding encoding = getEncoding(modelName);
        int totalTokens = 0;

        for (Message message : messages) {
            totalTokens += countTokens(message, modelName);
        }

        // 每条消息之间需要额外的分隔符 token（通常是 3 个）
        // 但第一条消息不需要，所以是 (messages.size() - 1) * 3
        if (messages.size() > 1) {
            totalTokens += (messages.size() - 1) * 3;
        }

        return totalTokens;
    }

    @Override
    public int countTokens(Message message, String modelName) {
        if (message == null) {
            return 0;
        }

        totalCalculations.incrementAndGet();

        // 策略1: 检查消息元数据中的缓存
        Integer cachedTokens = getCachedTokensFromMetadata(message, modelName);
        if (cachedTokens != null) {
            cacheHits.incrementAndGet();
            log.trace("Token count cache hit for message {}: {} tokens", message.getId(), cachedTokens);
            return cachedTokens;
        }

        // 策略2: 检查内容哈希缓存
        String contentHash = generateContentHash(message, modelName);
        if (contentHash != null) {
            Integer hashCachedTokens = contentHashCache.get(contentHash);
            if (hashCachedTokens != null) {
                cacheHits.incrementAndGet();
                // 同时更新到消息元数据中
                cacheTokensInMetadata(message, modelName, hashCachedTokens);
                log.trace("Token count hash cache hit for message {}: {} tokens", message.getId(), hashCachedTokens);
                return hashCachedTokens;
            }
        }

        // 缓存未命中，执行实际计算
        cacheMisses.incrementAndGet();
        int tokens = calculateTokens(message, modelName);

        // 缓存结果
        cacheTokensInMetadata(message, modelName, tokens);
        if (contentHash != null) {
            cacheTokensInContentHash(contentHash, tokens);
        }

        // 定期输出缓存统计
        logCacheStatistics();

        return tokens;
    }

    /**
     * 实际计算消息的 Token 数（不使用缓存）
     */
    private int calculateTokens(Message message, String modelName) {
        Encoding encoding = getEncoding(modelName);
        int tokens = 0;

        // 计算角色 token（通常是 1-2 个）
        if (message.getRole() != null) {
            tokens += encoding.countTokens(message.getRole());
        }

        // 计算内容 token
        if (message.getContent() != null) {
            tokens += encoding.countTokens(message.getContent());
        }

        // 计算元数据 token（如果存在）
        if (message.getMetadata() != null) {
            try {
                String metadataJson = objectMapper.writeValueAsString(message.getMetadata());
                tokens += encoding.countTokens(metadataJson);
            } catch (Exception e) {
                log.warn("Failed to serialize message metadata for token counting", e);
            }
        }

        // 每条消息的基础开销（格式化的 token）
        tokens += 4;

        return tokens;
    }

    /**
     * 从消息元数据中获取缓存的 Token 数
     */
    private Integer getCachedTokensFromMetadata(Message message, String modelName) {
        if (message.getMetadata() == null) {
            return null;
        }

        // 检查模型是否匹配
        if (!Objects.equals(modelName, message.getMetadata().getModel())) {
            return null;
        }

        return message.getMetadata().getTokenCount();
    }

    /**
     * 将 Token 数缓存到消息元数据中
     */
    private void cacheTokensInMetadata(Message message, String modelName, int tokens) {
        if (message.getMetadata() == null) {
            message.setMetadata(new Message.MessageMetadata());
        }

        message.getMetadata().setTokenCount(tokens);
        message.getMetadata().setModel(modelName);
    }

    /**
     * 生成消息内容的哈希值（用于内容缓存）
     */
    private String generateContentHash(Message message, String modelName) {
        try {
            // 组合关键字段生成哈希
            StringBuilder sb = new StringBuilder();
            sb.append(modelName).append(":");
            sb.append(message.getRole()).append(":");
            sb.append(message.getContent() != null ? message.getContent() : "");

            // 使用简单的哈希算法（Java String hashCode）
            int hash = sb.toString().hashCode();
            return modelName + ":" + hash;
        } catch (Exception e) {
            log.warn("Failed to generate content hash for message {}", message.getId(), e);
            return null;
        }
    }

    /**
     * 将 Token 数缓存到内容哈希缓存中
     */
    private void cacheTokensInContentHash(String contentHash, int tokens) {
        // 检查缓存大小，防止内存溢出
        if (contentHashCache.size() >= MAX_CONTENT_CACHE_SIZE) {
            // 简单的 LRU 策略：清除一半的缓存
            log.info("Content hash cache size exceeded limit ({}), clearing half of the cache", MAX_CONTENT_CACHE_SIZE);
            clearHalfOfCache();
        }

        contentHashCache.put(contentHash, tokens);
    }

    /**
     * 清除一半的缓存（简单的 LRU 实现）
     */
    private void clearHalfOfCache() {
        int targetSize = MAX_CONTENT_CACHE_SIZE / 2;
        int currentSize = contentHashCache.size();
        int toRemove = currentSize - targetSize;

        if (toRemove > 0) {
            contentHashCache.keySet().stream()
                .limit(toRemove)
                .forEach(contentHashCache::remove);
        }
    }

    /**
     * 定期输出缓存统计（每1000次计算输出一次）
     */
    private void logCacheStatistics() {
        long total = totalCalculations.get();
        if (total % 1000 == 0 && total > 0) {
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            double hitRate = (double) hits / total * 100;

            log.info("Token Counter Cache Statistics: Total={}, Hits={}, Misses={}, Hit Rate={:.2f}%, Cache Size={}",
                    total, hits, misses, hitRate, contentHashCache.size());
        }
    }

    /**
     * 获取缓存统计信息（用于监控）
     */
    public Map<String, Object> getCacheStatistics() {
        long total = totalCalculations.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = total > 0 ? (double) hits / total * 100 : 0.0;

        return Map.of(
            "totalCalculations", total,
            "cacheHits", hits,
            "cacheMisses", misses,
            "hitRate", hitRate,
            "contentCacheSize", contentHashCache.size(),
            "maxContentCacheSize", MAX_CONTENT_CACHE_SIZE
        );
    }

    /**
     * 清除所有缓存（用于测试或重置）
     */
    public void clearAllCaches() {
        contentHashCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        totalCalculations.set(0);
        log.info("All token counter caches cleared");
    }

    @Override
    public int countTokens(String text, String modelName) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        Encoding encoding = getEncoding(modelName);
        return encoding.countTokens(text);
    }

    @Override
    public int getModelTokenLimit(String modelName) {
        if (modelName == null) {
            return 8192; // 默认值
        }

        Map<String, Integer> modelLimits = memoryProperties.getModelLimits();
        if (modelLimits != null && modelLimits.containsKey(modelName)) {
            return modelLimits.get(modelName);
        }

        // 根据模型名称推断默认限制
        String lowerModelName = modelName.toLowerCase();
        if (lowerModelName.contains("gpt-4-turbo") || lowerModelName.contains("gpt-4o")) {
            return 128000;
        } else if (lowerModelName.contains("gpt-4")) {
            return 8192;
        } else if (lowerModelName.contains("deepseek")) {
            return 32768;
        } else if (lowerModelName.contains("qwen")) {
            if (lowerModelName.contains("max")) {
                return 8192;
            } else if (lowerModelName.contains("plus")) {
                return 32768;
            }
            return 8192;
        }

        // 默认值
        return 8192;
    }

    /**
     * 获取指定模型的编码器
     */
    private Encoding getEncoding(String modelName) {
        if (modelName == null) {
            return encodingCache.computeIfAbsent("default", k -> Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE));
        }

        return encodingCache.computeIfAbsent(modelName, model -> {
            // 大多数现代模型使用 cl100k_base 编码
            // GPT-4, GPT-3.5, DeepSeek, Qwen 等都使用这个编码
            return Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        });
    }
}

