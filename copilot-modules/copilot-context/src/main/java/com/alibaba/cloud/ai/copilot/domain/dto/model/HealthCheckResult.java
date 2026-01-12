package com.alibaba.cloud.ai.copilot.domain.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 健康检测结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResult {

    /**
     * 是否健康
     */
    private boolean healthy;

    /**
     * 消息
     */
    private String message;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 测试使用的模型名称
     */
    private String testModelName;

    /**
     * 该模型支持的最大 token 数
     */
    private Integer maxTokens;

    /**
     * 供应商名称
     */
    private String providerName;

    /**
     * 模型是否已配置（用于模型健康检测时提示用户）
     */
    private Boolean alreadyConfigured;

    /**
     * 模型是否新增到用户配置（检测成功后自动添加）
     */
    private Boolean newlyAdded;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 尝试过的模型列表（用于记录降级过程）
     */
    private List<String> attemptedModels;

    /**
     * 是否通过降级成功（使用了备选模型）
     */
    private Boolean fallbackUsed;

    /**
     * 创建成功结果
     */
    public static HealthCheckResult success(String message) {
        HealthCheckResult result = new HealthCheckResult();
        result.setHealthy(true);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建成功结果（带响应时间）
     */
    public static HealthCheckResult success(String message, Long responseTime) {
        HealthCheckResult result = new HealthCheckResult();
        result.setHealthy(true);
        result.setMessage(message);
        result.setResponseTime(responseTime);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static HealthCheckResult failure(String message, String error) {
        HealthCheckResult result = new HealthCheckResult();
        result.setHealthy(false);
        result.setMessage(message);
        result.setError(error);
        return result;
    }

    /**
     * 创建完整的成功结果（带模型信息）
     */
    public static HealthCheckResult success(String providerName, String testModelName,
                                            Integer maxTokens, Long responseTime) {
        HealthCheckResult result = new HealthCheckResult();
        result.setHealthy(true);
        result.setMessage(providerName + " 连接正常");
        result.setProviderName(providerName);
        result.setTestModelName(testModelName);
        result.setMaxTokens(maxTokens);
        result.setResponseTime(responseTime);
        return result;
    }

    /**
     * 创建失败结果（带供应商信息）
     */
    public static HealthCheckResult failure(String providerName, String message, String error) {
        HealthCheckResult result = new HealthCheckResult();
        result.setHealthy(false);
        result.setProviderName(providerName);
        result.setMessage(message);
        result.setError(error);
        return result;
    }
}
