package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM厂商实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_llm_factories")
public class LlmFactoriesEntity {
    
    /**
     * LLM厂商ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * LLM厂商名称
     */
    @TableField("name")
    private String name;
    
    /**
     * 供应商代码
     */
    @TableField("provider_code")
    private String providerCode;
    
    /**
     * 厂商logo base64字符串
     */
    @TableField("logo")
    private String logo;
    
    /**
     * 模型类型标签: LLM, Text Embedding, Image2Text, ASR
     */
    @TableField("tags")
    private String tags;
    
    /**
     * 排序权重
     */
    @TableField("sort_order")
    private Integer sortOrder;
    
    /**
     * 状态: 0=禁用, 1=启用
     */
    @TableField("status")
    private Integer status;
    
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
