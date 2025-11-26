package com.alibaba.cloud.ai.copilot.dto;

import java.util.List;
import java.util.Map;

/**
 * 通用大模型服务注册记录（支持 SILICONFLOW、OpenAI、Anthropic 等）
 */
public record LlmServiceProvider(
        String providerId,                     // 如 "siliconflow", "openai", "custom"
        List<LlmModel> models,
        String metadata          // 可选元数据，如 tags、region、version 等
) {
    /**
     * 通用模型描述（适用于任何供应商）
     */
    public record LlmModel(
            String id,                         // 模型唯一标识，如 "gpt-4o", "Qwen/Qwen3-72B"
            String name,                       // 可读名称（可选）
            String type,                    // 模型能力类型
            int maxTokens,                     // 最大上下文长度
            String status,                     // "active", "deprecated" 等
            Map<String, Object> extra          // 扩展字段：temperature_range, vision_support, pricing 等
    ) {}
}