package com.alibaba.cloud.ai.copilot.memory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步配置
 * 用于压缩任务的异步执行
 *
 * @author better
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "compressionExecutor")
    public Executor compressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("compression-");
        executor.initialize();
        return executor;
    }
}

