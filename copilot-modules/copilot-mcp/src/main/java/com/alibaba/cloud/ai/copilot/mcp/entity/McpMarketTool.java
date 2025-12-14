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
 * MCP 市场工具关联实体类
 *
 * @author Administrator
 */
@TableName("mcp_market_tool")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMarketTool {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 市场ID
     */
    @TableField("market_id")
    private Long marketId;

    /**
     * 工具名称
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 工具描述
     */
    @TableField("tool_description")
    private String toolDescription;

    /**
     * 工具版本
     */
    @TableField("tool_version")
    private String toolVersion;

    /**
     * 工具元数据（JSON格式）
     */
    @TableField("tool_metadata")
    private String toolMetadata;

    /**
     * 是否已加载到本地：0-未加载, 1-已加载
     */
    @TableField("is_loaded")
    private Boolean isLoaded;

    /**
     * 关联的本地工具ID
     */
    @TableField("local_tool_id")
    private Long localToolId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}

