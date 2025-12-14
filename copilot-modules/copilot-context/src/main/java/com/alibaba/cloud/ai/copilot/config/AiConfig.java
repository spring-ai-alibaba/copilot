package com.alibaba.cloud.ai.copilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * AI配置类 - 使用 Spring AI 1.1.0 新特性
 *
 * @author Administrator
 */
@Configuration
public class AiConfig {

    /**
     * 配置 RestTemplate
     * 用于 HTTP 调用 MCP 服务
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时 10 秒
        factory.setReadTimeout(60000);     // 读取超时 60 秒（工具执行可能需要较长时间）
        return new RestTemplate(factory);
    }

    /**
     * 配置基础 ChatClient（不带工具）
     * 用于普通对话
     * 
     * 注意：由于项目使用 DynamicModelService 动态获取 ChatModel，
     * 此 Bean 可能不会被使用。如果容器中有多个 ChatModel Bean，
     * 优先使用 openAiChatModel，如果不存在则使用 dashscopeChatModel。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "openAiChatModel")
    public ChatClient chatClient(@org.springframework.beans.factory.annotation.Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个有用的AI助手，能够理解上下文并提供准确的回答。")
                .build();
    }

    /**
     * 配置基础 ChatClient（使用 DashScope）
     * 当 openAiChatModel 不存在时使用
     */
    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    @ConditionalOnBean(name = "dashscopeChatModel")
    public ChatClient dashscopeChatClient(@org.springframework.beans.factory.annotation.Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个有用的AI助手，能够理解上下文并提供准确的回答。")
                .build();
    }
}

