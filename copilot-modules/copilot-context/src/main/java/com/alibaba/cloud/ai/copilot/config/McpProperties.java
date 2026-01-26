package com.alibaba.cloud.ai.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 配置属性
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /**
     * 客户端配置
     */
    private ClientConfig client = new ClientConfig();


    @Data
    public static class ClientConfig {
        /**
         * 请求超时时间（秒）
         */
        private int requestTimeout = 30;

        /**
         * 连接超时时间（秒）
         */
        private int connectionTimeout = 10;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
    }
}

