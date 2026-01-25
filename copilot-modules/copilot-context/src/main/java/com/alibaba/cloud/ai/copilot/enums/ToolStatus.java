package com.alibaba.cloud.ai.copilot.enums;

import lombok.Getter;

/**
 * MCP 工具状态枚举
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Getter
public enum ToolStatus {

    /**
     * 启用状态
     */
    ENABLED("ENABLED", "启用"),

    /**
     * 禁用状态
     */
    DISABLED("DISABLED", "禁用");

    /**
     * 状态值（存储到数据库）
     */
    private final String value;

    /**
     * 状态描述
     */
    private final String description;

    ToolStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 判断是否为启用状态
     *
     * @param value 状态值
     * @return 是否启用
     */
    public static boolean isEnabled(String value) {
        return ENABLED.value.equals(value);
    }
}

