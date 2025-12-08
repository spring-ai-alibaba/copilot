package com.alibaba.cloud.ai.copilot.memory.shortterm;

import com.alibaba.cloud.ai.copilot.memory.cache.MemoryCacheService;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressionService;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressedSummary;
import com.alibaba.cloud.ai.copilot.memory.compression.MessageBoundaryDetector;
import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 可压缩聊天记忆服务
 * 提供对话历史的存储、检索和智能压缩功能
 * 使用 Redis 分布式缓存保证多实例部署时的缓存一致性
 *
 * @author better
 */
@Slf4j
@Service
public class CompressibleChatMemory {

    private final MemoryProperties memoryProperties;
    private final TokenCounterService tokenCounterService;
    private final MessageBoundaryDetector boundaryDetector;
    private final CompressionService compressionService;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryCacheService cacheService;

    public CompressibleChatMemory(
            MemoryProperties memoryProperties,
            TokenCounterService tokenCounterService,
            MessageBoundaryDetector boundaryDetector,
            CompressionService compressionService,
            ChatMessageRepository chatMessageRepository,
            MemoryCacheService cacheService) {
        this.memoryProperties = memoryProperties;
        this.tokenCounterService = tokenCounterService;
        this.boundaryDetector = boundaryDetector;
        this.compressionService = compressionService;
        this.chatMessageRepository = chatMessageRepository;
        this.cacheService = cacheService;
    }

    /**
     * 添加消息到对话历史
     * 采用"先写数据库，后更新缓存"的策略保证数据一致性
     */
    public void add(String conversationId, Message message) {
        try {
            // 1. 先保存到数据库（数据源）
            chatMessageRepository.save(conversationId, message);
            
            // 2. 数据库保存成功后，更新缓存
            List<Message> messages = get(conversationId);
            messages.add(message);
            cacheService.setMessages(conversationId, messages);
            
            log.debug("Added message to conversation {}, total {} messages", 
                    conversationId, messages.size());
            
            // 3. 检查是否需要压缩
            checkAndCompress(conversationId);
            
        } catch (Exception e) {
            // 如果出现任何异常，使缓存失效，强制下次从数据库加载
            log.error("Failed to add message to conversation " + conversationId + 
                    ", invalidating cache", e);
            cacheService.deleteMessages(conversationId);
            throw e;
        }
    }

    /**
     * 获取对话历史
     * 采用 Cache-Aside 模式：先读缓存，缓存没有再读数据库，然后回填缓存
     */
    public List<Message> get(String conversationId) {
        // 1. 先从缓存获取
        List<Message> cached = cacheService.getMessages(conversationId);
        if (cached != null) {
            // 刷新缓存过期时间，实现 LRU 效果
            cacheService.refreshExpiration(conversationId);
            return cached;
        }

        // 2. 缓存未命中，从数据库加载
        log.debug("Cache miss for conversation {}, loading from database", conversationId);
        List<Message> messages = chatMessageRepository.load(conversationId);
        
        // 3. 回填缓存
        if (messages != null && !messages.isEmpty()) {
            cacheService.setMessages(conversationId, messages);
        }
        
        return new ArrayList<>(messages);
    }

    /**
     * 检查并压缩历史
     */
    private void checkAndCompress(String conversationId) {
        if (!memoryProperties.getShortTerm().isEnabled() ||
            !memoryProperties.getShortTerm().getCompression().isEnabled()) {
            return;
        }

        List<Message> messages = get(conversationId);
        if (messages.isEmpty()) {
            return;
        }

        // 检查是否达到压缩阈值
        if (!shouldCompress(conversationId, messages)) {
            return;
        }

        // 检查压缩间隔
        if (!canCompress(conversationId)) {
            return;
        }

        // 异步压缩
        if (memoryProperties.getShortTerm().getCompression().isAsyncCompression()) {
            compressHistoryAsync(conversationId);
        } else {
            compressHistory(conversationId);
        }
    }

    /**
     * 判断是否需要压缩
     */
    private boolean shouldCompress(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        
        // 获取模型名称（从第一条消息的元数据中获取，或使用默认值）
        String modelName = extractModelName(messages);
        
        int currentTokens = tokenCounterService.countTokens(messages, modelName);
        int maxTokens = tokenCounterService.getModelTokenLimit(modelName);
        int messageCount = messages.size();
        
        double usage = (double) currentTokens / maxTokens;
        double threshold = memoryProperties.getShortTerm().getCompression().getTriggerThreshold();
        int minMessageCount = memoryProperties.getShortTerm().getCompression().getMinMessageCount();
        
        // 检查两个条件：Token 使用率 或 消息数量
        boolean tokenThresholdMet = usage >= threshold;
        boolean messageCountThresholdMet = messageCount >= minMessageCount;
        boolean shouldCompress = tokenThresholdMet || messageCountThresholdMet;
        
        // 记录详细的 Token 使用情况（用于调试）
        log.info("Conversation {} compression check: {} messages, {} tokens / {} max tokens ({}%), " +
                        "token threshold: {}%, message threshold: {} messages. " +
                        "Token threshold met: {}, Message threshold met: {}, Will compress: {}",
                conversationId, messageCount, currentTokens, maxTokens, 
                String.format("%.2f", usage * 100), 
                String.format("%.2f", threshold * 100),
                minMessageCount,
                tokenThresholdMet, messageCountThresholdMet, shouldCompress);
        
        if (shouldCompress) {
            String reason = tokenThresholdMet && messageCountThresholdMet 
                    ? "both token and message count thresholds" 
                    : (tokenThresholdMet ? "token threshold" : "message count threshold");
            log.info("Conversation {} triggering compression due to: {}", conversationId, reason);
        }
        
        return shouldCompress;
    }

    /**
     * 判断是否可以压缩（检查时间间隔）
     */
    private boolean canCompress(String conversationId) {
        Long lastTime = cacheService.getLastCompressionTime(conversationId);
        if (lastTime == null) {
            return true;
        }

        long minInterval = memoryProperties.getShortTerm().getCompression().getMinIntervalSeconds() * 1000;
        long elapsed = System.currentTimeMillis() - lastTime;

        boolean canCompress = elapsed >= minInterval;
        if (!canCompress) {
            log.debug("Conversation {} cannot be compressed yet. Elapsed time: {}ms, required interval: {}ms",
                conversationId, elapsed, minInterval);
        }
        return canCompress;
    }

    /**
     * 压缩历史（同步）
     * 使用分布式锁防止并发压缩，采用"先写数据库，后删除缓存"策略保证一致性
     */
    public void compressHistory(String conversationId) {
        RLock lock = null;
        try {
            // 1. 获取分布式锁，防止并发压缩
            // 等待时间：5秒，锁自动释放时间：60秒
            lock = cacheService.tryLock(conversationId, 5, 60);
            if (lock == null) {
                log.warn("Failed to acquire lock for compressing conversation {}, skipping compression", 
                        conversationId);
                return;
            }

            // 2. 获取最新的消息列表
            List<Message> messages = get(conversationId);
            if (messages.isEmpty()) {
                log.debug("No messages to compress for conversation {}", conversationId);
                return;
            }

            // 3. 找到压缩边界
            double preserveThreshold = memoryProperties.getShortTerm().getCompression().getPreserveThreshold();
            int boundaryIndex = boundaryDetector.findCompressionBoundary(messages, preserveThreshold);

            if (boundaryIndex <= 0) {
                log.warn("No valid compression boundary found for conversation {}", conversationId);
                return;
            }

            // 4. 分割消息
            List<Message> toCompress = new ArrayList<>(messages.subList(0, boundaryIndex));
            List<Message> toPreserve = new ArrayList<>(messages.subList(boundaryIndex, messages.size()));

            log.info("Compressing {} messages, preserving {} messages for conversation {}",
                    toCompress.size(), toPreserve.size(), conversationId);

            // 5. 调用 AI 压缩旧消息
            String modelName = extractModelName(messages);
            CompressedSummary summary = compressionService.compressMessages(toCompress, modelName);

            // 6. 检查压缩是否成功
            if (summary.getMainTopics() != null && 
                summary.getMainTopics().contains("对话历史压缩失败")) {
                log.warn("Compression failed for conversation {}, keeping original messages", conversationId);
                return;
            }

            // 7. 创建摘要消息
            Message summaryMessage = createSummaryMessage(summary, toCompress.size());

            // 8. 重组历史记录
            List<Message> newHistory = new ArrayList<>();
            newHistory.add(summaryMessage);
            newHistory.addAll(toPreserve);

            // 9. 先更新数据库（数据源）
            chatMessageRepository.replace(conversationId, newHistory);

            // 10. 数据库更新成功后，删除缓存（让下次读取时从数据库加载最新数据）
            // 采用 Cache-Aside 模式的删除策略，比直接更新缓存更安全
            cacheService.deleteMessages(conversationId);

            // 11. 更新压缩时间
            cacheService.setLastCompressionTime(conversationId, System.currentTimeMillis());

            log.info("Successfully compressed conversation {}: {} messages -> {} messages",
                    conversationId, messages.size(), newHistory.size());

        } catch (Exception e) {
            log.error("Failed to compress history for conversation " + conversationId, e);
            // 出现异常时，删除缓存以保证数据一致性
            cacheService.deleteMessages(conversationId);
        } finally {
            // 12. 释放分布式锁
            if (lock != null) {
                cacheService.unlock(lock);
            }
        }
    }

    /**
     * 异步压缩历史
     */
    @Async("compressionExecutor")
    public void compressHistoryAsync(String conversationId) {
        compressHistory(conversationId);
    }

    /**
     * 创建摘要消息
     */
    private Message createSummaryMessage(CompressedSummary summary, int originalCount) {
        Message.MessageMetadata metadata = new Message.MessageMetadata();
        metadata.setSource("compression");
        metadata.setIsCompressedSummary(true);
        metadata.setOriginalMessageCount(originalCount);
        
        Message message = new Message();
        message.setId("summary_" + System.currentTimeMillis());
        message.setRole("system");
        message.setContent("[历史对话摘要]\n" + summary.toXml());
        message.setTimestamp(LocalDateTime.now());
        message.setMetadata(metadata);

        return message;
    }

    /**
     * 从消息列表中提取模型名称
     */
    private String extractModelName(List<Message> messages) {
        for (Message message : messages) {
            if (message.getMetadata() != null && message.getMetadata().getModel() != null) {
                return message.getMetadata().getModel();
            }
        }
        return "gpt-4o-mini"; // 默认模型
    }

    /**
     * 清除对话历史
     * 采用"先删数据库，后删缓存"的策略
     */
    public void clear(String conversationId) {
        try {
            // 1. 先删除数据库中的数据
            chatMessageRepository.delete(conversationId);
            
            // 2. 然后清除所有相关的缓存
            cacheService.clearAll(conversationId);
            
            log.info("Cleared history and cache for conversation: {}", conversationId);
        } catch (Exception e) {
            // 如果数据库删除失败，缓存不会被删除，保证了一致性
            // 如果缓存删除失败，数据会不一致（脏数据），但有过期时间 TTL 作为兜底
            log.error("Failed to clear history for conversation " + conversationId, e);
            throw e;
        }
    }
}

