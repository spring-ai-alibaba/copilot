package com.alibaba.cloud.ai.copilot.memory.token.impl;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import com.alibaba.cloud.ai.copilot.redis.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 混合缓存 Token 计数服务（三层缓存架构）
 * 
 * 缓存层级：
 * L1: Message元数据缓存（对象级，最快，<0.01ms）
 * L2: 本地内存缓存（进程级，很快，<0.01ms）
 * L3: Redis缓存（集群级，共享，~1ms）
 * 
 * 优势：
 * - 结合本地缓存的极致性能
 * - 结合Redis的跨实例共享
 * - 自动降级：Redis不可用时仍可工作
 * 
 * @author better
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "copilot.memory.token-cache.hybrid-mode", havingValue = "true")
public class HybridCachedTokenCounterService implements TokenCounterService {

    private final MemoryProperties memoryProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, Encoding> encodingCache = new ConcurrentHashMap<>();
    
    /**
     * L2: 本地内存缓存
     */
    private final Map<String, Integer> localCache = new ConcurrentHashMap<>();
    
    /**
     * Redis Key 前缀
     */
    private static final String REDIS_KEY_PREFIX = "copilot:token:cache:";
    
    /**
     * Redis 缓存过期时间（24小时）
     */
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    
    /**
     * 本地缓存大小限制
     */
    private static final int MAX_LOCAL_CACHE_SIZE = 5000;
    
    /**
     * 缓存统计
     */
    private final AtomicLong l1Hits = new AtomicLong(0);  // 元数据缓存命中
    private final AtomicLong l2Hits = new AtomicLong(0);  // 本地缓存命中
    private final AtomicLong l3Hits = new AtomicLong(0);  // Redis缓存命中
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCalculations = new AtomicLong(0);
    private final AtomicLong redisErrors = new AtomicLong(0);

    public HybridCachedTokenCounterService(MemoryProperties memoryProperties, ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
        log.info("Initialized HybridCachedTokenCounterService with 3-tier cache architecture");
    }

    @Override
    public int countTokens(List<Message> messages, String modelName) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int totalTokens = 0;
        for (Message message : messages) {
            totalTokens += countTokens(message, modelName);
        }

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
        
        // L1: 检查消息元数据缓存（最快）
        Integer l1Cached = getCachedTokensFromMetadata(message, modelName);
        if (l1Cached != null) {
            l1Hits.incrementAndGet();
            log.trace("L1 cache hit (metadata) for message {}: {} tokens", message.getId(), l1Cached);
            return l1Cached;
        }
        
        // 生成缓存Key
        String cacheKey = generateCacheKey(message, modelName);
        if (cacheKey == null) {
            // 无法生成Key，直接计算
            return calculateAndCache(message, modelName, null);
        }
        
        // L2: 检查本地内存缓存（很快）
        Integer l2Cached = localCache.get(cacheKey);
        if (l2Cached != null) {
            l2Hits.incrementAndGet();
            log.trace("L2 cache hit (local) for message {}: {} tokens", message.getId(), l2Cached);
            // 回填到L1
            cacheTokensInMetadata(message, modelName, l2Cached);
            return l2Cached;
        }
        
        // L3: 检查Redis缓存（共享）
        Integer l3Cached = getFromRedisCache(cacheKey);
        if (l3Cached != null) {
            l3Hits.incrementAndGet();
            log.trace("L3 cache hit (Redis) for message {}: {} tokens", message.getId(), l3Cached);
            // 回填到L2和L1
            cacheToLocal(cacheKey, l3Cached);
            cacheTokensInMetadata(message, modelName, l3Cached);
            return l3Cached;
        }
        
        // 所有缓存未命中，执行实际计算
        cacheMisses.incrementAndGet();
        return calculateAndCache(message, modelName, cacheKey);
    }

    /**
     * 计算Token并缓存到所有层级
     */
    private int calculateAndCache(Message message, String modelName, String cacheKey) {
        int tokens = calculateTokens(message, modelName);
        
        // 缓存到L1（元数据）
        cacheTokensInMetadata(message, modelName, tokens);
        
        // 缓存到L2（本地）和L3（Redis）
        if (cacheKey != null) {
            cacheToLocal(cacheKey, tokens);
            cacheToRedis(cacheKey, tokens);
        }
        
        // 定期输出统计
        logCacheStatistics();
        
        return tokens;
    }

    /**
     * 实际计算Token数
     */
    private int calculateTokens(Message message, String modelName) {
        Encoding encoding = getEncoding(modelName);
        int tokens = 0;

        if (message.getRole() != null) {
            tokens += encoding.countTokens(message.getRole());
        }

        if (message.getContent() != null) {
            tokens += encoding.countTokens(message.getContent());
        }

        if (message.getMetadata() != null) {
            try {
                String metadataJson = objectMapper.writeValueAsString(message.getMetadata());
                tokens += encoding.countTokens(metadataJson);
            } catch (Exception e) {
                log.warn("Failed to serialize message metadata for token counting", e);
            }
        }

        tokens += 4;
        return tokens;
    }

    /**
     * L1: 从消息元数据获取缓存
     */
    private Integer getCachedTokensFromMetadata(Message message, String modelName) {
        if (message.getMetadata() == null) {
            return null;
        }
        
        if (!Objects.equals(modelName, message.getMetadata().getModel())) {
            return null;
        }
        
        return message.getMetadata().getTokenCount();
    }

    /**
     * L1: 缓存到消息元数据
     */
    private void cacheTokensInMetadata(Message message, String modelName, int tokens) {
        if (message.getMetadata() == null) {
            message.setMetadata(new Message.MessageMetadata());
        }
        
        message.getMetadata().setTokenCount(tokens);
        message.getMetadata().setModel(modelName);
    }

    /**
     * 生成缓存Key
     */
    private String generateCacheKey(Message message, String modelName) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(modelName).append(":");
            sb.append(message.getRole()).append(":");
            sb.append(message.getContent() != null ? message.getContent() : "");
            
            int hash = sb.toString().hashCode();
            return modelName + ":" + hash;
        } catch (Exception e) {
            log.warn("Failed to generate cache key for message {}", message.getId(), e);
            return null;
        }
    }

    /**
     * L2: 缓存到本地内存
     */
    private void cacheToLocal(String cacheKey, int tokens) {
        if (localCache.size() >= MAX_LOCAL_CACHE_SIZE) {
            // 简单的LRU：清除一半
            int toRemove = MAX_LOCAL_CACHE_SIZE / 2;
            localCache.keySet().stream()
                .limit(toRemove)
                .forEach(localCache::remove);
            log.debug("Local cache size exceeded limit, cleared {} entries", toRemove);
        }
        
        localCache.put(cacheKey, tokens);
    }

    /**
     * L3: 从Redis获取缓存
     */
    private Integer getFromRedisCache(String cacheKey) {
        try {
            String redisKey = REDIS_KEY_PREFIX + cacheKey;
            Integer cached = RedisUtils.getCacheObject(redisKey);
            return cached;
        } catch (Exception e) {
            redisErrors.incrementAndGet();
            log.debug("Failed to get from Redis cache: {}", e.getMessage());
            return null;  // Redis不可用时自动降级
        }
    }

    /**
     * L3: 缓存到Redis
     */
    private void cacheToRedis(String cacheKey, int tokens) {
        try {
            String redisKey = REDIS_KEY_PREFIX + cacheKey;
            RedisUtils.setCacheObject(redisKey, tokens, REDIS_TTL);
        } catch (Exception e) {
            redisErrors.incrementAndGet();
            log.debug("Failed to cache to Redis: {}", e.getMessage());
            // Redis不可用时不影响主流程
        }
    }

    /**
     * 定期输出缓存统计
     */
    private void logCacheStatistics() {
        long total = totalCalculations.get();
        if (total % 1000 == 0 && total > 0) {
            long l1 = l1Hits.get();
            long l2 = l2Hits.get();
            long l3 = l3Hits.get();
            long misses = cacheMisses.get();
            long errors = redisErrors.get();
            
            double l1Rate = (double) l1 / total * 100;
            double l2Rate = (double) l2 / total * 100;
            double l3Rate = (double) l3 / total * 100;
            double totalHitRate = (double) (l1 + l2 + l3) / total * 100;
            
            log.info("Hybrid Token Cache Statistics: Total={}, L1={} ({:.1f}%), L2={} ({:.1f}%), " +
                    "L3={} ({:.1f}%), Misses={}, Total Hit Rate={:.1f}%, Redis Errors={}, Local Cache Size={}",
                    total, l1, l1Rate, l2, l2Rate, l3, l3Rate, misses, totalHitRate, errors, localCache.size());
        }
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStatistics() {
        long total = totalCalculations.get();
        long l1 = l1Hits.get();
        long l2 = l2Hits.get();
        long l3 = l3Hits.get();
        long misses = cacheMisses.get();
        long errors = redisErrors.get();
        
        double l1Rate = total > 0 ? (double) l1 / total * 100 : 0.0;
        double l2Rate = total > 0 ? (double) l2 / total * 100 : 0.0;
        double l3Rate = total > 0 ? (double) l3 / total * 100 : 0.0;
        double totalHitRate = total > 0 ? (double) (l1 + l2 + l3) / total * 100 : 0.0;
        
        // 使用HashMap避免Map.of()的10个键值对限制
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalCalculations", total);
        stats.put("l1Hits", l1);
        stats.put("l1HitRate", l1Rate);
        stats.put("l2Hits", l2);
        stats.put("l2HitRate", l2Rate);
        stats.put("l3Hits", l3);
        stats.put("l3HitRate", l3Rate);
        stats.put("cacheMisses", misses);
        stats.put("totalHitRate", totalHitRate);
        stats.put("redisErrors", errors);
        stats.put("localCacheSize", localCache.size());
        stats.put("maxLocalCacheSize", MAX_LOCAL_CACHE_SIZE);
        
        return stats;
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCaches() {
        localCache.clear();
        l1Hits.set(0);
        l2Hits.set(0);
        l3Hits.set(0);
        cacheMisses.set(0);
        totalCalculations.set(0);
        redisErrors.set(0);
        
        // 清除Redis缓存
        try {
            RedisUtils.deleteKeys(REDIS_KEY_PREFIX + "*");
            log.info("Cleared all hybrid caches (local + Redis)");
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache: {}", e.getMessage());
        }
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
            return 8192;
        }

        Map<String, Integer> modelLimits = memoryProperties.getModelLimits();
        if (modelLimits != null && modelLimits.containsKey(modelName)) {
            return modelLimits.get(modelName);
        }

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

        return 8192;
    }

    private Encoding getEncoding(String modelName) {
        if (modelName == null) {
            return encodingCache.computeIfAbsent("default", 
                k -> Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE));
        }

        return encodingCache.computeIfAbsent(modelName, 
            model -> Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE));
    }
}
