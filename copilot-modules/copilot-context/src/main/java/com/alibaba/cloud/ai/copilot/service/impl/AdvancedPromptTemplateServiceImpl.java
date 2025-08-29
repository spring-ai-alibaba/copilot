package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.PromptProperties;
import com.alibaba.cloud.ai.copilot.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高级提示词模板服务实现
 * 支持配置文件管理、缓存、动态加载等特性
 *
 * @author Alibaba Cloud AI Team
 */
@Slf4j
@Service("advancedPromptTemplateService")
@RequiredArgsConstructor
public class AdvancedPromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptProperties promptProperties;
    private final ResourceLoader resourceLoader;
    
    // 模板内容缓存
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Override
    public String renderTemplate(String templateName, Map<String, Object> variables) {
        try {
            String templateContent = getTemplateContent(templateName);
            PromptTemplate promptTemplate = new PromptTemplate(templateContent);
            return promptTemplate.render(variables);
        } catch (Exception e) {
            log.error("Error rendering template: {}", templateName, e);
            throw new RuntimeException("Failed to render template: " + templateName, e);
        }
    }

    @Override
    public String buildSystemPrompt(String fileType, boolean backEnd) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fileType", fileType);
        variables.put("backEnd", backEnd);
        
        return renderTemplate("system-message", variables);
    }

    @Override
    public String buildSystemPromptWithFileType(String fileType, boolean backEnd) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fileType", fileType);
        variables.put("backEnd", backEnd);
        
        // 先渲染基础系统提示词
        String basePrompt = buildSystemPrompt(fileType, backEnd);
        
        // 然后渲染文件类型特定的指令
        String fileTypeInstructions = renderTemplate("file-type-specific", variables);
        
        // 合并两部分
        return basePrompt + "\n\n" + fileTypeInstructions;
    }

    @Override
    public String buildMaxSystemPrompt(Map<String, String> files, String fileType, boolean backEnd) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fileType", fileType);
        variables.put("backEnd", backEnd);
        variables.put("files", files);
        
        return renderTemplate("max-system-prompt", variables);
    }

    @Override
    public String buildPromptEnhancementTemplate(String originalPrompt) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalPrompt", originalPrompt);
        
        return renderTemplate("prompt-enhancement", variables);
    }

    /**
     * 获取模板内容（支持缓存）
     */
    @Cacheable(value = "promptTemplates", condition = "#root.target.promptProperties.defaults.cacheEnabled")
    public String getTemplateContent(String templateName) {
        try {
            // 如果启用缓存且缓存中存在，直接返回
            if (promptProperties.getDefaults().isCacheEnabled() && templateCache.containsKey(templateName)) {
                return templateCache.get(templateName);
            }

            String templatePath = getTemplatePath(templateName);
            var resource = resourceLoader.getResource(templatePath);
            
            if (!resource.exists()) {
                throw new IllegalArgumentException("Template not found: " + templatePath);
            }
            
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            
            // 缓存模板内容
            if (promptProperties.getDefaults().isCacheEnabled()) {
                templateCache.put(templateName, content);
            }
            
            return content;
        } catch (Exception e) {
            log.error("Error loading template: {}", templateName, e);
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }

    /**
     * 根据模板名称获取模板路径
     */
    private String getTemplatePath(String templateName) {
        PromptProperties.Templates templates = promptProperties.getTemplates();
        
        return switch (templateName) {
            case "system-message" -> templates.getSystemMessage();
            case "file-type-specific" -> templates.getFileTypeSpecific();
            case "max-system-prompt" -> templates.getMaxSystemPrompt();
            case "prompt-enhancement" -> templates.getPromptEnhancement();
            default -> throw new IllegalArgumentException("Unknown template: " + templateName);
        };
    }

    /**
     * 清除模板缓存
     */
    public void clearTemplateCache() {
        templateCache.clear();
        log.info("Template cache cleared");
    }

    /**
     * 重新加载指定模板
     */
    public void reloadTemplate(String templateName) {
        templateCache.remove(templateName);
        log.info("Template reloaded: {}", templateName);
    }

    /**
     * 根据文件扩展名自动检测文件类型
     */
    public String detectFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "other";
        }
        
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        return promptProperties.getFileTypeByExtension(extension);
    }

    /**
     * 获取场景特定的配置
     */
    public PromptProperties.ScenarioConfig getScenarioConfig(String scenario) {
        return promptProperties.getScenarioConfig(scenario);
    }
}
