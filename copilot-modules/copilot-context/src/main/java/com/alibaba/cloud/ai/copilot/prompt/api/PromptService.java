package com.alibaba.cloud.ai.copilot.prompt.api;

import java.util.Map;

/**
 * 提示词服务接口
 * 负责提示词的增强、优化和管理
 *
 * @author Alibaba Cloud AI Team
 */
public interface PromptService {

    /**
     * 增强用户提示词
     *
     * @param originalPrompt 原始提示词
     * @param context 上下文信息
     * @return 增强后的提示词
     */
    String enhancePrompt(String originalPrompt, Map<String, Object> context);

    /**
     * 根据模板生成提示词
     *
     * @param templateName 模板名称
     * @param variables 变量映射
     * @return 生成的提示词
     */
    String generateFromTemplate(String templateName, Map<String, Object> variables);

    /**
     * 优化提示词
     *
     * @param prompt 原始提示词
     * @param targetModel 目标模型
     * @return 优化后的提示词
     */
    String optimizePrompt(String prompt, String targetModel);

    /**
     * 验证提示词质量
     *
     * @param prompt 提示词
     * @return 质量评分 (0-100)
     */
    int validatePromptQuality(String prompt);

    /**
     * 获取提示词建议
     *
     * @param prompt 原始提示词
     * @return 改进建议
     */
    String getPromptSuggestions(String prompt);
}