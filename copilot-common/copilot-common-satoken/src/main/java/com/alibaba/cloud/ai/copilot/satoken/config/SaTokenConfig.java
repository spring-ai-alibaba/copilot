package com.alibaba.cloud.ai.copilot.satoken.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * sa-token 配置（精简版）
 * <p>
 * 不再注册自定义 SaTokenDao（使用 Sa-Token 默认内存存储），
 * 不再注册 JWT StpLogic（使用默认 token 风格）。
 * 保留空配置类以维持 AutoConfiguration.imports 注册有效。
 *
 */
@AutoConfiguration
public class SaTokenConfig implements WebMvcConfigurer {
}
