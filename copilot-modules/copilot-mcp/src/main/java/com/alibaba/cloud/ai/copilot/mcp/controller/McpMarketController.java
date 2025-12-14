package com.alibaba.cloud.ai.copilot.mcp.controller;

import com.alibaba.cloud.ai.copilot.mcp.entity.McpMarketInfo;
import com.alibaba.cloud.ai.copilot.mcp.service.McpMarketInfoService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 市场管理控制器（REST API）
 *
 * @author Administrator
 */
@RestController
@RequestMapping("/api/mcp/markets")
public class McpMarketController {

    @Resource
    private McpMarketInfoService mcpMarketInfoService;

    /**
     * 获取市场列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<McpMarketInfo> markets = keyword != null && !keyword.isEmpty()
                ? mcpMarketInfoService.searchByName(keyword)
                : status != null && !status.isEmpty()
                ? mcpMarketInfoService.listByStatus(status)
                : mcpMarketInfoService.listAll();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", markets);
        result.put("total", markets.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 根据ID获取市场
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        McpMarketInfo market = mcpMarketInfoService.getById(id);
        Map<String, Object> result = new HashMap<>();
        if (market == null) {
            result.put("success", false);
            result.put("message", "市场不存在");
            return ResponseEntity.notFound().build();
        }
        result.put("success", true);
        result.put("data", market);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取市场工具列表（支持分页）
     */
    @GetMapping("/{id}/tools")
    public ResponseEntity<Map<String, Object>> getMarketTools(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Map<String, Object> result = mcpMarketInfoService.getMarketToolsWithPage(id, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 保存或更新市场
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody McpMarketInfo market) {
        Map<String, Object> result = new HashMap<>();
        try {
            McpMarketInfo savedMarket = mcpMarketInfoService.saveOrUpdateInfo(market);
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("data", savedMarket);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新市场
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody McpMarketInfo market) {
        Map<String, Object> result = new HashMap<>();
        try {
            market.setId(id);
            McpMarketInfo savedMarket = mcpMarketInfoService.saveOrUpdateInfo(market);
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("data", savedMarket);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 删除市场
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpMarketInfoService.deleteById(id);
            if (success) {
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "删除失败或市场不存在");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 更新市场状态
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpMarketInfoService.updateStatus(id, status);
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
     * 刷新市场工具列表
     */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<Map<String, Object>> refreshTools(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpMarketInfoService.refreshMarketTools(id);
            if (success) {
                result.put("success", true);
                result.put("message", "刷新成功");
            } else {
                result.put("success", false);
                result.put("message", "刷新失败，请检查市场URL和网络连接");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 加载市场工具到本地
     */
    @PostMapping("/tools/{toolId}/load")
    public ResponseEntity<Map<String, Object>> loadTool(@PathVariable Long toolId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = mcpMarketInfoService.loadToolToLocal(toolId);
            if (success) {
                result.put("success", true);
                result.put("message", "工具加载成功");
            } else {
                result.put("success", false);
                result.put("message", "工具加载失败");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "工具加载失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 批量加载市场工具到本地
     */
    @PostMapping("/tools/batch-load")
    public ResponseEntity<Map<String, Object>> batchLoadTools(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<Long> toolIds = (List<Long>) request.get("toolIds");
            if (toolIds == null || toolIds.isEmpty()) {
                result.put("success", false);
                result.put("message", "请选择要加载的工具");
                return ResponseEntity.badRequest().body(result);
            }

            int successCount = mcpMarketInfoService.batchLoadToolsToLocal(toolIds);
            if (successCount > 0) {
                result.put("success", true);
                result.put("message", String.format("成功加载 %d 个工具", successCount));
                result.put("successCount", successCount);
            } else {
                result.put("success", false);
                result.put("message", "批量加载失败，请检查工具状态");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量加载失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
