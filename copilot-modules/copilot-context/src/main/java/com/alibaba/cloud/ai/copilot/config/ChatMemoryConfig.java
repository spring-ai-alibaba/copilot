package com.alibaba.cloud.ai.copilot.config;

import com.alibaba.cloud.ai.copilot.memory.shortterm.CompressibleChatMemoryAdapter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Chat Memory Configuration
 * 配置Spring AI的聊天记忆功能，优先使用可压缩记忆系统
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 配置ChatMemory
     * 优先使用 CompressibleChatMemoryAdapter（如果存在）
     * 否则使用 Spring AI 的 MessageWindowChatMemory
     */
    @Bean
    @Primary
    @ConditionalOnBean(CompressibleChatMemoryAdapter.class)
    public ChatMemory chatMemory(CompressibleChatMemoryAdapter adapter) {
        return adapter;
    }

    /**
     * 备用配置：如果记忆系统未启用，使用 Spring AI 的默认实现
     */
    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory defaultChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20) // 保留最近20条消息
                .build();
    }
}
