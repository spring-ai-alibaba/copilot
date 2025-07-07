package com.alibaba.cloud.ai.copilot.model;

/**
 * MCP服务器添加请求
 */
public class McpServerRequest {
    private String name;
    private McpServerConfig config;

    public McpServerRequest() {}

    public McpServerRequest(String name, McpServerConfig config) {
        this.name = name;
        this.config = config;
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

    @Override
    public String toString() {
        return "McpServerRequest{" +
                "name='" + name + '\'' +
                ", config=" + config +
                '}';
    }
}
