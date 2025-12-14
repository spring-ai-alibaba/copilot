package com.alibaba.cloud.ai.copilot.mcp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpToolTypeEnum {

    LOCAL("LOCAL"),

    REMOTE("REMOTE"),
    ;

    private final String value;
}
