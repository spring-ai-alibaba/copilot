package com.alibaba.cloud.ai.copilot.mcp.invoke;

import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.register.McpToolRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 MCP 工具调用服务
 * 负责调用已注册的远程 MCP 工具
 *
 * @author Administrator
 */
@Slf4j
@Service
public class RemoteMcpToolInvokeService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * HTTP 客户端，用于调用远程 MCP 服务
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private McpToolRegistryService mcpToolRegistryService;

    /**
     * 调用远程 MCP 工具
     *
     * @param tool     工具实体
     * @param toolName 工具名称（MCP 协议中的工具名称）
     * @param params   工具参数
     * @return 调用结果
     */
    public Object invokeRemoteTool(McpToolInfo tool, String toolName, Map<String, Object> params) {
        try {
            // 检查工具是否已注册
            if (!mcpToolRegistryService.isRegistered(tool.getId())) {
                throw new IllegalStateException("工具未注册: " + tool.getName());
            }

            // 获取远程客户端包装器
            String beanName = "mcpClient_" + tool.getId();
            McpToolRegistryService.RemoteMcpClientWrapper clientWrapper =
                    applicationContext.getBean(beanName, McpToolRegistryService.RemoteMcpClientWrapper.class);

            // 根据传输类型调用不同的方法
            String transportType = clientWrapper.transportType();
            String url = clientWrapper.url();
            Map<String, String> headers = clientWrapper.headers();

            return switch (transportType.toLowerCase()) {
                case "http", "sse" -> invokeHttpTool(url, toolName, params, headers);
                case "websocket" ->
                    // WebSocket 调用需要特殊处理，这里先返回提示
                        throw new UnsupportedOperationException("WebSocket 传输方式暂未实现");
                default -> throw new IllegalArgumentException("不支持的传输类型: " + transportType);
            };
        } catch (Exception e) {
            log.error("调用远程工具失败: {} -> {}", tool.getName(), toolName, e);
            throw new RuntimeException("调用远程工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 HTTP/SSE 调用工具（使用 MCP 协议）
     */
    private Object invokeHttpTool(String baseUrl, String toolName,
                                  Map<String, Object> params, Map<String, String> headers) {
        try {
            // 构建请求 URL（MCP 协议使用 /mcp 端点）
            String requestUrl = baseUrl;
            if (!requestUrl.endsWith("/")) {
                requestUrl += "/";
            }
            // Spring AI MCP Server 的端点路径是 /mcp
            requestUrl += "mcp";

            // 构建请求体（MCP JSON-RPC 2.0 协议格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", System.currentTimeMillis()); // 使用时间戳作为 ID
            requestBody.put("method", "tools/call");

            Map<String, Object> methodParams = new HashMap<>();
            methodParams.put("name", toolName);
            methodParams.put("arguments", params != null ? params : Map.of());
            requestBody.put("params", methodParams);

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            log.debug("调用 MCP 工具: {} -> {}, 请求: {}", requestUrl, toolName, requestBodyJson);

            // 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            // 添加自定义请求头
            if (headers != null && !headers.isEmpty()) {
                headers.forEach((key, value) -> {
                    if (value != null && !value.isEmpty()) {
                        requestBuilder.header(key, value);
                    }
                });
            }

            HttpRequest request = requestBuilder.build();

            // 发送请求
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("远程工具调用失败，状态码: " + response.statusCode());
            }

            // 解析响应（MCP JSON-RPC 2.0 格式）
            String responseBody = response.body();
            if (responseBody == null || responseBody.isEmpty()) {
                return Map.of("success", true, "result", "");
            }

            // 解析 JSON-RPC 响应
            try {
                Map jsonRpcResponse = objectMapper.readValue(responseBody, Map.class);

                // 检查是否有错误
                if (jsonRpcResponse.containsKey("error")) {
                    Map<String, Object> error = (Map<String, Object>) jsonRpcResponse.get("error");
                    throw new RuntimeException("MCP 工具调用错误: " + error.get("message"));
                }

                // 提取结果
                Map<String, Object> result = (Map<String, Object>) jsonRpcResponse.get("result");
                if (result != null && result.containsKey("content")) {
                    // MCP 协议返回的内容在 content 字段中
                    Object content = result.get("content");
                    if (content instanceof List<?> contentList) {
                        if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                            Map<String, Object> firstContent = (Map<String, Object>) contentList.get(0);
                            return firstContent.get("text"); // 返回文本内容
                        }
                    }
                    return content;
                }

                return result;
            } catch (Exception e) {
                // 如果解析失败，尝试直接返回字符串
                log.warn("解析 MCP 响应失败，返回原始字符串: {}", e.getMessage());
                return responseBody;
            }
        } catch (Exception e) {
            log.error("HTTP 工具调用失败: {} -> {}", baseUrl, toolName, e);
            throw new RuntimeException("HTTP 工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查远程工具是否可用
     *
     * @param tool 工具实体
     * @return 是否可用
     */
    public boolean isToolAvailable(McpToolInfo tool) {
        try {
            if (!mcpToolRegistryService.isRegistered(tool.getId())) {
                return false;
            }

            String beanName = "mcpClient_" + tool.getId();
            McpToolRegistryService.RemoteMcpClientWrapper clientWrapper =
                    applicationContext.getBean(beanName, McpToolRegistryService.RemoteMcpClientWrapper.class);
            String url = clientWrapper.url();
            Map<String, String> clientHeaders = clientWrapper.headers();

            // 尝试调用 MCP 协议的 initialize 方法来检查服务是否可用
            String mcpUrl = url;
            if (!mcpUrl.endsWith("/")) {
                mcpUrl += "/";
            }
            mcpUrl += "mcp";

            // 构建 MCP initialize 请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", System.currentTimeMillis());
            requestBody.put("method", "initialize");
            requestBody.put("params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of(
                            "name", "mcp-client-demo",
                            "version", "1.0.0"
                    )
            ));

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            // 构建 HTTP 请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            // 添加自定义请求头
            if (clientHeaders != null && !clientHeaders.isEmpty()) {
                clientHeaders.forEach((key, value) -> {
                    if (value != null && !value.isEmpty()) {
                        requestBuilder.header(key, value);
                    }
                });
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("工具健康检查失败: {}", tool.getName(), e);
            return false;
        }
    }
}
