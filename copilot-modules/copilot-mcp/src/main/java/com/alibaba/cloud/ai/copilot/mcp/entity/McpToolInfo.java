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
 * MCP 工具实体类
 *
 * @author evo
 */
@TableName("mcp_tool_info")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInfo {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工具名称
     */
    @TableField("name")
    private String name;

    /**
     * 工具描述
     */
    @TableField("description")
    private String description;

    /**
     * 工具类型：LOCAL-本地, REMOTE-远程
     */
    @TableField("type")
    private String type;

    /**
     * 状态：ENABLED-启用, DISABLED-禁用
     */
    @TableField("status")
    private String status;

    /**
     * 配置信息（JSON格式）
     */
    @TableField("config_json")
    private String configJson;

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

