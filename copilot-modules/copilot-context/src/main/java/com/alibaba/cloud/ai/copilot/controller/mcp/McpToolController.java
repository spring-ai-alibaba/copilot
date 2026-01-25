package com.alibaba.cloud.ai.copilot.controller.mcp;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 工具管理 Controller
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpToolController {

    private final McpToolService mcpToolService;

    /**
     * 查询 MCP 工具列表
     */
    @GetMapping("/servers")
    public McpToolListResult list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return mcpToolService.listTools(keyword, type, status);
    }

    /**
     * 新增 MCP 工具
     */
    @PostMapping
    public R<McpToolInfo> save(@RequestBody McpToolInfo tool) {
        return R.ok(mcpToolService.saveTool(tool));
    }

    /**
     * 更新 MCP 工具
     */
    @PutMapping("/{id}")
    public R<McpToolInfo> update(@PathVariable Long id, @RequestBody McpToolInfo tool) {
        tool.setId(id);
        return R.ok(mcpToolService.updateTool(tool));
    }

    /**
     * 删除 MCP 工具
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mcpToolService.deleteTool(id);
        return R.ok();
    }

    /**
     * 批量删除 MCP 工具
     */
    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
        mcpToolService.batchDeleteTools(idList);
        return R.ok();
    }

    /**
     * 更新工具状态
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        mcpToolService.updateToolStatus(id, status);
        return R.ok();
    }

    /**
     * 测试工具连接
     */
    @PostMapping("/{id}/test")
    public McpToolTestResult testTool(@PathVariable Long id) {
        return mcpToolService.testTool(id);
    }
}

