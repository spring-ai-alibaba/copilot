package com.alibaba.cloud.ai.copilot.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * API配置实体类
 * 用于存储不同AI模型的API配置信息
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("api_config")
public class ApiConfig {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，null表示全局配置
     */
    @TableField("user_id")
    private String userId;

    /**
     * 模型名称 (如: gpt-3.5-turbo, gpt-4, etc.)
     */
    @TableField("model_name")
    private String modelName;

    /**
     * API Key
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * API Base URL
     */
    @TableField("base_url")
    private String baseUrl;

    /**
     * 配置状态 (0: 禁用, 1: 启用)
     */
    @TableField("status")
    private Integer status;

    /**
     * 优先级 (数字越小优先级越高)
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 备注信息
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 删除标志 (0: 未删除, 1: 已删除)
     */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}
