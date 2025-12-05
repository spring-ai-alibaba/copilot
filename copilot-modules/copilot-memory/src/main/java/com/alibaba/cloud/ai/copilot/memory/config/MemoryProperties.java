package com.alibaba.cloud.ai.copilot.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆系统配置属性
 *
 * @author better
 */
@Data
@ConfigurationProperties(prefix = "copilot.memory")
public class MemoryProperties {

    /**
     * 短期记忆配置
     */
    private ShortTermMemory shortTerm = new ShortTermMemory();

    /**
     * 长期记忆配置
     */
    private LongTermMemory longTerm = new LongTermMemory();

    /**
     * Token 预算配置
     */
    private TokenBudget tokenBudget = new TokenBudget();

    /**
     * 模型 Token 限制
     */
    private Map<String, Integer> modelLimits = new HashMap<>();

    @Data
    public static class ShortTermMemory {
        /**
         * 是否启用短期记忆
         */
        private boolean enabled = true;

        /**
         * 压缩配置
         */
        private Compression compression = new Compression();
    }

    @Data
    public static class Compression {
        /**
         * 是否启用压缩
         */
        private boolean enabled = true;

        /**
         * 触发压缩的阈值（0.7 = 70%）
         */
        private double triggerThreshold = 0.7;

        /**
         * 触发压缩的最小消息数量（达到此数量时也会触发压缩）
         */
        private int minMessageCount = 10;

        /**
         * 保留的对话比例（0.3 = 保留 30%）
         */
        private double preserveThreshold = 0.3;

        /**
         * 边界检测策略
         */
        private String boundaryStrategy = "USER_MESSAGE";

        /**
         * 用于压缩的模型名称
         */
        private String compressionModel = "gpt-4o-mini";

        /**
         * 用于压缩的 ChatModel Bean 名称
         */
        private String chatModelBeanName = "openAiChatModel";

        /**
         * 摘要格式
         */
        private String summaryFormat = "XML";

        /**
         * 是否异步压缩
         */
        private boolean asyncCompression = true;

        /**
         * 最小压缩间隔（秒）
         */
        private long minIntervalSeconds = 300;
    }

    @Data
    public static class LongTermMemory {
        /**
         * 是否启用长期记忆
         */
        private boolean enabled = true;

        /**
         * 记忆文件名
         */
        private String filename = "COPILOT_MEMORY.md";

        /**
         * 全局记忆目录名
         */
        private String globalDirectory = ".copilot";

        /**
         * 最大搜索深度
         */
        private int maxSearchDepth = 3;

        /**
         * 最大搜索目录数
         */
        private int maxSearchDirs = 50;

        /**
         * 最大文件大小（字节）
         */
        private long maxFileSize = 51200; // 50KB

        /**
         * 忽略的目录模式
         */
        private List<String> ignorePatterns = List.of(
            ".git", "node_modules", "target", "build", "dist"
        );
    }

    @Data
    public static class TokenBudget {
        /**
         * 系统提示词占比
         */
        private double systemPromptRatio = 0.05;

        /**
         * 长期记忆占比
         */
        private double longTermMemoryRatio = 0.05;

        /**
         * 压缩历史占比
         */
        private double compressedHistoryRatio = 0.10;

        /**
         * 保留历史占比
         */
        private double preservedHistoryRatio = 0.30;

        /**
         * 响应缓冲占比
         */
        private double responseBufferRatio = 0.50;
    }
}

