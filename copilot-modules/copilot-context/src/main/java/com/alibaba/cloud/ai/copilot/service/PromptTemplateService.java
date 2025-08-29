package com.alibaba.cloud.ai.copilot.service;

import java.util.Map;

/**
 * 提示词模板服务接口
 * 负责管理和渲染提示词模板
 *
 * @author Alibaba Cloud AI Team
 */
public interface PromptTemplateService {

    /**
     * 根据模板名称和变量生成提示词
     *
     * @param templateName 模板名称（不包含.st后缀）
     * @param variables 模板变量
     * @return 渲染后的提示词
     */
    String renderTemplate(String templateName, Map<String, Object> variables);

    /**
     * 构建系统提示词
     *
     * @param fileType 文件类型
     * @param backEnd 是否为后端代码
     * @return 系统提示词
     */
    String buildSystemPrompt(String fileType, boolean backEnd);

    /**
     * 构建带有文件类型特定指令的系统提示词
     *
     * @param fileType 文件类型
     * @param backEnd 是否为后端代码
     * @return 系统提示词
     */
    String buildSystemPromptWithFileType(String fileType, boolean backEnd);

    /**
     * 构建最大系统提示词（包含文件内容）
     *
     * @param files 文件映射
     * @param fileType 文件类型
     * @param backEnd 是否为后端代码
     * @return 系统提示词
     */
    String buildMaxSystemPrompt(Map<String, String> files, String fileType, boolean backEnd);

    /**
     * 构建提示词增强模板
     *
     * @param originalPrompt 原始提示词
     * @return 增强提示词模板
     */
    String buildPromptEnhancementTemplate(String originalPrompt);
}
