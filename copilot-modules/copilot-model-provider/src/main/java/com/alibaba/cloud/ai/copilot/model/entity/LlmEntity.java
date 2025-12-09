package com.alibaba.cloud.ai.copilot.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM模型实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm")
public class LlmEntity {

    /**
     * LLM模型ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 厂商ID
     */
    @TableField("fid")
    private String fid;

    /**
     * 模型名称
     */
    @TableField("llm_name")
    private String llmName;

    /**
     * 模型类型: LLM, Text Embedding, Image2Text, ASR
     */
    @TableField("model_type")
    private String modelType;

    /**
     * 最大token数
     */
    @TableField("max_tokens")
    private Integer maxTokens;

    /**
     * 功能标签: LLM, Text Embedding, Image2Text, Chat, 32k...
     */
    @TableField("tags")
    private String tags;

    /**
     * 是否支持工具调用
     */
    @TableField("is_tools")
    private Boolean isTools;

    /**
     * 状态: 0=禁用, 1=启用
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
