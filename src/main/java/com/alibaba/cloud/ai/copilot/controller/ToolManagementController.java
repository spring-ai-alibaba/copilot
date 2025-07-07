package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import com.alibaba.cloud.ai.copilot.service.ToolDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具管理控制器
 * 提供工具发现、查询和管理的API接口
 */
@RestController
@RequestMapping("/api/tools")
public class ToolManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolManagementController.class);
    
    private final ToolDiscoveryService toolDiscoveryService;
    
    public ToolManagementController(ToolDiscoveryService toolDiscoveryService) {
        this.toolDiscoveryService = toolDiscoveryService;
    }
    
    /**
     * 获取所有工具
     */
    @GetMapping("/all")
    public ResponseEntity<List<ToolInfo>> getAllTools() {
        try {
            List<ToolInfo> tools = toolDiscoveryService.getAllTools();
            logger.info("返回 {} 个工具信息", tools.size());
            return ResponseEntity.ok(tools);
        } catch (Exception e) {
            logger.error("获取所有工具时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取系统工具
     */
    @GetMapping("/system")
    public ResponseEntity<List<ToolInfo>> getSystemTools() {
        try {
            List<ToolInfo> systemTools = toolDiscoveryService.getSystemTools();
            logger.info("返回 {} 个系统工具", systemTools.size());
            return ResponseEntity.ok(systemTools);
        } catch (Exception e) {
            logger.error("获取系统工具时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取MCP工具
     */
    @GetMapping("/mcp")
    public ResponseEntity<List<ToolInfo>> getMcpTools() {
        try {
            List<ToolInfo> mcpTools = toolDiscoveryService.getMcpTools();
            logger.info("返回 {} 个MCP工具", mcpTools.size());
            return ResponseEntity.ok(mcpTools);
        } catch (Exception e) {
            logger.error("获取MCP工具时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取工具统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getToolStats() {
        try {
            List<ToolInfo> allTools = toolDiscoveryService.getAllTools();
            
            long systemToolCount = allTools.stream()
                .filter(tool -> "SYSTEM".equals(tool.getType()))
                .count();
            
            long mcpToolCount = allTools.stream()
                .filter(tool -> "MCP".equals(tool.getType()))
                .count();
            
            long enabledToolCount = allTools.stream()
                .filter(ToolInfo::isEnabled)
                .count();
            
            Map<String, Object> stats = Map.of(
                "totalTools", allTools.size(),
                "systemTools", systemToolCount,
                "mcpTools", mcpToolCount,
                "enabledTools", enabledToolCount,
                "disabledTools", allTools.size() - enabledToolCount
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取工具统计信息时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据名称获取工具详情
     */
    @GetMapping("/{toolName}")
    public ResponseEntity<ToolInfo> getToolByName(@PathVariable String toolName) {
        try {
            List<ToolInfo> allTools = toolDiscoveryService.getAllTools();
            ToolInfo tool = allTools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);
            
            if (tool != null) {
                return ResponseEntity.ok(tool);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("获取工具详情时发生错误: {}", toolName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 搜索工具
     */
    @GetMapping("/search")
    public ResponseEntity<List<ToolInfo>> searchTools(@RequestParam String query) {
        try {
            List<ToolInfo> allTools = toolDiscoveryService.getAllTools();
            List<ToolInfo> matchedTools = allTools.stream()
                .filter(tool -> 
                    tool.getName().toLowerCase().contains(query.toLowerCase()) ||
                    tool.getDisplayName().toLowerCase().contains(query.toLowerCase()) ||
                    tool.getDescription().toLowerCase().contains(query.toLowerCase())
                )
                .collect(Collectors.toList());
            
            logger.info("搜索 '{}' 找到 {} 个工具", query, matchedTools.size());
            return ResponseEntity.ok(matchedTools);
        } catch (Exception e) {
            logger.error("搜索工具时发生错误: {}", query, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
