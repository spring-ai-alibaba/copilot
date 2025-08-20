package com.alibaba.cloud.ai.copilot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Chat mode enumeration
 */
public enum ChatMode {
    CHAT("chat"),
    BUILDER("builder");

    private final String value;

    ChatMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ChatMode fromValue(String value) {
        if (value == null) {
            return BUILDER; // default
        }
        for (ChatMode mode : ChatMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return BUILDER; // default
    }
}
