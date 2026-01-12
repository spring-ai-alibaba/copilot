package com.alibaba.cloud.ai.copilot.domain.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;



/**
 * OpenAI Compatible Provider 健康检测请求
 * 用于前端添加自定义 OpenAI 兼容供应商
 */
@Data
public class OpenAiCompatibleRequest {

    /**
     * API 基础 URL（必填）
     * 例如：https://api.example.com/v1
     */
    @NotBlank(message = "API URL 不能为空")
    private String apiUrl;

    /**
     * API Key（必填）
     */
    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    /**
     * 测试模型名称（必填）
     * 用于健康检测时调用的模型
     */
    @NotBlank(message = "测试模型名称不能为空")
    private String testModelName;
}
