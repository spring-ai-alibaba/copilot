package com.alibaba.cloud.ai.copilot.memory.token;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.token.impl.JtokitTokenCounterService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Token 缓存性能演示
 * 
 * 运行此类可以直观看到缓存优化的效果
 *
 * @author better
 */
public class TokenCachePerformanceDemo {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Token 计数缓存性能演示");
        System.out.println("=".repeat(80));
        System.out.println();

        // 初始化服务
        MemoryProperties properties = new MemoryProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        JtokitTokenCounterService tokenCounter = new JtokitTokenCounterService(properties, objectMapper);

        // 演示1: 单条消息重复计算
        demo1SingleMessageRepeatedCalculation(tokenCounter);
        
        System.out.println();
        
        // 演示2: 消息列表批量计算
        demo2MessageListBatchCalculation(tokenCounter);
        
        System.out.println();
        
        // 演示3: 相同内容不同对象
        demo3SameContentDifferentObjects(tokenCounter);
        
        System.out.println();
        
        // 演示4: 缓存统计
        demo4CacheStatistics(tokenCounter);
        
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("演示完成！");
        System.out.println("=".repeat(80));
    }

    /**
     * 演示1: 单条消息重复计算
     */
    private static void demo1SingleMessageRepeatedCalculation(JtokitTokenCounterService tokenCounter) {
        System.out.println("【演示1】单条消息重复计算");
        System.out.println("-".repeat(80));
        
        Message message = createMessage("user", "Hello, how are you today? I hope you're doing well!");
        String modelName = "gpt-4o-mini";
        
        // 第一次计算（缓存未命中）
        long start1 = System.nanoTime();
        int tokens1 = tokenCounter.countTokens(message, modelName);
        long duration1 = System.nanoTime() - start1;
        
        System.out.println("第一次计算（缓存未命中）:");
        System.out.println("  Token数量: " + tokens1);
        System.out.println("  耗时: " + String.format("%.3f", duration1 / 1_000_000.0) + " ms");
        
        // 第二次计算（缓存命中）
        long start2 = System.nanoTime();
        int tokens2 = tokenCounter.countTokens(message, modelName);
        long duration2 = System.nanoTime() - start2;
        
        System.out.println("\n第二次计算（缓存命中）:");
        System.out.println("  Token数量: " + tokens2);
        System.out.println("  耗时: " + String.format("%.3f", duration2 / 1_000_000.0) + " ms");
        
        // 性能提升
        double speedup = (double) duration1 / duration2;
        System.out.println("\n性能提升:");
        System.out.println("  速度提升: " + String.format("%.1f", speedup) + " 倍");
        System.out.println("  时间节省: " + String.format("%.3f", (duration1 - duration2) / 1_000_000.0) + " ms");
        System.out.println("  效率提升: " + String.format("%.1f", (1.0 - (double)duration2/duration1) * 100) + "%");
    }

    /**
     * 演示2: 消息列表批量计算
     */
    private static void demo2MessageListBatchCalculation(JtokitTokenCounterService tokenCounter) {
        System.out.println("【演示2】消息列表批量计算（100条消息）");
        System.out.println("-".repeat(80));
        
        // 创建100条测试消息
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            messages.add(createMessage(
                i % 2 == 0 ? "user" : "assistant",
                "This is test message number " + i + ". It contains some sample text for token counting."
            ));
        }
        
        String modelName = "gpt-4o-mini";
        
        // 第一次计算（缓存未命中）
        long start1 = System.nanoTime();
        int totalTokens1 = tokenCounter.countTokens(messages, modelName);
        long duration1 = System.nanoTime() - start1;
        
        System.out.println("第一次计算（缓存未命中）:");
        System.out.println("  消息数量: " + messages.size());
        System.out.println("  总Token数: " + totalTokens1);
        System.out.println("  耗时: " + String.format("%.3f", duration1 / 1_000_000.0) + " ms");
        System.out.println("  吞吐量: " + String.format("%.0f", totalTokens1 / (duration1 / 1_000_000_000.0)) + " tokens/秒");
        
        // 第二次计算（缓存命中）
        long start2 = System.nanoTime();
        int totalTokens2 = tokenCounter.countTokens(messages, modelName);
        long duration2 = System.nanoTime() - start2;
        
        System.out.println("\n第二次计算（缓存命中）:");
        System.out.println("  消息数量: " + messages.size());
        System.out.println("  总Token数: " + totalTokens2);
        System.out.println("  耗时: " + String.format("%.3f", duration2 / 1_000_000.0) + " ms");
        System.out.println("  吞吐量: " + String.format("%.0f", totalTokens2 / (duration2 / 1_000_000_000.0)) + " tokens/秒");
        
        // 性能提升
        double speedup = (double) duration1 / duration2;
        System.out.println("\n性能提升:");
        System.out.println("  速度提升: " + String.format("%.1f", speedup) + " 倍");
        System.out.println("  时间节省: " + String.format("%.3f", (duration1 - duration2) / 1_000_000.0) + " ms");
        System.out.println("  吞吐量提升: " + String.format("%.1f", speedup) + " 倍");
    }

    /**
     * 演示3: 相同内容不同对象
     */
    private static void demo3SameContentDifferentObjects(JtokitTokenCounterService tokenCounter) {
        System.out.println("【演示3】相同内容不同对象（内容哈希缓存）");
        System.out.println("-".repeat(80));
        
        String modelName = "gpt-4o-mini";
        String content = "This is a test message with the same content.";
        
        // 创建第一个消息对象
        Message message1 = createMessage("user", content);
        long start1 = System.nanoTime();
        int tokens1 = tokenCounter.countTokens(message1, modelName);
        long duration1 = System.nanoTime() - start1;
        
        System.out.println("第一个消息对象:");
        System.out.println("  消息ID: " + message1.getId());
        System.out.println("  Token数量: " + tokens1);
        System.out.println("  耗时: " + String.format("%.3f", duration1 / 1_000_000.0) + " ms");
        
        // 创建第二个消息对象（内容相同）
        Message message2 = createMessage("user", content);
        long start2 = System.nanoTime();
        int tokens2 = tokenCounter.countTokens(message2, modelName);
        long duration2 = System.nanoTime() - start2;
        
        System.out.println("\n第二个消息对象（内容相同）:");
        System.out.println("  消息ID: " + message2.getId());
        System.out.println("  Token数量: " + tokens2);
        System.out.println("  耗时: " + String.format("%.3f", duration2 / 1_000_000.0) + " ms");
        
        System.out.println("\n验证:");
        System.out.println("  对象相同: " + (message1 == message2));
        System.out.println("  内容相同: " + content.equals(message2.getContent()));
        System.out.println("  Token相同: " + (tokens1 == tokens2));
        System.out.println("  命中内容哈希缓存: " + (duration2 < duration1 * 0.5));
    }

    /**
     * 演示4: 缓存统计
     */
    private static void demo4CacheStatistics(JtokitTokenCounterService tokenCounter) {
        System.out.println("【演示4】缓存统计信息");
        System.out.println("-".repeat(80));
        
        Map<String, Object> stats = tokenCounter.getCacheStatistics();
        
        System.out.println("缓存统计:");
        System.out.println("  总计算次数: " + stats.get("totalCalculations"));
        System.out.println("  缓存命中次数: " + stats.get("cacheHits"));
        System.out.println("  缓存未命中次数: " + stats.get("cacheMisses"));
        System.out.println("  缓存命中率: " + String.format("%.2f", stats.get("hitRate")) + "%");
        System.out.println("  内容缓存大小: " + stats.get("contentCacheSize"));
        System.out.println("  最大缓存大小: " + stats.get("maxContentCacheSize"));
        
        // 计算节省的时间（假设每次计算2ms）
        long cacheHits = (Long) stats.get("cacheHits");
        double savedTime = cacheHits * 2.0; // 假设每次节省2ms
        System.out.println("\n预估性能收益:");
        System.out.println("  节省计算时间: " + String.format("%.1f", savedTime) + " ms");
        System.out.println("  节省计算时间: " + String.format("%.2f", savedTime / 1000.0) + " 秒");
    }

    /**
     * 创建测试消息
     */
    private static Message createMessage(String role, String content) {
        Message message = new Message();
        message.setId("msg_" + System.nanoTime());
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        return message;
    }
}
