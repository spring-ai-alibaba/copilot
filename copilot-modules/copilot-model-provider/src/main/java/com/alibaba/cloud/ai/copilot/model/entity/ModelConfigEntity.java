package com.alibaba.cloud.ai.copilot.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 用户 id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 可见性/权限: PUBLIC(公开), ORGANIZATION(组织), PRIVATE(个人)
     * 默认为 PUBLIC
     */
    @TableField("visibility")
    private String visibility = "PUBLIC";

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
     * 最大 token
     */
    @TableField("max_token")
    private Integer maxToken;

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
     * 模型类型: LLM, Text Embedding, Image2Text, ASR
     */
    @TableField("model_type")
    private String modelType;

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
