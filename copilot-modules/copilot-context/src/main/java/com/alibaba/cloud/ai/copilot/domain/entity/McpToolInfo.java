package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具信息实体
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@TableName("mcp_tool_info")
public class McpToolInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具类型：LOCAL-本地, REMOTE-远程
     */
    private String type;

    /**
     * 状态：ENABLED-启用, DISABLED-禁用
     */
    private String status;

    /**
     * 配置信息（JSON格式）
     * LOCAL: {"command": "npx", "args": ["-y", "@example/mcp-server"], "env": {...}}
     * REMOTE: {"baseUrl": "http://localhost:8080/mcp"}
     */
    private String configJson;

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

