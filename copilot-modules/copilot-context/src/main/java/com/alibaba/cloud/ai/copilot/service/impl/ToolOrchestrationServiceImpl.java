package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.ToolOrchestrationService;
import com.alibaba.cloud.ai.copilot.tools.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工具编排服务实现类
 * 负责将所有工具注册并转换为Spring AI的ToolCallback格式
 */
@Slf4j
@Service
public class ToolOrchestrationServiceImpl implements ToolOrchestrationService {

    private final Map<String, BaseTool<?>> toolRegistry = new HashMap<>();
    private final ApplicationContext applicationContext;

    public ToolOrchestrationServiceImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        // 自动注册所有工具
        initializeTools();
    }

    /**
     * 初始化并注册所有工具
     */
    private void initializeTools() {
        try {
            // 从Spring容器获取所有工具Bean
            Map<String, BaseTool> toolBeans = applicationContext.getBeansOfType(BaseTool.class);
            for (BaseTool<?> tool : toolBeans.values()) {
                registerTool(tool);
            }
            log.info("Registered {} tools: {}", toolRegistry.size(), toolRegistry.keySet());
        } catch (Exception e) {
            log.error("Error initializing tools", e);
        }
    }

    @Override
    public List<ToolCallback> getAllToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();

        // 从所有工具类中提取@Tool注解的方法并转换为ToolCallback
        for (BaseTool<?> tool : toolRegistry.values()) {
            try {
                // 使用 ToolCallbacks.from() 从工具实例中提取所有@Tool注解的方法
                List<ToolCallback> toolCallbacks = List.of(ToolCallbacks.from(tool));
                callbacks.addAll(toolCallbacks);
                log.debug("Extracted {} callbacks from tool: {}", toolCallbacks.size(), tool.getName());
            } catch (Exception e) {
                log.error("Error extracting callbacks from tool: {}", tool.getName(), e);
            }
        }

        log.info("Created {} tool callbacks in total", callbacks.size());
        return callbacks;
    }

    @Override
    public BaseTool<?> getTool(String toolName) {
        return toolRegistry.get(toolName);
    }

    @Override
    public void registerTool(BaseTool<?> tool) {
        toolRegistry.put(tool.getName(), tool);
        log.debug("Registered tool: {}", tool.getName());
    }

    @Override
    public List<String> getAllToolNames() {
        return new ArrayList<>(toolRegistry.keySet());
    }
}
