package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 搜索记忆请求
 *
 * @author better
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMemoryRequest {
    /**
     * 命名空间
     */
    private List<String> namespace;

    /**
     * 搜索过滤器（可选）
     */
    private Map<String, Object> filter;
}
