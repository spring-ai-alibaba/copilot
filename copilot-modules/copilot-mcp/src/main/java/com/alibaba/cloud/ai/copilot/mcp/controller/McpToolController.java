package com.alibaba.cloud.ai.copilot.mcp.controller;


import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolCallbackService;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolInfoService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具管理控制器（REST API）
 *
 * @author Administrator
 */
@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    @Resource
    private McpToolInfoService mcpToolInfoService;

    @Resource
    private McpToolCallbackService mcpToolCallbackService;

    /**
     * 获取工具列表
     */
    @GetMapping("/servers")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<McpToolInfo> tools;
        if (keyword != null && !keyword.isEmpty()) {
            tools = mcpToolInfoService.searchByName(keyword);
        } else if (type != null && !type.isEmpty()) {
            tools = mcpToolInfoService.listByType(type);
        } else if (status != null && !status.isEmpty()) {
            tools = mcpToolInfoService.listByStatus(status);
        } else {
            tools = mcpToolInfoService.listAll();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", tools);
        result.put("total", tools.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 根据ID获取工具
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        McpToolInfo tool = mcpToolInfoService.getById(id);
        Map<String, Object> result = new HashMap<>();
        if (tool == null) {
            result.put("success", false);
            result.put("message", "工具不存在");
            return ResponseEntity.notFound().build();
        }
        result.put("success", true);
        result.put("data", tool);
        return ResponseEntity.ok(result);
    }

    /**
     * 保存或更新工具
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody McpToolInfo tool) {
        Map<String, Object> result = new HashMap<>();
        try {
            McpToolInfo savedTool = mcpToolInfoService.saveOrUpdateInfo(tool);
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("data", savedTool);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新工具
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody McpToolInfo tool) {
        Map<String, Object> result = new HashMap<>();
        try {
            tool.setId(id);
            McpToolInfo savedTool = mcpToolInfoService.saveOrUpdateInfo(tool);
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("data", savedTool);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 删除工具
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpToolInfoService.deleteById(id);
            if (success) {
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "删除失败或工具不存在");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 批量删除工具
     */
    @DeleteMapping("/batch")
    public ResponseEntity<Map<String, Object>> deleteBatch(@RequestParam String ids) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            boolean success = mcpToolInfoService.deleteBatch(idList);
            if (success) {
                result.put("success", true);
                result.put("message", "批量删除成功");
            } else {
                result.put("success", false);
                result.put("message", "批量删除失败");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新工具状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpToolInfoService.updateStatus(id, status);
            if (success) {
                result.put("success", true);
                result.put("message", "状态更新成功");
            } else {
                result.put("success", false);
                result.put("message", "状态更新失败");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "状态更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 调用 MCP 工具
     * 供大模型 Function Calling 使用
     */
    @PostMapping("/{id}/invoke")
    public ResponseEntity<Map<String, Object>> invokeTool(
            @PathVariable Long id,
            @RequestBody Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        try {
            McpToolInfo tool = mcpToolInfoService.getById(id);
            if (tool == null) {
                result.put("success", false);
                result.put("error", "工具不存在: " + id);
                return ResponseEntity.badRequest().body(result);
            }

            if (!"ENABLED".equals(tool.getStatus())) {
                result.put("success", false);
                result.put("error", "工具未启用: " + tool.getName());
                return ResponseEntity.badRequest().body(result);
            }

            // 执行工具调用 - 使用 McpToolCallbackService
            String toolResult = mcpToolCallbackService.invokeTool(id, parameters);

            result.put("success", true);
            result.put("toolId", id);
            result.put("toolName", tool.getName());
            result.put("result", toolResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "工具调用失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 根据工具名称调用 MCP 工具
     */
    @PostMapping("/invoke/name/{name}")
    public ResponseEntity<Map<String, Object>> invokeToolByName(
            @PathVariable String name,
            @RequestBody Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<McpToolInfo> tools = mcpToolInfoService.searchByName(name);
            if (tools.isEmpty()) {
                result.put("success", false);
                result.put("error", "工具不存在: " + name);
                return ResponseEntity.badRequest().body(result);
            }

            McpToolInfo tool = tools.get(0);
            if (!"ENABLED".equals(tool.getStatus())) {
                result.put("success", false);
                result.put("error", "工具未启用: " + tool.getName());
                return ResponseEntity.badRequest().body(result);
            }

            // 执行工具调用 - 使用 McpToolCallbackService
            String toolResult = mcpToolCallbackService.invokeToolByName(tool.getName(), parameters);

            result.put("success", true);
            result.put("toolId", tool.getId());
            result.put("toolName", tool.getName());
            result.put("result", toolResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "工具调用失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }


    /**
     * 测试工具连接
     * 简单验证工具配置是否正确
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testTool(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            McpToolInfo tool = mcpToolInfoService.getById(id);
            if (tool == null) {
                result.put("success", false);
                result.put("message", "工具不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 检查工具状态
            if (!"ENABLED".equals(tool.getStatus())) {
                result.put("success", false);
                result.put("message", "工具未启用，请先启用工具");
                return ResponseEntity.ok(result);
            }

            // TODO: 根据工具类型进行实际的连接测试
            // 目前只做基本的配置检查
            if (tool.getConfigJson() == null || tool.getConfigJson().isEmpty()) {
                result.put("success", false);
                result.put("message", "工具配置为空");
                return ResponseEntity.ok(result);
            }

            result.put("success", true);
            result.put("message", "工具配置正确，可以正常使用");
            result.put("toolName", tool.getName());
            result.put("toolType", tool.getType());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
