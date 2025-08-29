package com.alibaba.cloud.ai.copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词配置属性类
 * 支持通过配置文件灵活管理提示词模板路径和参数
 *
 * @author Alibaba Cloud AI Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "prompts")
public class PromptProperties {

    /**
     * 模板文件路径配置
     */
    private Templates templates = new Templates();

    /**
     * 默认配置
     */
    private Defaults defaults = new Defaults();

    /**
     * 文件类型映射
     */
    private Map<String, String> fileTypes = new HashMap<>();

    /**
     * 特定场景配置
     */
    private Map<String, ScenarioConfig> scenarios = new HashMap<>();

    @Data
    public static class Templates {
        private String systemMessage = "classpath:/prompts/system-message.st";
        private String fileTypeSpecific = "classpath:/prompts/file-type-specific.st";
        private String maxSystemPrompt = "classpath:/prompts/max-system-prompt.st";
        private String promptEnhancement = "classpath:/prompts/prompt-enhancement.st";
    }

    @Data
    public static class Defaults {
        private boolean cacheEnabled = true;
        private int cacheTtl = 60;
        private int renderTimeout = 30;
    }

    @Data
    public static class ScenarioConfig {
        private double temperature = 0.3;
        private int maxTokens = 32000;  // 增加到32K以支持完整代码生成
        private Map<String, Object> additionalParams = new HashMap<>();
    }

    /**
     * 根据文件扩展名获取文件类型
     */
    public String getFileTypeByExtension(String extension) {
        return fileTypes.getOrDefault(extension.toLowerCase(), "other");
    }

    /**
     * 获取场景配置
     */
    public ScenarioConfig getScenarioConfig(String scenario) {
        return scenarios.getOrDefault(scenario, new ScenarioConfig());
    }
}
