package com.alibaba.cloud.ai.copilot.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户偏好信息
 *
 * @author better
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceInfo {
    /**
     * 偏好类别
     */
    private String category;

    /**
     * 偏好值
     */
    private String value;

    /**
     * 原始上下文
     */
    private String context;

    /**
     * 置信度（0.0-1.0）
     */
    private Double confidence;

    /**
     * 学习时间
     */
    private LocalDateTime learnedAt;

    /**
     * 使用次数（用于排序）
     */
    private Integer usageCount;

    /**
     * 来源：auto（自动学习）、manual（手动添加）、agent（Agent主动学习）
     */
    private String source;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 从 Map 转换为 PreferenceInfo
     */
    public static PreferenceInfo fromMap(Map<String, Object> map) {
        PreferenceInfo.PreferenceInfoBuilder builder = PreferenceInfo.builder();

        if (map.get("category") != null) {
            builder.category(map.get("category").toString());
        }
        if (map.get("value") != null) {
            builder.value(map.get("value").toString());
        }
        if (map.get("context") != null) {
            builder.context(map.get("context").toString());
        }
        if (map.get("confidence") != null) {
            if (map.get("confidence") instanceof Number) {
                builder.confidence(((Number) map.get("confidence")).doubleValue());
            }
        }
        if (map.get("learnedAt") != null) {
            if (map.get("learnedAt") instanceof String) {
                builder.learnedAt(LocalDateTime.parse((String) map.get("learnedAt")));
            }
        }
        if (map.get("usageCount") != null) {
            if (map.get("usageCount") instanceof Number) {
                builder.usageCount(((Number) map.get("usageCount")).intValue());
            }
        }
        if (map.get("source") != null) {
            builder.source(map.get("source").toString());
        }
        if (map.get("enabled") != null) {
            if (map.get("enabled") instanceof Boolean) {
                builder.enabled((Boolean) map.get("enabled"));
            } else if (map.get("enabled") instanceof String) {
                builder.enabled(Boolean.parseBoolean((String) map.get("enabled")));
            }
        }

        return builder.build();
    }

    /**
     * 转换为 Map
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "category", category != null ? category : "",
                "value", value != null ? value : "",
                "context", context != null ? context : "",
                "confidence", confidence != null ? confidence : 0.0,
                "learnedAt", learnedAt != null ? learnedAt.toString() : LocalDateTime.now().toString(),
                "usageCount", usageCount != null ? usageCount : 0,
                "source", source != null ? source : "auto",
                "enabled", enabled != null ? enabled : true
        );
    }
}
