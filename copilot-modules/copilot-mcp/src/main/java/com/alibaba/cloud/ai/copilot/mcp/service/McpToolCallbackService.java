package com.alibaba.cloud.ai.copilot.mcp.service;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具回调服务接口
 * 负责将 MCP 工具转换为 Spring AI 的 ToolCallback
 *
 * @author evo
 */
public interface McpToolCallbackService {

    /**
     * 根据工具ID列表获取 ToolCallback 列表
     *
     * @param toolIds 工具ID列表
     * @return ToolCallback 列表
     */
    List<ToolCallback> getToolCallbacks(List<Long> toolIds);

    /**
     * 根据单个工具ID获取 ToolCallback
     *
     * @param toolId 工具ID
     * @return ToolCallback，如果工具不存在或未启用则返回 null
     */
    ToolCallback getToolCallback(Long toolId);

    /**
     * 执行 MCP 工具
     *
     * @param toolId     工具ID
     * @param parameters 调用参数
     * @return 工具执行结果
     */
    String invokeTool(Long toolId, Map<String, Object> parameters);

    /**
     * 根据工具名称执行 MCP 工具
     *
     * @param toolName   工具名称
     * @param parameters 调用参数
     * @return 工具执行结果
     */
    String invokeToolByName(String toolName, Map<String, Object> parameters);

    /**
     * 获取所有启用的工具的 ToolCallback 列表
     *
     * @return ToolCallback 列表
     */
    List<ToolCallback> getAllEnabledToolCallbacks();

    /**
     * 检查工具是否存在且启用
     *
     * @param toolId 工具ID
     * @return 是否可用
     */
    boolean isToolAvailable(Long toolId);
}

