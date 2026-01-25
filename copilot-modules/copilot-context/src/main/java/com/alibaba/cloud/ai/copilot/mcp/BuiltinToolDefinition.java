package com.alibaba.cloud.ai.copilot.mcp;

import org.springframework.ai.tool.ToolCallback;

import java.util.function.Supplier;

/**
 * 内置工具定义
 * 用于描述系统内置的工具信息
 *
 * @param name             工具名称（唯一标识）
 * @param displayName      显示名称
 * @param description      工具描述
 * @param callbackSupplier 创建 ToolCallback 的供应者
 * @author copilot team: evo
 * @email exotisch@163.com
 */
public record BuiltinToolDefinition(String name, String displayName, String description,
                                    Supplier<ToolCallback> callbackSupplier) {

}

