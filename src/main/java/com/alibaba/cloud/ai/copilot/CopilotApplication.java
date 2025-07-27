package com.alibaba.cloud.ai.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.alibaba.cloud.ai.copilot.config.AppProperties;

/**
 * 主要功能：
 * 1. 文件读取、写入、编辑
 * 2. 目录列表和结构查看
 * 4. 连续性文件操作
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableAspectJAutoProxy
public class CopilotApplication {

    private static final Logger logger = LoggerFactory.getLogger(CopilotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CopilotApplication.class, args);
        logger.info("Application started successfully!");
    }


}
