package com.alibaba.cloud.ai.copilot.domain.dto;

import com.alibaba.cloud.ai.copilot.domain.entity.McpMarketInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 市场列表返回结果
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMarketListResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 市场列表
     */
    private List<McpMarketInfo> data;

    /**
     * 总数
     */
    private int total;

    public static McpMarketListResult of(List<McpMarketInfo> data) {
        return McpMarketListResult.builder()
                .success(true)
                .data(data)
                .total(data != null ? data.size() : 0)
                .build();
    }
}

