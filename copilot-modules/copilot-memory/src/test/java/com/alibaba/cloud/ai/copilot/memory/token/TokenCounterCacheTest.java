package com.alibaba.cloud.ai.copilot.memory.token;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.token.impl.JtokitTokenCounterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token计数缓存测试
 * 验证缓存优化的效果
 *
 * @author better
 */
@SpringBootTest
class TokenCounterCacheTest {

    private JtokitTokenCounterService tokenCounterService;
    private MemoryProperties memoryProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        objectMapper = new ObjectMapper();
        tokenCounterService = new JtokitTokenCounterService(memoryProperties, objectMapper);
        
        // 清除缓存，确保测试独立性
        tokenCounterService.clearAllCaches();
    }

    @Test
    void testMetadataCacheHit() {
        // 创建测试消息
        Message message = createTestMessage("user", "Hello, how are you?");
        String modelName = "gpt-4o-mini";
        
        // 第一次计算（缓存未命中）
        int tokens1 = tokenCounterService.countTokens(message, modelName);
        assertTrue(tokens1 > 0, "Token count should be greater than 0");
        
        // 验证元数据已缓存
        assertNotNull(message.getMetadata(), "Metadata should be set");
        assertEquals(tokens1, message.getMetadata().getTokenCount(), "Token count should be cached in metadata");
        assertEquals(modelName, message.getMetadata().getModel(), "Model name should be cached in metadata");
        
        // 第二次计算（缓存命中）
        int tokens2 = tokenCounterService.countTokens(message, modelName);
        assertEquals(tokens1, tokens2, "Token count should be the same");
        
        // 验证缓存统计
        Map<String, Object> stats = tokenCounterService.getCacheStatistics();
        assertEquals(2L, stats.get("totalCalculations"), "Total calculations should be 2");
        assertEquals(1L, stats.get("cacheHits"), "Cache hits should be 1");
        assertEquals(1L, stats.get("cacheMisses"), "Cache misses should be 1");
    }

    @Test
    void testContentHashCacheHit() {
        // 创建两个内容相同但对象不同的消息
        Message message1 = createTestMessage("user", "Hello, world!");
        Message message2 = createTestMessage("user", "Hello, world!");
        String modelName = "gpt-4o-mini";
        
        // 第一次计算
        int tokens1 = tokenCounterService.countTokens(message1, modelName);
        
        // 第二次计算（不同对象，但内容相同）
        int tokens2 = tokenCounterService.countTokens(message2, modelName);
        
        assertEquals(tokens1, tokens2, "Token count should be the same for same content");
        
        // 验证缓存统计
        Map<String, Object> stats = tokenCounterService.getCacheStatistics();
        assertEquals(2L, stats.get("totalCalculations"), "Total calculations should be 2");
        assertEquals(1L, stats.get("cacheHits"), "Cache hits should be 1 (content hash hit)");
    }

    @Test
    void testDifferentModelInvalidatesCache() {
        Message message = createTestMessage("user", "Test message");
        
        // 使用第一个模型计算
        int tokens1 = tokenCounterService.countTokens(message, "gpt-4o-mini");
        
        // 使用不同模型计算（缓存应该失效）
        int tokens2 = tokenCounterService.countTokens(message, "gpt-4");
        
        // 两个模型的token数可能不同（虽然这个例子中可能相同）
        // 重点是验证缓存逻辑正确
        assertNotNull(message.getMetadata());
        assertEquals("gpt-4", message.getMetadata().getModel(), "Model should be updated");
    }

    @Test
    void testMessageListCounting() {
        List<Message> messages = new ArrayList<>();
        messages.add(createTestMessage("user", "Hello"));
        messages.add(createTestMessage("assistant", "Hi there!"));
        messages.add(createTestMessage("user", "How are you?"));
        
        String modelName = "gpt-4o-mini";
        
        // 第一次计算
        int totalTokens1 = tokenCounterService.countTokens(messages, modelName);
        assertTrue(totalTokens1 > 0, "Total tokens should be greater than 0");
        
        // 第二次计算（应该使用缓存）
        int totalTokens2 = tokenCounterService.countTokens(messages, modelName);
        assertEquals(totalTokens1, totalTokens2, "Total tokens should be the same");
        
        // 验证缓存命中
        Map<String, Object> stats = tokenCounterService.getCacheStatistics();
        long hits = (Long) stats.get("cacheHits");
        assertTrue(hits >= 3, "Should have at least 3 cache hits for second calculation");
    }

    @Test
    void testPerformanceImprovement() {
        // 创建大量消息
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            messages.add(createTestMessage("user", "This is test message number " + i));
        }
        
        String modelName = "gpt-4o-mini";
        
        // 第一次计算（无缓存）
        long start1 = System.currentTimeMillis();
        int tokens1 = tokenCounterService.countTokens(messages, modelName);
        long duration1 = System.currentTimeMillis() - start1;
        
        // 第二次计算（有缓存）
        long start2 = System.currentTimeMillis();
        int tokens2 = tokenCounterService.countTokens(messages, modelName);
        long duration2 = System.currentTimeMillis() - start2;
        
        assertEquals(tokens1, tokens2, "Token counts should be the same");
        
        // 验证性能提升（第二次应该显著快于第一次）
        System.out.println("First calculation: " + duration1 + "ms");
        System.out.println("Second calculation (cached): " + duration2 + "ms");
        System.out.println("Performance improvement: " + (duration1 - duration2) + "ms (" + 
                          String.format("%.1f", (1.0 - (double)duration2/duration1) * 100) + "% faster)");
        
        // 缓存应该至少快50%
        assertTrue(duration2 < duration1 * 0.5, 
                  "Cached calculation should be at least 50% faster");
    }

    @Test
    void testCacheStatistics() {
        // 执行一些计算
        for (int i = 0; i < 10; i++) {
            Message message = createTestMessage("user", "Message " + i);
            tokenCounterService.countTokens(message, "gpt-4o-mini");
            
            // 重复计算相同消息
            tokenCounterService.countTokens(message, "gpt-4o-mini");
        }
        
        // 获取统计信息
        Map<String, Object> stats = tokenCounterService.getCacheStatistics();
        
        assertNotNull(stats, "Statistics should not be null");
        assertEquals(20L, stats.get("totalCalculations"), "Total calculations should be 20");
        assertEquals(10L, stats.get("cacheHits"), "Cache hits should be 10");
        assertEquals(10L, stats.get("cacheMisses"), "Cache misses should be 10");
        assertEquals(50.0, (Double) stats.get("hitRate"), 0.1, "Hit rate should be 50%");
        
        System.out.println("Cache Statistics: " + stats);
    }

    @Test
    void testCacheClear() {
        Message message = createTestMessage("user", "Test message");
        
        // 计算并缓存
        tokenCounterService.countTokens(message, "gpt-4o-mini");
        
        // 验证缓存存在
        Map<String, Object> statsBefore = tokenCounterService.getCacheStatistics();
        assertTrue((Long) statsBefore.get("totalCalculations") > 0);
        
        // 清除缓存
        tokenCounterService.clearAllCaches();
        
        // 验证缓存已清除
        Map<String, Object> statsAfter = tokenCounterService.getCacheStatistics();
        assertEquals(0L, statsAfter.get("totalCalculations"));
        assertEquals(0L, statsAfter.get("cacheHits"));
        assertEquals(0L, statsAfter.get("cacheMisses"));
    }

    /**
     * 创建测试消息
     */
    private Message createTestMessage(String role, String content) {
        Message message = new Message();
        message.setId("msg_" + System.nanoTime());
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        return message;
    }
}
