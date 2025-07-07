package com.alibaba.cloud.ai.copilot.model;

import java.util.List;
import java.util.Map;

/**
 * MCP服务器配置
 */
public class McpServerConfig {
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private String workingDirectory;

    public McpServerConfig() {}

    public McpServerConfig(String command, List<String> args) {
        this.command = command;
        this.args = args;
    }

    // Getters and Setters
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String toString() {
        return "McpServerConfig{" +
                "command='" + command + '\'' +
                ", args=" + args +
                ", env=" + env +
                ", workingDirectory='" + workingDirectory + '\'' +
                '}';
    }
}
