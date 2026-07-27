package com.alibaba.cloud.ai.copilot.domain.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自定义模型端点健康检测请求。
 *
 * <p>providerCode 决定协议实现，apiUrl 决定实际请求的中转站或官方端点。</p>
 */
@Data
public class CustomModelProviderRequest {

    @NotBlank(message = "Provider Code 不能为空")
    private String providerCode;

    @NotBlank(message = "API URL 不能为空")
    private String apiUrl;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    @NotBlank(message = "测试模型名称不能为空")
    private String testModelName;
}
