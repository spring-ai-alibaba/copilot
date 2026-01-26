package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 市场工具关联实体
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@TableName("mcp_market_tool")
public class McpMarketTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 市场 ID
     */
    private Long marketId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String toolDescription;

    /**
     * 工具版本
     */
    private String toolVersion;

    /**
     * 工具元数据（JSON格式）
     */
    private String toolMetadata;

    /**
     * 是否已加载到本地
     */
    private Boolean isLoaded;

    /**
     * 关联的本地工具 ID
     */
    private Long localToolId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

