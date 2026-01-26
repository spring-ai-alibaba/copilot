package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 市场信息实体
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@TableName("mcp_market_info")
public class McpMarketInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 市场名称
     */
    private String name;

    /**
     * 市场 URL
     */
    private String url;

    /**
     * 市场描述
     */
    private String description;

    /**
     * 认证配置（JSON格式）
     */
    private String authConfig;

    /**
     * 状态：ENABLED-启用, DISABLED-禁用
     */
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

