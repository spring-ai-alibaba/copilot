package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.PromptConfig;
import com.alibaba.cloud.ai.copilot.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词模板服务实现
 * 使用Spring AI的PromptTemplate和StringTemplate引擎
 *
 * @author Alibaba Cloud AI Team
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptConfig promptConfig;

    @Override
    public String renderTemplate(String templateName, Map<String, Object> variables) {
        try {
            String templateContent = promptConfig.getTemplateContent(templateName);
            PromptTemplate promptTemplate = new PromptTemplate(templateContent);
            return promptTemplate.render(variables);
        } catch (Exception e) {
            log.error("Error rendering template: {}", templateName, e);
            throw new RuntimeException("Failed to render template: " + templateName, e);
        }
    }

    @Override
    public String buildSystemPrompt(String fileType, boolean backEnd) {
        try {
            String systemMessage = promptConfig.getTemplateContent("system-message");

            // 添加条件逻辑
            StringBuilder prompt = new StringBuilder(systemMessage);

            if ("miniProgram".equals(fileType)) {
                prompt.append("\nIMPORTANT: For any place that uses images, implement using weui's icon library");
            }

            if (backEnd) {
                prompt.append("\nIMPORTANT: You must generate backend code, do not only generate frontend code");
                prompt.append("\nIMPORTANT: Backend must handle CORS for all domains");
            }

            return prompt.toString();
        } catch (Exception e) {
            log.error("Error building system prompt", e);
            throw new RuntimeException("Failed to build system prompt", e);
        }
    }

    @Override
    public String buildSystemPromptWithFileType(String fileType, boolean backEnd) {
        try {
            // 先获取基础系统提示词
            String basePrompt = buildSystemPrompt(fileType, backEnd);

            // 然后添加文件类型特定的指令
            String fileTypeInstructions = getFileTypeSpecificInstructions(fileType);

            // 合并两部分
            if (fileTypeInstructions != null && !fileTypeInstructions.isEmpty()) {
                return basePrompt + "\n\n" + fileTypeInstructions;
            } else {
                return basePrompt;
            }
        } catch (Exception e) {
            log.error("Error building system prompt with file type", e);
            throw new RuntimeException("Failed to build system prompt with file type", e);
        }
    }

    /**
     * 根据文件类型获取特定指令
     */
    private String getFileTypeSpecificInstructions(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return "";
        }

        return switch (fileType.toLowerCase()) {
            case "react" -> """
                REACT SPECIFIC INSTRUCTIONS:
                - Use functional components with hooks (useState, useEffect, etc.)
                - Follow React best practices and patterns
                - Use proper JSX syntax and component structure
                - Implement proper state management
                - Use TypeScript if applicable
                - Include proper prop types and error handling
                - Always create package.json with Vite and React dependencies
                - Command sequence: npm install then npm run dev
                """;
            case "vue" -> """
                VUE SPECIFIC INSTRUCTIONS:
                - Use Vue 3 Composition API when possible
                - Follow Vue.js best practices and conventions
                - Use proper template syntax and directives
                - Implement reactive data and computed properties
                - Use proper component lifecycle hooks
                - Include proper TypeScript support if needed
                - Always create package.json with Vite and Vue dependencies
                - Command sequence: npm install then npm run dev
                """;
            case "angular" -> """
                ANGULAR SPECIFIC INSTRUCTIONS:
                - Use Angular latest version conventions
                - Follow Angular style guide and best practices
                - Use proper component, service, and module structure
                - Implement dependency injection correctly
                - Use TypeScript with proper typing
                - Include proper RxJS usage for async operations
                """;
            case "miniprogram" -> """
                MINI PROGRAM SPECIFIC INSTRUCTIONS:
                - Follow WeChat Mini Program development standards
                - Use proper WXML, WXSS, and JavaScript structure
                - Implement proper page lifecycle methods
                - Use WeUI components and design patterns
                - Handle proper data binding and event handling
                - Include proper API usage and error handling
                """;
            case "nodejs", "node" -> """
                NODE.JS SPECIFIC INSTRUCTIONS:
                - Use modern Node.js features and ES6+ syntax
                - Follow Node.js best practices and patterns
                - Implement proper error handling and logging
                - Use appropriate npm packages and dependencies
                - Include proper async/await usage
                - Implement proper security practices
                """;
            case "python" -> """
                PYTHON SPECIFIC INSTRUCTIONS:
                - Follow PEP 8 style guidelines
                - Use proper Python idioms and patterns
                - Implement proper exception handling
                - Use type hints when appropriate
                - Follow Python best practices for imports and structure
                - Include proper documentation and comments
                """;
            case "java" -> """
                JAVA SPECIFIC INSTRUCTIONS:
                - Follow Java coding conventions and best practices
                - Use proper OOP principles and design patterns
                - Implement proper exception handling
                - Use appropriate Java 8+ features (streams, lambdas, etc.)
                - Include proper package structure and imports
                - Use proper annotations and documentation
                """;
            case "spring", "springboot" -> """
                SPRING BOOT SPECIFIC INSTRUCTIONS:
                - Follow Spring Boot conventions and best practices
                - Use proper dependency injection and annotations
                - Implement proper REST API design
                - Use appropriate Spring Boot starters
                - Include proper configuration and properties
                - Implement proper error handling and validation
                """;
            case "html", "css", "javascript", "js" -> """
                HTML/CSS/JS STATIC PROJECT INSTRUCTIONS:
                - Always create package.json with Vite for development server
                - Use modern HTML5 semantic elements
                - Implement responsive design with CSS Grid/Flexbox
                - Use ES6+ JavaScript features
                - Command sequence: 1) Create files 2) npm install 3) npm run dev
                - Structure: Create complete index.html, style.css, script.js files
                """;
            default -> """
                GENERAL DEVELOPMENT INSTRUCTIONS:
                - Follow language-specific best practices
                - Use proper code structure and organization
                - Implement proper error handling
                - Include appropriate comments and documentation
                - Use modern language features when applicable
                - CRITICAL: For web projects, always create proper package.json with dev server setup
                """;
        };
    }

    @Override
    public String buildMaxSystemPrompt(Map<String, String> files, String fileType, boolean backEnd) {
        try {
            // 先获取基础系统提示词
            String basePrompt = buildSystemPrompt(fileType, backEnd);

            // 构建文件内容部分
            StringBuilder filesContent = new StringBuilder();
            filesContent.append("\nCurrent project files:\n");

            for (Map.Entry<String, String> entry : files.entrySet()) {
                filesContent.append("File: ").append(entry.getKey()).append("\n");
                filesContent.append(entry.getValue()).append("\n\n");
            }

            filesContent.append("IMPORTANT: You can only modify the contents within the directory tree above.\n");
            filesContent.append("IMPORTANT: When updating existing files, make sure to include the complete file content.\n");
            filesContent.append("IMPORTANT: Remember to use the <boltArtifact> and <boltAction> format as shown in the example above.");

            return basePrompt + filesContent.toString();
        } catch (Exception e) {
            log.error("Error building max system prompt", e);
            throw new RuntimeException("Failed to build max system prompt", e);
        }
    }

    @Override
    public String buildPromptEnhancementTemplate(String originalPrompt) {
        try {
            String enhancementContent = promptConfig.getTemplateContent("prompt-enhancement");
            // 使用简单的字符串替换而不是模板引擎
            return enhancementContent.replace("{originalPrompt}", originalPrompt);
        } catch (Exception e) {
            log.error("Error building prompt enhancement template", e);
            throw new RuntimeException("Failed to build prompt enhancement template", e);
        }
    }
}
