package com.alibaba.cloud.ai.copilot.enums;

import lombok.Getter;

/**
 * Tool type enumeration
 * Defines all available tools and their properties
 */
@Getter
public enum ToolType {
    EDIT_FILE("edit_file", "edit-progress", "edit"),
    WRITE_FILE("write_file", "edit-progress", "add"),
    READ_FILE("read_file", "read-progress", "read"),
    LIST_DIRECTORY("list_directory", "list-progress", "list");

    private final String toolName;
    private final String eventName;
    private final String dataType;

    ToolType(String toolName, String eventName, String dataType) {
        this.toolName = toolName;
        this.eventName = eventName;
        this.dataType = dataType;
    }

    /**
     * Get ToolType by tool name
     *
     * @param toolName the tool name to search for
     * @return ToolType if found, null otherwise
     */
    public static ToolType fromToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        for (ToolType type : ToolType.values()) {
            if (type.toolName.equals(toolName)) {
                return type;
            }
        }
        return null;
    }
}