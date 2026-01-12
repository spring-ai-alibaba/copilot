package com.alibaba.cloud.ai.copilot.memory.cache;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.redis.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式内存缓存服务
 * 解决多实例部署时的缓存一致性问题
 *
 * @author better
 */
@Slf4j
@Service
public class MemoryCacheService {

    private static final String CACHE_PREFIX = "copilot:memory:messages:";
    private static final String COMPRESSION_TIME_PREFIX = "copilot:memory:compression_time:";
    private static final String LOCK_PREFIX = "copilot:memory:lock:";
    
    // 缓存过期时间：24小时
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    
    // 压缩时间记录过期时间：1小时
    private static final Duration COMPRESSION_TIME_TTL = Duration.ofHours(1);
    
    private final RedissonClient redissonClient;

    public MemoryCacheService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取消息列表缓存
     *
     * @param conversationId 会话ID
     * @return 消息列表，如果不存在返回 null
     */
    public List<Message> getMessages(String conversationId) {
        try {
            String key = CACHE_PREFIX + conversationId;
            List<Message> messages = RedisUtils.getCacheObject(key);
            
            if (messages != null) {
                log.debug("Cache hit for conversation: {}, {} messages", conversationId, messages.size());
                return new ArrayList<>(messages);
            }
            
            log.debug("Cache miss for conversation: {}", conversationId);
            return null;
        } catch (Exception e) {
            log.error("Failed to get messages from cache for conversation: " + conversationId, e);
            return null;
        }
    }

    /**
     * 设置消息列表缓存
     *
     * @param conversationId 会话ID
     * @param messages       消息列表
     */
    public void setMessages(String conversationId, List<Message> messages) {
        try {
            String key = CACHE_PREFIX + conversationId;
            RedisUtils.setCacheObject(key, new ArrayList<>(messages), CACHE_TTL);
            log.debug("Cached {} messages for conversation: {}", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to set messages to cache for conversation: " + conversationId, e);
        }
    }

    /**
     * 删除消息列表缓存
     *
     * @param conversationId 会话ID
     */
    public void deleteMessages(String conversationId) {
        try {
            String key = CACHE_PREFIX + conversationId;
            RedisUtils.deleteObject(key);
            log.debug("Deleted cache for conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to delete messages from cache for conversation: " + conversationId, e);
        }
    }

    /**
     * 获取上次压缩时间
     *
     * @param conversationId 会话ID
     * @return 压缩时间戳，如果不存在返回 null
     */
    public Long getLastCompressionTime(String conversationId) {
        try {
            String key = COMPRESSION_TIME_PREFIX + conversationId;
            return RedisUtils.getCacheObject(key);
        } catch (Exception e) {
            log.error("Failed to get last compression time for conversation: " + conversationId, e);
            return null;
        }
    }

    /**
     * 设置上次压缩时间
     *
     * @param conversationId 会话ID
     * @param timestamp      时间戳
     */
    public void setLastCompressionTime(String conversationId, long timestamp) {
        try {
            String key = COMPRESSION_TIME_PREFIX + conversationId;
            RedisUtils.setCacheObject(key, timestamp, COMPRESSION_TIME_TTL);
            log.debug("Set last compression time for conversation: {} to {}", conversationId, timestamp);
        } catch (Exception e) {
            log.error("Failed to set last compression time for conversation: " + conversationId, e);
        }
    }

    /**
     * 删除压缩时间记录
     *
     * @param conversationId 会话ID
     */
    public void deleteCompressionTime(String conversationId) {
        try {
            String key = COMPRESSION_TIME_PREFIX + conversationId;
            RedisUtils.deleteObject(key);
        } catch (Exception e) {
            log.error("Failed to delete compression time for conversation: " + conversationId, e);
        }
    }

    /**
     * 获取分布式锁（用于压缩操作）
     *
     * @param conversationId 会话ID
     * @param waitTime       等待时间（秒）
     * @param leaseTime      锁自动释放时间（秒）
     * @return 锁对象，如果获取失败返回 null
     */
    public RLock tryLock(String conversationId, long waitTime, long leaseTime) {
        try {
            String lockKey = LOCK_PREFIX + conversationId;
            RLock lock = redissonClient.getLock(lockKey);
            
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (acquired) {
                log.debug("Acquired lock for conversation: {}", conversationId);
                return lock;
            } else {
                log.debug("Failed to acquire lock for conversation: {}", conversationId);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while trying to acquire lock for conversation: " + conversationId, e);
            return null;
        } catch (Exception e) {
            log.error("Failed to acquire lock for conversation: " + conversationId, e);
            return null;
        }
    }

    /**
     * 释放分布式锁
     *
     * @param lock 锁对象
     */
    public void unlock(RLock lock) {
        try {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock");
            }
        } catch (Exception e) {
            log.error("Failed to release lock", e);
        }
    }

    /**
     * 清除会话的所有缓存数据
     *
     * @param conversationId 会话ID
     */
    public void clearAll(String conversationId) {
        deleteMessages(conversationId);
        deleteCompressionTime(conversationId);
        log.info("Cleared all cache for conversation: {}", conversationId);
    }

    /**
     * 检查缓存是否存在
     *
     * @param conversationId 会话ID
     * @return true 如果缓存存在
     */
    public boolean exists(String conversationId) {
        try {
            String key = CACHE_PREFIX + conversationId;
            return RedisUtils.isExistsObject(key);
        } catch (Exception e) {
            log.error("Failed to check cache existence for conversation: " + conversationId, e);
            return false;
        }
    }

    /**
     * 刷新缓存过期时间
     *
     * @param conversationId 会话ID
     */
    public void refreshExpiration(String conversationId) {
        try {
            String key = CACHE_PREFIX + conversationId;
            RedisUtils.expire(key, CACHE_TTL);
            log.debug("Refreshed cache expiration for conversation: {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to refresh cache expiration for conversation: " + conversationId, e);
        }
    }
}

