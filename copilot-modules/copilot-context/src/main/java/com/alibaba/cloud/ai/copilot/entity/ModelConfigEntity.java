package com.alibaba.cloud.ai.copilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型配置实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_config")
public class ModelConfigEntity {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 模型名称
     */
    @TableField("model_name")
    private String modelName;
    
    /**
     * 模型标识键
     */
    @TableField("model_key")
    private String modelKey;
    
    /**
     * 是否支持图像处理
     */
    @TableField("use_image")
    private Boolean useImage;
    
    /**
     * 模型描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 图标URL
     */
    @TableField("icon_url")
    private String iconUrl;
    
    /**
     * 模型提供商
     */
    @TableField("provider")
    private String provider;
    
    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;
    
    /**
     * API地址
     */
    @TableField("api_url")
    private String apiUrl;
    
    /**
     * 是否支持函数调用
     */
    @TableField("function_call")
    private Boolean functionCall;
    
    /**
     * 是否启用
     */
    @TableField("enabled")
    private Boolean enabled;
    
    /**
     * 排序顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;
    
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
