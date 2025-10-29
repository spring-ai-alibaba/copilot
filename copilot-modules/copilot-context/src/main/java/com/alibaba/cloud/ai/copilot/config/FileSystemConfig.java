package com.alibaba.cloud.ai.copilot.config;

import com.alibaba.cloud.ai.copilot.service.FileSystemService;
import com.alibaba.cloud.ai.copilot.service.impl.FileSystemServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件系统配置类
 */
@Configuration
public class FileSystemConfig {

    @Bean
    public FileSystemService fileSystemService() {
        return new FileSystemServiceImpl();
    }
}