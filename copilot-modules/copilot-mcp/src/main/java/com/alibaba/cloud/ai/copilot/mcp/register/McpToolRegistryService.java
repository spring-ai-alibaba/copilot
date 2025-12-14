package com.alibaba.cloud.ai.copilot.mcp.register;

import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.enums.McpToolTypeEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 工具动态注册服务
 * 负责将 MCP 工具动态注册到 Spring 容器中
 *
 * @author Administrator
 */
@Slf4j
@Service
public class McpToolRegistryService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 存储已注册的工具 Bean 名称
    private final Map<Long, String> registeredTools = new ConcurrentHashMap<>();
    @Resource
    private ApplicationContext applicationContext;

    /**
     * 注册 MCP 工具
     *
     * @param tool MCP 工具实体
     * @return 是否注册成功
     */
    public boolean registerTool(McpToolInfo tool) {
        try {
            if (tool.getType().equals(McpToolTypeEnum.LOCAL.getValue())) {
                return registerLocalTool(tool);
            } else if (tool.getType().equals(McpToolTypeEnum.REMOTE.getValue())) {
                return registerRemoteTool(tool);
            }
        } catch (Exception e) {
            log.error("注册工具失败: {}", tool.getName(), e);
            return false;
        }
        return false;
    }

    /**
     * 注销 MCP 工具
     *
     * @param toolId 工具ID
     * @return 是否注销成功
     */
    public boolean unregisterTool(Long toolId) {
        try {
            String beanName = registeredTools.get(toolId);
            if (beanName == null) {
                log.warn("工具未注册，无法注销: {}", toolId);
                return false;
            }

            if (applicationContext instanceof ConfigurableApplicationContext configurableContext) {

                // 从容器中移除 Bean
                if (configurableContext.getBeanFactory().containsBean(beanName)) {
                    configurableContext.getBeanFactory().destroyBean(beanName);
                    log.info("成功注销 MCP 工具: {} ({})", toolId, beanName);
                }
            }

            registeredTools.remove(toolId);
            return true;
        } catch (Exception e) {
            log.error("注销工具失败: {}", toolId, e);
            return false;
        }
    }

    /**
     * 注册本地工具
     */
    private boolean registerLocalTool(McpToolInfo tool) {
        try {
            registeredTools.put(tool.getId(), "mcpTool_" + tool.getId());
            log.info("成功注册本地 MCP 工具: {}", tool.getName());
            return true;
        } catch (Exception e) {
            log.error("注册本地工具失败: {}", tool.getName(), e);
            return false;
        }
    }

    /**
     * 注册远程工具
     */
    private boolean registerRemoteTool(McpToolInfo tool) {
        try {
            // 解析配置信息
            Map<String, Object> config = objectMapper.readValue(
                    tool.getConfigJson() != null ? tool.getConfigJson() : "{}",
                    new TypeReference<>() {
                    });

            // 获取传输配置
            @SuppressWarnings("unchecked")
            Map<String, Object> transport = (Map<String, Object>) config.get("transport");
            if (transport == null) {
                log.error("远程工具配置缺少 transport 信息: {}", tool.getName());
                return false;
            }

            String transportType = (String) transport.get("type");
            String url = (String) transport.get("url");

            if (url == null || url.isEmpty()) {
                log.error("远程工具配置缺少 URL: {}", tool.getName());
                return false;
            }

            // 提取请求头
            Map<String, String> headers = extractHeaders(transport);

            // 创建远程 MCP 客户端包装器
            RemoteMcpClientWrapper clientWrapper = new RemoteMcpClientWrapper(
                    tool.getId(),
                    tool.getName(),
                    url,
                    transportType,
                    headers,
                    config
            );

            // 注册为 Spring Bean
            String beanName = "mcpClient_" + tool.getId();
            if (applicationContext instanceof ConfigurableApplicationContext configurableContext) {
                configurableContext.getBeanFactory().registerSingleton(beanName, clientWrapper);
                registeredTools.put(tool.getId(), beanName);
                log.info("成功注册远程 MCP 工具: {} ({} -> {})", tool.getName(), transportType, url);
                return true;
            } else {
                log.error("ApplicationContext 不是 ConfigurableApplicationContext，无法注册 Bean");
                return false;
            }
        } catch (Exception e) {
            log.error("注册远程工具失败: {}", tool.getName(), e);
            return false;
        }
    }

    /**
     * 提取请求头
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractHeaders(Map<String, Object> transport) {
        Map<String, Object> headers = (Map<String, Object>) transport.get("headers");
        if (headers == null) {
            return Map.of();
        }
        return headers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() != null ? e.getValue().toString() : ""));
    }

    /**
     * 检查工具是否已注册
     */
    public boolean isRegistered(Long toolId) {
        return registeredTools.containsKey(toolId);
    }

    /**
     * 远程 MCP 客户端包装器
     * 用于存储远程 MCP 服务的连接信息
     */
    public record RemoteMcpClientWrapper(Long toolId, String toolName, String url, String transportType,
                                         Map<String, String> headers, Map<String, Object> config) {
    }
}
