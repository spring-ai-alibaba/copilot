package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 保存记忆请求
 *
 * @author better
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveMemoryRequest {
    /**
     * 命名空间
     */
    private List<String> namespace;

    /**
     * 键
     */
    private String key;

    /**
     * 值
     */
    private Map<String, Object> value;
}
