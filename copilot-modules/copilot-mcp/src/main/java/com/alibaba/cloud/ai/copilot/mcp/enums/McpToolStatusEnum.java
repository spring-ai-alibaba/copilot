package com.alibaba.cloud.ai.copilot.mcp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpToolStatusEnum {

    ENABLED("ENABLED", 0),

    DISABLED("DISABLED", 1),
    ;

    private final String value;

    private final int code;
}