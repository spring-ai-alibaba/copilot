package com.alibaba.cloud.ai.copilot.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * MCP 配置类
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Configuration
public class McpConfig {

    /**
     * RestTemplate 用于调用外部市场 API
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }
}

