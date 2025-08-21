package com.alibaba.cloud.ai.copilot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat Memory Configuration
 * 配置Spring AI的聊天记忆功能，使用显式记忆管理方式
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 配置ChatMemory
     * Spring AI 1.0.0会自动配置JdbcChatMemoryRepository作为底层存储
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20) // 保留最近20条消息
                .build();
    }
}
