package com.alibaba.cloud.ai.copilot.domain.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发现的模型信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredModelInfo {
    
    /**
     * 模型ID
     */
    private String llmName;

    /**
     * 模型类型: chat, embedding, rerank, image
     */
    private String modelType;

    /**
     * 最大token数
     */
    private Integer maxTokens;

    /**
     * 功能标签
     */
    private String tags;
    
    /**
     * 支持的模态
     */
    private List<String> supportedModalities;
    
    /**
     * 是否支持工具调用
     */
    private Boolean isTools;

    /**
     * 状态
     */
    private String status;
}
