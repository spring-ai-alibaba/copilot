package com.alibaba.cloud.ai.copilot.service.mcp;

import com.alibaba.cloud.ai.copilot.config.McpProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpSyncClientWrapper;
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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器
 *
 * <p>管理与外部 MCP Server 的连接（STDIO / SSE），连接按工具 ID 持久缓存复用：
 * agent 是每次请求构建的，若连接跟随 agent 生命周期，STDIO 类型每条消息都会
 * 拉起一个子进程且无人回收。此处缓存框架的 {@link McpSyncClientWrapper}
 * （initialize 幂等），注册到任意多个 Toolkit 共享同一底层连接。</p>
 *
 * @author copilot team: evo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpToolInfoMapper mcpToolInfoMapper;
    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    /**
     * 活跃连接缓存：toolId -> 框架 wrapper（含底层 McpSyncClient）
     */
    private final Map<Long, McpSyncClientWrapper> activeClients = new ConcurrentHashMap<>();

    /**
     * 把所有启用状态的 MCP 工具注册到指定 Toolkit（供 agent 构建时调用）。
     * 单个 server 注册失败只记日志并使其缓存失效（下次构建重试），不影响其他工具与聊天主流程。
     *
     * @param toolkit agent 的工具箱
     * @return 成功注册的 server 数量
     */
    public int registerEnabledTools(Toolkit toolkit) {
        List<McpToolInfo> enabled = mcpToolInfoMapper.selectList(
                new LambdaQueryWrapper<McpToolInfo>()
                        .eq(McpToolInfo::getStatus, ToolStatus.ENABLED.getValue()));
        int count = 0;
        for (McpToolInfo tool : enabled) {
            McpSyncClientWrapper wrapper = getOrCreateClient(tool.getId());
            if (wrapper == null) {
                continue;
            }
            try {
                toolkit.registration().mcpClient(wrapper).apply();
                count++;
            } catch (Exception e) {
                log.warn("注册 MCP server [{}] 到 toolkit 失败，跳过并重置连接: {}", tool.getName(), e.getMessage());
                // 连接可能已失效（如 STDIO 子进程退出），关闭并清出缓存，下次构建时重建
                closeClient(tool.getId());
            }
        }
        return count;
    }

    /**
     * 获取或创建 MCP Client（持久缓存）
     *
     * @param toolId 工具 ID
     * @return 框架 wrapper，创建失败返回 null
     */
    private McpSyncClientWrapper getOrCreateClient(Long toolId) {
        return activeClients.computeIfAbsent(toolId, id -> {
            McpToolInfo tool = mcpToolInfoMapper.selectById(id);
            if (tool == null || !ToolStatus.isEnabled(tool.getStatus())) {
                return null;
            }
            try {
                McpSyncClient client = createMcpClient(tool);
                client.initialize();
                McpSyncClientWrapper wrapper = new McpSyncClientWrapper(safeName(tool), client);
                log.info("Successfully created MCP client for tool: {}", tool.getName());
                return wrapper;
            } catch (Exception e) {
                log.error("Failed to create MCP client for tool {}: {}", tool.getName(), e.getMessage());
                return null;
            }
        });
    }

    /**
     * server 名称会参与工具命名/日志，收敛为模型安全字符集（字母数字_-）
     */
    private String safeName(McpToolInfo tool) {
        String base = tool.getName() == null ? "" : tool.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        if (base.isBlank() || base.chars().allMatch(c -> c == '_')) {
            base = "mcp_" + tool.getId();
        }
        return base;
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
            // SSE 传输 - 远程服务
            return createRemoteClient(config, tool.getName());
        }
    }

    /**
     * 创建 STDIO Client (本地命令行工具)
     */
    private McpSyncClient createStdioClient(McpToolConfig config, String toolName) throws Exception {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("LOCAL 类型必须配置 command");
        }
        // 处理 Windows 系统的命令执行问题
        String command = resolveCommand(config.getCommand());
        List<String> args = config.getArgs() != null ? config.getArgs() : Collections.emptyList();

        log.info("Creating STDIO client for tool: {}, command: {}, args: {}", toolName, command, args);

        ServerParameters serverParams = ServerParameters.builder(command)
            .args(args)
            .env(config.getEnv() != null ? config.getEnv() : Collections.emptyMap())
            .build();

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

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
     */
    private String resolveCommand(String command) {
        if (command == null || command.isBlank()) {
            return command;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (isWindows) {
            // 仅 npm 系命令是 .cmd 脚本壳需要补后缀；
            // uv/uvx/node 是原生 exe，ProcessBuilder 可直接执行，补 .cmd 反而找不到命令
            String lowerCommand = command.toLowerCase();
            if (lowerCommand.equals("npx") || lowerCommand.equals("npm") ||
                lowerCommand.equals("pnpm") || lowerCommand.equals("yarn")) {
                String resolvedCommand = command + ".cmd";
                log.debug("Windows detected, resolved command: {} -> {}", command, resolvedCommand);
                return resolvedCommand;
            }
        }

        return command;
    }

    /**
     * 创建远程 Client (SSE)
     */
    private McpSyncClient createRemoteClient(McpToolConfig config, String toolName) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("REMOTE 类型必须配置 baseUrl");
        }
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(config.getBaseUrl())
            .build();

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
     */
    private McpToolConfig parseConfig(String configJson) throws Exception {
        if (configJson == null || configJson.isBlank()) {
            return new McpToolConfig();
        }
        return objectMapper.readValue(configJson, McpToolConfig.class);
    }

    /**
     * 保存前校验工具配置：JSON 合法性 + 类型必填项。
     * 否则非法配置要等到连接测试才暴露，且报错是难读的 Jackson 堆栈
     * （Windows 路径的 "\\" 未转义是高频翻车点）。
     *
     * @throws IllegalArgumentException 配置不合法
     */
    public void validateConfig(McpToolInfo tool) {
        McpToolConfig config;
        try {
            config = parseConfig(tool.getConfigJson());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "configJson 不是合法 JSON（Windows 路径请用 / 或转义为 \\\\）: " + e.getMessage());
        }
        String type = tool.getType() == null ? "LOCAL" : tool.getType();
        if ("LOCAL".equals(type) && (config.getCommand() == null || config.getCommand().isBlank())) {
            throw new IllegalArgumentException("LOCAL 类型必须在 configJson 中配置 command");
        }
        if ("REMOTE".equals(type) && (config.getBaseUrl() == null || config.getBaseUrl().isBlank())) {
            throw new IllegalArgumentException("REMOTE 类型必须在 configJson 中配置 baseUrl");
        }
    }

    /**
     * 测试连接（独立建连，测完即断，不动缓存）
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
     * 刷新指定工具的客户端连接（配置变更后调用，下次使用时重建）
     */
    public void refreshClient(Long toolId) {
        closeClient(toolId);
    }

    /**
     * 关闭指定工具的客户端连接
     */
    public void closeClient(Long toolId) {
        McpSyncClientWrapper wrapper = activeClients.remove(toolId);
        if (wrapper != null) {
            try {
                wrapper.close();
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
        new ArrayList<>(activeClients.keySet()).forEach(this::closeClient);
    }

    /**
     * 获取当前活跃的客户端数量
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
