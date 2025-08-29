package com.alibaba.cloud.ai.copilot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * 提示词配置类
 * 演示使用@Value注解加载提示词模板
 *
 * @author Alibaba Cloud AI Team
 */
@Data
@Configuration
public class PromptConfig {

    /**
     * 系统消息模板
     */
    @Value("classpath:/prompts/system-message.st")
    private Resource systemMessageTemplate;

    /**
     * 文件类型特定指令模板
     */
    @Value("classpath:/prompts/file-type-specific.st")
    private Resource fileTypeSpecificTemplate;

    /**
     * 最大系统提示词模板
     */
    @Value("classpath:/prompts/max-system-prompt.st")
    private Resource maxSystemPromptTemplate;

    /**
     * 提示词增强模板
     */
    @Value("classpath:/prompts/prompt-enhancement.st")
    private Resource promptEnhancementTemplate;

    /**
     * 获取模板内容
     */
    public String getTemplateContent(String templateName) {
        try {
            Resource resource = getTemplateResource(templateName);
            return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }

    /**
     * 根据模板名称获取对应的Resource
     */
    private Resource getTemplateResource(String templateName) {
        return switch (templateName) {
            case "system-message" -> systemMessageTemplate;
            case "file-type-specific" -> fileTypeSpecificTemplate;
            case "max-system-prompt" -> maxSystemPromptTemplate;
            case "prompt-enhancement" -> promptEnhancementTemplate;
            default -> throw new IllegalArgumentException("Unknown template: " + templateName);
        };
    }
}
