package com.alibaba.cloud.ai.copilot.model;

import java.time.LocalDateTime;

/**
 * MCP服务器信息
 */
public class McpServerInfo {
    private String name;
    private McpServerConfig config;
    private String status; // RUNNING, STOPPED, ERROR
    private LocalDateTime startTime;
    private String errorMessage;
    private int toolCount;

    public McpServerInfo() {}

    public McpServerInfo(String name, McpServerConfig config) {
        this.name = name;
        this.config = config;
        this.status = "STOPPED";
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public McpServerConfig getConfig() {
        return config;
    }

    public void setConfig(McpServerConfig config) {
        this.config = config;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    @Override
    public String toString() {
        return "McpServerInfo{" +
                "name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", toolCount=" + toolCount +
                '}';
    }
}
