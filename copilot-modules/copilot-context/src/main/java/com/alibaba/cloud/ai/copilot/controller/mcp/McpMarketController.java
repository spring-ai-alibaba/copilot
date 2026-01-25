package com.alibaba.cloud.ai.copilot.controller.mcp;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketToolListResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpMarketInfo;
import com.alibaba.cloud.ai.copilot.service.McpMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 市场管理 Controller
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/markets")
@RequiredArgsConstructor
public class McpMarketController {

    private final McpMarketService mcpMarketService;

    /**
     * 查询市场列表
     */
    @GetMapping
    public McpMarketListResult list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return mcpMarketService.listMarkets(keyword, status);
    }

    /**
     * 新增市场
     */
    @PostMapping
    public R<McpMarketInfo> save(@RequestBody McpMarketInfo market) {
        return R.ok(mcpMarketService.saveMarket(market));
    }

    /**
     * 更新市场
     */
    @PutMapping("/{id}")
    public R<McpMarketInfo> update(@PathVariable Long id, @RequestBody McpMarketInfo market) {
        market.setId(id);
        return R.ok(mcpMarketService.updateMarket(market));
    }

    /**
     * 删除市场
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mcpMarketService.deleteMarket(id);
        return R.ok();
    }

    /**
     * 更新市场状态
     */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        mcpMarketService.updateMarketStatus(id, status);
        return R.ok();
    }

    /**
     * 获取市场工具列表（分页）
     */
    @GetMapping("/{marketId}/tools")
    public McpMarketToolListResult getMarketTools(
            @PathVariable Long marketId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return mcpMarketService.getMarketTools(marketId, page, size);
    }

    /**
     * 刷新市场工具列表
     */
    @PostMapping("/{marketId}/refresh")
    public R<Void> refreshMarketTools(@PathVariable Long marketId) {
        mcpMarketService.refreshMarketTools(marketId);
        return R.ok();
    }

    /**
     * 加载单个工具到本地
     */
    @PostMapping("/tools/{toolId}/load")
    public R<Void> loadToolToLocal(@PathVariable Long toolId) {
        mcpMarketService.loadToolToLocal(toolId);
        return R.ok();
    }

    /**
     * 批量加载工具到本地
     */
    @PostMapping("/tools/batch-load")
    public R<Map<String, Object>> batchLoadTools(@RequestBody Map<String, List<Long>> request) {
        List<Long> toolIds = request.get("toolIds");
        int successCount = mcpMarketService.batchLoadTools(toolIds);
        return R.ok(Map.of("successCount", successCount));
    }
}

