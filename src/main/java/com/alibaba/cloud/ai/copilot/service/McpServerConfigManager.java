package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.McpServerConfig;
import com.alibaba.cloud.ai.copilot.model.McpServerInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP服务器配置管理器
 * 负责MCP服务器的配置、启动、停止和管理
 */
@Service
public class McpServerConfigManager {
    
    private static final Logger logger = LoggerFactory.getLogger(McpServerConfigManager.class);
    
    private final ObjectMapper objectMapper;
    private static final String MCP_CONFIG_FILE = "mcp-servers.json";
    private static final String MCP_CONFIG_DIR = "config";
    
    // 存储MCP服务器进程信息
    private final Map<String, McpServerProcess> mcpServerProcesses = new ConcurrentHashMap<>();
    
    // 存储MCP服务器配置信息
    private final Map<String, McpServerInfo> mcpServerInfos = new ConcurrentHashMap<>();
    
    public McpServerConfigManager() {
        this.objectMapper = new ObjectMapper();
        initializeConfigDirectory();
        loadExistingConfigurations();
    }
    
    /**
     * 初始化配置目录
     */
    private void initializeConfigDirectory() {
        try {
            Path configDir = Paths.get(MCP_CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("创建MCP配置目录: {}", configDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("创建MCP配置目录失败", e);
        }
    }
    
    /**
     * 加载现有配置
     */
    private void loadExistingConfigurations() {
        try {
            Path configFile = Paths.get(MCP_CONFIG_DIR, MCP_CONFIG_FILE);
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile);
                Map<String, Object> config = objectMapper.readValue(content, Map.class);
                
                if (config.containsKey("mcpServers")) {
                    Map<String, Map<String, Object>> servers = (Map<String, Map<String, Object>>) config.get("mcpServers");
                    for (Map.Entry<String, Map<String, Object>> entry : servers.entrySet()) {
                        String serverName = entry.getKey();
                        Map<String, Object> serverConfig = entry.getValue();
                        
                        McpServerConfig mcpConfig = objectMapper.convertValue(serverConfig, McpServerConfig.class);
                        McpServerInfo serverInfo = new McpServerInfo(serverName, mcpConfig);
                        mcpServerInfos.put(serverName, serverInfo);
                        
                        logger.info("加载MCP服务器配置: {}", serverName);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("加载MCP配置文件失败", e);
        }
    }
    
    /**
     * 添加MCP服务器配置
     */
    public void addMcpServer(String serverName, McpServerConfig config) {
        try {
            logger.info("添加MCP服务器: {} 配置: {}", serverName, config);
            
            // 1. 保存配置到内存
            McpServerInfo serverInfo = new McpServerInfo(serverName, config);
            mcpServerInfos.put(serverName, serverInfo);
            
            // 2. 保存配置到文件
            saveMcpServerConfig(serverName, config);
            
            // 3. 启动MCP服务器进程
            startMcpServer(serverName, config);
            
            logger.info("成功添加MCP服务器: {}", serverName);
            
        } catch (Exception e) {
            logger.error("添加MCP服务器失败: " + serverName, e);
            // 清理失败的配置
            mcpServerInfos.remove(serverName);
            throw new RuntimeException("Failed to add MCP server: " + e.getMessage(), e);
        }
    }
    
    /**
     * 保存MCP服务器配置到文件
     */
    private void saveMcpServerConfig(String serverName, McpServerConfig config) throws IOException {
        Path configFile = Paths.get(MCP_CONFIG_DIR, MCP_CONFIG_FILE);
        
        Map<String, Object> allConfig;
        if (Files.exists(configFile)) {
            String content = Files.readString(configFile);
            allConfig = objectMapper.readValue(content, Map.class);
        } else {
            allConfig = new HashMap<>();
        }
        
        // 确保mcpServers节点存在
        if (!allConfig.containsKey("mcpServers")) {
            allConfig.put("mcpServers", new HashMap<>());
        }
        
        Map<String, Object> servers = (Map<String, Object>) allConfig.get("mcpServers");
        servers.put(serverName, config);
        
        // 写入文件
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allConfig);
        Files.writeString(configFile, jsonContent);
        
        logger.info("保存MCP服务器配置到文件: {}", serverName);
    }
    
    /**
     * 启动MCP服务器进程
     */
    private void startMcpServer(String serverName, McpServerConfig config) {
        try {
            logger.info("启动MCP服务器进程: {}", serverName);
            
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // 构建命令
            List<String> command = new ArrayList<>();
            command.add(config.getCommand());
            if (config.getArgs() != null) {
                command.addAll(config.getArgs());
            }
            
            processBuilder.command(command);
            
            // 设置工作目录
            if (config.getWorkingDirectory() != null) {
                processBuilder.directory(new File(config.getWorkingDirectory()));
            } else {
                processBuilder.directory(new File(System.getProperty("user.dir")));
            }
            
            // 设置环境变量
            if (config.getEnv() != null) {
                processBuilder.environment().putAll(config.getEnv());
            }
            
            // 启动进程
            Process process = processBuilder.start();
            
            // 存储进程引用
            McpServerProcess serverProcess = new McpServerProcess(serverName, process, config);
            mcpServerProcesses.put(serverName, serverProcess);
            
            // 更新服务器状态
            McpServerInfo serverInfo = mcpServerInfos.get(serverName);
            if (serverInfo != null) {
                serverInfo.setStatus("RUNNING");
                serverInfo.setStartTime(LocalDateTime.now());
            }
            
            logger.info("成功启动MCP服务器: {} 命令: {}", serverName, command);
            
            // TODO: 连接到MCP服务器并注册工具
            // connectAndRegisterTools(serverName, config);
            
        } catch (IOException e) {
            logger.error("启动MCP服务器进程失败: " + serverName, e);
            
            // 更新服务器状态为错误
            McpServerInfo serverInfo = mcpServerInfos.get(serverName);
            if (serverInfo != null) {
                serverInfo.setStatus("ERROR");
                serverInfo.setErrorMessage(e.getMessage());
            }
            
            throw new RuntimeException("Failed to start MCP server process: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取所有MCP服务器信息
     */
    public List<McpServerInfo> getAllMcpServers() {
        return new ArrayList<>(mcpServerInfos.values());
    }
    
    /**
     * 移除MCP服务器
     */
    public void removeMcpServer(String serverName) {
        try {
            logger.info("移除MCP服务器: {}", serverName);
            
            // 1. 停止进程
            McpServerProcess serverProcess = mcpServerProcesses.get(serverName);
            if (serverProcess != null) {
                serverProcess.getProcess().destroyForcibly();
                mcpServerProcesses.remove(serverName);
            }
            
            // 2. 从内存中移除
            mcpServerInfos.remove(serverName);
            
            // 3. 从配置文件中移除
            removeMcpServerFromConfig(serverName);
            
            logger.info("成功移除MCP服务器: {}", serverName);
            
        } catch (Exception e) {
            logger.error("移除MCP服务器失败: " + serverName, e);
            throw new RuntimeException("Failed to remove MCP server: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从配置文件中移除MCP服务器
     */
    private void removeMcpServerFromConfig(String serverName) throws IOException {
        Path configFile = Paths.get(MCP_CONFIG_DIR, MCP_CONFIG_FILE);
        
        if (Files.exists(configFile)) {
            String content = Files.readString(configFile);
            Map<String, Object> allConfig = objectMapper.readValue(content, Map.class);
            
            if (allConfig.containsKey("mcpServers")) {
                Map<String, Object> servers = (Map<String, Object>) allConfig.get("mcpServers");
                servers.remove(serverName);
                
                // 写回文件
                String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allConfig);
                Files.writeString(configFile, jsonContent);
            }
        }
    }
    
    /**
     * MCP服务器进程信息
     */
    public static class McpServerProcess {
        private final String name;
        private final Process process;
        private final McpServerConfig config;
        private final LocalDateTime startTime;
        
        public McpServerProcess(String name, Process process, McpServerConfig config) {
            this.name = name;
            this.process = process;
            this.config = config;
            this.startTime = LocalDateTime.now();
        }
        
        // Getters
        public String getName() { return name; }
        public Process getProcess() { return process; }
        public McpServerConfig getConfig() { return config; }
        public LocalDateTime getStartTime() { return startTime; }
    }
}
