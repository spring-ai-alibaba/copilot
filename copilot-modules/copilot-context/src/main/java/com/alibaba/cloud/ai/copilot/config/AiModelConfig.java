package com.alibaba.cloud.ai.copilot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * AI模型配置类
 * 不再创建默认的OpenAI模型Bean，改为使用动态创建方式
 */
@Slf4j
@Configuration
public class AiModelConfig {

    // 移除默认的OpenAI模型Bean创建
    // 改为在DynamicModelService中动态创建
}
