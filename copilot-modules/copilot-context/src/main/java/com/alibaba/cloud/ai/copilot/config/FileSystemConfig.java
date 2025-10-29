package com.alibaba.cloud.ai.copilot.config;

import com.alibaba.cloud.ai.copilot.service.FileSystemService;
import com.alibaba.cloud.ai.copilot.service.impl.FileSystemServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件系统配置类
 * 用于条件化启用文件系统功能
 */
@Configuration
@ConditionalOnProperty(name = "app.chat.file-system.enabled", havingValue = "true")
public class FileSystemConfig {

    @Bean
    public FileSystemService fileSystemService() {
        return new FileSystemServiceImpl();
    }
}