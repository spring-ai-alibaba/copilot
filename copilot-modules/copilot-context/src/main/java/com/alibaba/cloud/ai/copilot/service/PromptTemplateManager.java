package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.config.PromptProperties;
import com.alibaba.cloud.ai.copilot.service.impl.AdvancedPromptTemplateServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词模板管理器
 * 提供便捷的API来管理和使用提示词模板
 *
 * @author Alibaba Cloud AI Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateManager {

    private final AdvancedPromptTemplateServiceImpl advancedPromptTemplateService;
    private final PromptProperties promptProperties;

    /**
     * 构建代码生成提示词
     */
    public String buildCodeGenerationPrompt(String fileType, boolean backEnd, String userRequest) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fileType", fileType);
        variables.put("backEnd", backEnd);
        variables.put("userRequest", userRequest);
        
        // 获取场景配置
        PromptProperties.ScenarioConfig config = promptProperties.getScenarioConfig("code-generation");
        variables.put("temperature", config.getTemperature());
        variables.put("maxTokens", config.getMaxTokens());
        
        if (fileType != null && !fileType.isEmpty()) {
            return advancedPromptTemplateService.buildSystemPromptWithFileType(fileType, backEnd);
        } else {
            return advancedPromptTemplateService.buildSystemPrompt(fileType, backEnd);
        }
    }

    /**
     * 构建代码审查提示词
     */
    public String buildCodeReviewPrompt(String code, String language) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("code", code);
        variables.put("language", language);
        variables.put("scenario", "code-review");
        
        // 可以创建专门的代码审查模板
        return advancedPromptTemplateService.renderTemplate("system-message", variables);
    }

    /**
     * 构建调试帮助提示词
     */
    public String buildDebuggingPrompt(String errorMessage, String code, String language) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("errorMessage", errorMessage);
        variables.put("code", code);
        variables.put("language", language);
        variables.put("scenario", "debugging");
        
        return advancedPromptTemplateService.renderTemplate("system-message", variables);
    }

    /**
     * 根据文件名自动检测并构建提示词
     */
    public String buildPromptByFileName(String fileName, boolean backEnd) {
        String fileType = advancedPromptTemplateService.detectFileType(fileName);
        log.info("Detected file type '{}' for file: {}", fileType, fileName);
        
        return buildCodeGenerationPrompt(fileType, backEnd, "");
    }

    /**
     * 增强用户提示词
     */
    public String enhanceUserPrompt(String originalPrompt) {
        return advancedPromptTemplateService.buildPromptEnhancementTemplate(originalPrompt);
    }

    /**
     * 重新加载所有模板
     */
    public void reloadAllTemplates() {
        advancedPromptTemplateService.clearTemplateCache();
        log.info("All prompt templates reloaded");
    }

    /**
     * 获取支持的文件类型
     */
    public Map<String, String> getSupportedFileTypes() {
        return promptProperties.getFileTypes();
    }

    /**
     * 获取可用的场景配置
     */
    public Map<String, PromptProperties.ScenarioConfig> getAvailableScenarios() {
        return promptProperties.getScenarios();
    }

    /**
     * 动态添加自定义模板变量
     */
    public String renderCustomTemplate(String templateName, Map<String, Object> customVariables) {
        // 添加一些默认变量
        Map<String, Object> variables = new HashMap<>(customVariables);
        variables.putIfAbsent("timestamp", System.currentTimeMillis());
        variables.putIfAbsent("version", "1.0.0");
        
        return advancedPromptTemplateService.renderTemplate(templateName, variables);
    }
}
