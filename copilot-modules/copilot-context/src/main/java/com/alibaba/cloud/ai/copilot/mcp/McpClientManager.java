package com.alibaba.cloud.ai.copilot.mcp;

import com.alibaba.cloud.ai.copilot.config.McpProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器
 * 管理与外部 MCP Server 的连接，支持 STDIO 和 SSE 传输
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpToolInfoMapper mcpToolInfoMapper;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    /**
     * 缓存活跃的 MCP Client
     */
    private final Map<Long, McpSyncClient> activeClients = new ConcurrentHashMap<>();

    /**
     * 获取指定工具 ID 列表的 ToolCallback
     * 用于注入到 ReactAgent
     *
     * @param toolIds 工具 ID 列表
     * @return ToolCallback 列表
     */
    public List<ToolCallback> getToolCallbacks(List<Long> toolIds) {
        List<McpSyncClient> clients = new ArrayList<>();

        for (Long toolId : toolIds) {
            try {
                McpSyncClient client = getOrCreateClient(toolId);
                if (client != null) {
                    clients.add(client);
                }
            } catch (Exception e) {
                log.error("Failed to create MCP client for tool {}: {}", toolId, e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 McpToolUtils 将 MCP Client 转换为 ToolCallback
        return McpToolUtils.getToolCallbacksFromSyncClients(clients);
    }

    /**
     * 获取或创建 MCP Client
     *
     * @param toolId 工具 ID
     * @return MCP 同步客户端
     */
    private McpSyncClient getOrCreateClient(Long toolId) {
        return activeClients.computeIfAbsent(toolId, id -> {
            McpToolInfo tool = mcpToolInfoMapper.selectById(id);
            if (tool == null || !ToolStatus.isEnabled(tool.getStatus())) {
                return null;
            }

            try {
                McpSyncClient client = createMcpClient(tool);
                // 初始化连接
                client.initialize();
                log.info("Successfully created MCP client for tool: {}", tool.getName());
                return client;
            } catch (Exception e) {
                log.error("Failed to create MCP client for tool {}: {}", tool.getName(), e.getMessage());
                return null;
            }
        });
    }

    /**
     * 根据工具配置创建 MCP Client
     *
     * @param tool 工具信息
     * @return MCP 同步客户端
     */
    private McpSyncClient createMcpClient(McpToolInfo tool) throws Exception {
        McpToolConfig config = parseConfig(tool.getConfigJson());

        if ("LOCAL".equals(tool.getType())) {
            // STDIO 传输 - 本地命令行工具
            return createStdioClient(config, tool.getName());
        } else {
            // SSE/Streamable-HTTP 传输 - 远程服务
            return createRemoteClient(config, tool.getName());
        }
    }

    /**
     * 创建 STDIO Client (本地命令行工具)
     *
     * @param config 配置
     * @param toolName 工具名称
     * @return MCP 同步客户端
     */
    private McpSyncClient createStdioClient(McpToolConfig config, String toolName) throws Exception {
        // 处理 Windows 系统的命令执行问题
        String command = resolveCommand(config.getCommand());
        List<String> args = config.getArgs() != null ? config.getArgs() : Collections.emptyList();
        
        log.info("Creating STDIO client for tool: {}, command: {}, args: {}", toolName, command, args);
        
        // 使用 ServerParameters 构建器
        ServerParameters serverParams = ServerParameters.builder(command)
            .args(args)
            .env(config.getEnv() != null ? config.getEnv() : Collections.emptyMap())
            .build();

        // 创建 JSON mapper - 使用全局 objectMapper
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        // 创建 STDIO 传输
        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        // 创建 Implementation 对象
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
            "copilot-mcp-client-" + toolName, 
            "1.0.0"
        );

        return McpClient.sync(transport)
            .clientInfo(clientInfo)
            .requestTimeout(Duration.ofSeconds(mcpProperties.getClient().getRequestTimeout()))
            .build();
    }

    /**
     * 解析命令，处理 Windows 系统的兼容性问题
     * Windows 上的 npx、npm、node 等命令实际上是 .cmd 文件，
     * Java ProcessBuilder 无法直接执行，需要添加 .cmd 后缀
     *
     * @param command 原始命令
     * @return 处理后的命令
     */
    private String resolveCommand(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }
        
        // 检查是否是 Windows 系统
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        if (isWindows) {
            // Windows 上需要处理 npm、npx、node 等命令
            String lowerCommand = command.toLowerCase();
            if (lowerCommand.equals("npx") || lowerCommand.equals("npm") || 
                lowerCommand.equals("node") || lowerCommand.equals("pnpm") ||
                lowerCommand.equals("yarn") || lowerCommand.equals("uvx") ||
                lowerCommand.equals("uv")) {
                // 如果命令没有 .cmd 后缀，添加它
                if (!lowerCommand.endsWith(".cmd") && !lowerCommand.endsWith(".exe")) {
                    String resolvedCommand = command + ".cmd";
                    log.debug("Windows detected, resolved command: {} -> {}", command, resolvedCommand);
                    return resolvedCommand;
                }
            }
        }
        
        return command;
    }

    /**
     * 创建远程 Client (SSE/Streamable-HTTP)
     *
     * @param config 配置
     * @param toolName 工具名称
     * @return MCP 同步客户端
     */
    private McpSyncClient createRemoteClient(McpToolConfig config, String toolName) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(config.getBaseUrl())
            .build();

        // 创建 Implementation 对象
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
            "copilot-mcp-client-" + toolName, 
            "1.0.0"
        );

        return McpClient.sync(transport)
            .clientInfo(clientInfo)
            .requestTimeout(Duration.ofSeconds(mcpProperties.getClient().getRequestTimeout()))
            .build();
    }

    /**
     * 解析配置 JSON
     *
     * @param configJson 配置 JSON 字符串
     * @return 配置对象
     */
    private McpToolConfig parseConfig(String configJson) throws Exception {
        if (configJson == null || configJson.isBlank()) {
            return new McpToolConfig();
        }
        return objectMapper.readValue(configJson, McpToolConfig.class);
    }

    /**
     * 测试连接
     *
     * @param tool 工具信息
     * @return 测试结果
     */
    public McpToolTestResult testConnection(McpToolInfo tool) {
        McpSyncClient client = null;
        try {
            client = createMcpClient(tool);
            client.initialize();

            ListToolsResult toolsResult = client.listTools();
            int toolCount = toolsResult.tools() != null ? toolsResult.tools().size() : 0;

            // 获取工具名称列表用于展示
            List<String> toolNames = new ArrayList<>();
            if (toolsResult.tools() != null) {
                toolsResult.tools().forEach(t -> toolNames.add(t.name()));
            }

            return McpToolTestResult.success(
                    String.format("连接成功，发现 %d 个工具", toolCount),
                    toolCount,
                    toolNames
            );
        } catch (Exception e) {
            log.error("Test connection failed for tool {}: {}", tool.getName(), e.getMessage());
            return McpToolTestResult.fail("连接失败: " + e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("Error closing test client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 刷新指定工具的客户端连接
     *
     * @param toolId 工具 ID
     */
    public void refreshClient(Long toolId) {
        closeClient(toolId);
        // 下次使用时会自动重新创建
    }

    /**
     * 关闭指定工具的客户端连接
     *
     * @param toolId 工具 ID
     */
    public void closeClient(Long toolId) {
        McpSyncClient client = activeClients.remove(toolId);
        if (client != null) {
            try {
                client.close();
                log.info("Closed MCP client for tool: {}", toolId);
            } catch (Exception e) {
                log.warn("Error closing MCP client for tool {}: {}", toolId, e.getMessage());
            }
        }
    }

    /**
     * 应用关闭时清理所有连接
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up {} MCP clients...", activeClients.size());
        activeClients.keySet().forEach(this::closeClient);
    }

    /**
     * 获取当前活跃的客户端数量
     *
     * @return 活跃客户端数量
     */
    public int getActiveClientCount() {
        return activeClients.size();
    }

    /**
     * MCP 工具配置类
     */
    @Data
    public static class McpToolConfig {
        // STDIO 配置
        private String command;
        private List<String> args;
        private Map<String, String> env;

        // 远程配置
        private String baseUrl;

        // 通用配置
        private Integer timeout;
    }
}
