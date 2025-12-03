package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.tools.BaseTool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 工具编排服务接口
 * 负责管理和编排所有AI工具的调用
 */
public interface ToolOrchestrationService {

    /**
     * 获取所有工具的ToolCallback列表，用于Spring AI 1.1工具调用
     *
     * @return ToolCallback列表
     */
    List<ToolCallback> getAllToolCallbacks();

    /**
     * 根据名称获取工具
     *
     * @param toolName 工具名称
     * @return 工具实例
     */
    BaseTool<?> getTool(String toolName);

    /**
     * 注册工具
     *
     * @param tool 要注册的工具
     */
    void registerTool(BaseTool<?> tool);

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    List<String> getAllToolNames();
}
