package com.alibaba.cloud.ai.copilot.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolCallbackService;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具类，提供一些通用方法
 *
 * @author evo
 */
@Slf4j
@Component
public class ChatMcpToolUtils {
    @Resource
    private McpToolCallbackService mcpToolCallbackService;


    /**
     * 将前端传来的 ToolInfo 列表转换为 Spring AI 的 ToolCallback 列表
     * 直接通过 McpToolCallbackService 从数据库获取工具并创建 ToolCallback
     */
    public List<ToolCallback> convertToolsToCallbacks(List<ToolInfo> tools) {
        List<ToolCallback> callbacks = new ArrayList<>();

        for (ToolInfo tool : tools) {
            try {
                ToolCallback callback = null;

                // 优先通过 ID 获取 ToolCallback
                if (StrUtil.isNotBlank(tool.getId())) {
                    try {
                        Long toolId = Long.parseLong(tool.getId());
                        callback = mcpToolCallbackService.getToolCallback(toolId);
                        if (callback != null) {
                            log.debug("通过 ID 获取 MCP 工具: id={}, name={}", toolId, tool.getName());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("工具 ID 格式错误: {}", tool.getId());
                    }
                }

                // 如果通过 ID 获取失败，尝试通过名称查找（需要先查询数据库获取 ID）
                if (callback == null && StrUtil.isNotBlank(tool.getName())) {
                    log.warn("无法通过 ID 获取工具，尝试通过名称查找: {}", tool.getName());
                }

                if (callback != null) {
                    callbacks.add(callback);
                    log.debug("已转换 MCP 工具: {}", tool.getName());
                } else {
                    log.warn("无法获取 MCP 工具: id={}, name={}", tool.getId(), tool.getName());
                }
            } catch (Exception e) {
                log.warn("转换工具失败: {}, 错误: {}", tool.getName(), e.getMessage());
            }
        }

        return callbacks;
    }

}
