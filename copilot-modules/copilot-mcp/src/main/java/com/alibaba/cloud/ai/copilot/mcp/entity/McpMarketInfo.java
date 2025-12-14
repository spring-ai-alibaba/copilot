package com.alibaba.cloud.ai.copilot.mcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 市场实体类
 *
 * @author Administrator
 */
@TableName("mcp_market_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMarketInfo {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 市场名称
     */
    @TableField("name")
    private String name;

    /**
     * 市场URL
     */
    @TableField("url")
    private String url;

    /**
     * 市场描述
     */
    @TableField("description")
    private String description;

    /**
     * 认证配置（JSON格式）
     */
    @TableField("auth_config")
    private String authConfig;

    /**
     * 状态：ENABLED-启用, DISABLED-禁用
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

