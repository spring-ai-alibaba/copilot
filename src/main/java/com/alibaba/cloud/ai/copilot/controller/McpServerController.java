package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.model.McpServerInfo;
import com.alibaba.cloud.ai.copilot.model.McpServerRequest;
import com.alibaba.cloud.ai.copilot.service.McpServerConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP服务器管理控制器
 * 提供MCP服务器的添加、删除、查询等API接口
 */
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {
    
    private static final Logger logger = LoggerFactory.getLogger(McpServerController.class);
    
    private final McpServerConfigManager mcpServerConfigManager;
    
    public McpServerController(McpServerConfigManager mcpServerConfigManager) {
        this.mcpServerConfigManager = mcpServerConfigManager;
    }
    
    /**
     * 添加MCP服务器
     */
    @PostMapping("/servers")
    public ResponseEntity<Map<String, String>> addMcpServer(@RequestBody McpServerRequest request) {
        try {
            logger.info("收到添加MCP服务器请求: {}", request.getName());
            
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "服务器名称不能为空"));
            }
            
            if (request.getConfig() == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "服务器配置不能为空"));
            }
            
            if (request.getConfig().getCommand() == null || request.getConfig().getCommand().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "服务器命令不能为空"));
            }
            
            mcpServerConfigManager.addMcpServer(request.getName(), request.getConfig());
            
            return ResponseEntity.ok(Map.of(
                "message", "MCP服务器添加成功",
                "serverName", request.getName()
            ));
            
        } catch (Exception e) {
            logger.error("添加MCP服务器失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "添加MCP服务器失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有MCP服务器
     */
    @GetMapping("/servers")
    public ResponseEntity<List<McpServerInfo>> getMcpServers() {
        try {
            List<McpServerInfo> servers = mcpServerConfigManager.getAllMcpServers();
            logger.info("返回 {} 个MCP服务器信息", servers.size());
            return ResponseEntity.ok(servers);
        } catch (Exception e) {
            logger.error("获取MCP服务器列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除MCP服务器
     */
    @DeleteMapping("/servers/{serverName}")
    public ResponseEntity<Map<String, String>> removeMcpServer(@PathVariable String serverName) {
        try {
            logger.info("收到删除MCP服务器请求: {}", serverName);
            
            mcpServerConfigManager.removeMcpServer(serverName);
            
            return ResponseEntity.ok(Map.of(
                "message", "MCP服务器删除成功",
                "serverName", serverName
            ));
            
        } catch (Exception e) {
            logger.error("删除MCP服务器失败: {}", serverName, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "删除MCP服务器失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取MCP服务器状态
     */
    @GetMapping("/servers/{serverName}/status")
    public ResponseEntity<Map<String, Object>> getMcpServerStatus(@PathVariable String serverName) {
        try {
            List<McpServerInfo> servers = mcpServerConfigManager.getAllMcpServers();
            McpServerInfo serverInfo = servers.stream()
                .filter(s -> s.getName().equals(serverName))
                .findFirst()
                .orElse(null);
            
            if (serverInfo == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> status = Map.of(
                "name", serverInfo.getName(),
                "status", serverInfo.getStatus(),
                "startTime", serverInfo.getStartTime(),
                "toolCount", serverInfo.getToolCount(),
                "errorMessage", serverInfo.getErrorMessage() != null ? serverInfo.getErrorMessage() : ""
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("获取MCP服务器状态失败: {}", serverName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 重启MCP服务器
     */
    @PostMapping("/servers/{serverName}/restart")
    public ResponseEntity<Map<String, String>> restartMcpServer(@PathVariable String serverName) {
        try {
            logger.info("收到重启MCP服务器请求: {}", serverName);
            
            // 获取服务器配置
            List<McpServerInfo> servers = mcpServerConfigManager.getAllMcpServers();
            McpServerInfo serverInfo = servers.stream()
                .filter(s -> s.getName().equals(serverName))
                .findFirst()
                .orElse(null);
            
            if (serverInfo == null) {
                return ResponseEntity.status(404)
                    .body(Map.of("error", "MCP服务器不存在: " + serverName));
            }
            
            // 先删除再添加（重启）
            mcpServerConfigManager.removeMcpServer(serverName);
            mcpServerConfigManager.addMcpServer(serverName, serverInfo.getConfig());
            
            return ResponseEntity.ok(Map.of(
                "message", "MCP服务器重启成功",
                "serverName", serverName
            ));
            
        } catch (Exception e) {
            logger.error("重启MCP服务器失败: {}", serverName, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "重启MCP服务器失败: " + e.getMessage()));
        }
    }
}
