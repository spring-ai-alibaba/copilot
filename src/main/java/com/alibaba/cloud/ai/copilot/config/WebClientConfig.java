package com.alibaba.cloud.ai.copilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.HttpProtocol;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

/**
 * 目的: 强制使用 HTTP/1.1
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // 创建并直接配置 HttpClient
        HttpClient httpClient = HttpClient.create()
            .protocol(HttpProtocol.HTTP11);  // 强制使用 HTTP/1.1

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient)) // 集成配置
            .build();
    }
}