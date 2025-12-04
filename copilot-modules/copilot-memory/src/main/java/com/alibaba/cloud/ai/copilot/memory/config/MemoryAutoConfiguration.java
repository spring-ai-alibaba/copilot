package com.alibaba.cloud.ai.copilot.memory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆系统自动配置类
 *
 * @author better
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryAutoConfiguration {
}

