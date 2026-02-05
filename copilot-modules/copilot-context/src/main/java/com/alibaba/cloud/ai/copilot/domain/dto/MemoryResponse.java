package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 记忆响应
 *
 * @author better
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryResponse {
    /**
     * 操作结果消息
     */
    private String message;

    /**
     * 记忆值或搜索结果
     */
    private Object value;

    /**
     * 搜索结果列表（用于search接口）
     */
    private List<Map<String, Object>> items;
}
