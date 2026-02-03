package com.alibaba.cloud.ai.copilot.domain.dto;

import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 工具列表返回结果
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolListResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 工具列表
     */
    private List<McpToolInfo> data;

    /**
     * 总数
     */
    private int total;

    public static McpToolListResult of(List<McpToolInfo> data) {
        return McpToolListResult.builder()
                .success(true)
                .data(data)
                .total(data != null ? data.size() : 0)
                .build();
    }
}

