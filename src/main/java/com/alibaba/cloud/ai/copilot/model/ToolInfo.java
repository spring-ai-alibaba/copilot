package com.alibaba.cloud.ai.copilot.model;

import java.util.List;
import java.util.Map;

/**
 * 工具信息统一模型
 */
public class ToolInfo {
    private String name;
    private String displayName;
    private String description;
    private String type; // SYSTEM, MCP
    private String source; // 来源类名或MCP服务器名
    private boolean enabled;
    private boolean builtIn; // 是否为内置工具
    private List<ToolParameter> parameters;
    private Map<String, Object> metadata;

    public ToolInfo() {}

    public ToolInfo(String name, String displayName, String description) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public List<ToolParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<ToolParameter> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "ToolInfo{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type='" + type + '\'' +
                ", source='" + source + '\'' +
                ", enabled=" + enabled +
                ", builtIn=" + builtIn +
                '}';
    }
}
