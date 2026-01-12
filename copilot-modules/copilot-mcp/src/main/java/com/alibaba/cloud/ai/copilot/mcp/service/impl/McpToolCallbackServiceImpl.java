package com.alibaba.cloud.ai.copilot.mcp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolCallbackService;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolInfoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP 工具回调服务实现类
 * 将 MCP 工具转换为 Spring AI 的 ToolCallback
 *
 * @author evo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolCallbackServiceImpl implements McpToolCallbackService {

    private final McpToolInfoService mcpToolInfoService;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    // 缓存已加载的类和 Bean
    private final Map<String, Object> beanCache = new ConcurrentHashMap<>();

    @Override
    public List<ToolCallback> getToolCallbacks(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ToolCallback> callbacks = new ArrayList<>();
        for (Long toolId : toolIds) {
            ToolCallback callback = getToolCallback(toolId);
            if (callback != null) {
                callbacks.add(callback);
            }
        }
        return callbacks;
    }

    @Override
    public ToolCallback getToolCallback(Long toolId) {
        if (toolId == null) {
            return null;
        }
        // 从数据库获取工具信息
        McpToolInfo toolInfo = mcpToolInfoService.getById(toolId);
        if (toolInfo == null) {
            log.warn("MCP 工具不存在: toolId={}", toolId);
            return null;
        }

        if (!"ENABLED".equals(toolInfo.getStatus())) {
            log.warn("MCP 工具未启用: toolId={}, status={}", toolId, toolInfo.getStatus());
            return null;
        }

        // 创建 ToolCallback
        return createToolCallback(toolInfo);
    }

    @Override
    public String invokeTool(Long toolId, Map<String, Object> parameters) {
        McpToolInfo toolInfo = mcpToolInfoService.getById(toolId);
        if (toolInfo == null) {
            return "{\"error\": \"Tool not found\"}";
        }

        return executeToolLogic(toolInfo, parameters);
    }

    @Override
    public String invokeToolByName(String toolName, Map<String, Object> parameters) {
        List<McpToolInfo> tools = mcpToolInfoService.searchByName(toolName);
        if (tools.isEmpty()) {
            return "{\"error\": \"Tool not found: " + toolName + "\"}";
        }

        // 使用第一个匹配的工具
        McpToolInfo toolInfo = tools.get(0);
        if (!"ENABLED".equals(toolInfo.getStatus())) {
            return "{\"error\": \"Tool is disabled: " + toolName + "\"}";
        }

        return executeToolLogic(toolInfo, parameters);
    }

    @Override
    public List<ToolCallback> getAllEnabledToolCallbacks() {
        List<McpToolInfo> enabledTools = mcpToolInfoService.listByStatus("ENABLED");
        List<ToolCallback> callbacks = new ArrayList<>();

        for (McpToolInfo tool : enabledTools) {
            ToolCallback callback = getToolCallback(tool.getId());
            if (callback != null) {
                callbacks.add(callback);
            }
        }

        return callbacks;
    }

    @Override
    public boolean isToolAvailable(Long toolId) {
        if (toolId == null) {
            return false;
        }

        McpToolInfo toolInfo = mcpToolInfoService.getById(toolId);
        return toolInfo != null && "ENABLED".equals(toolInfo.getStatus());
    }

    /**
     * 创建 ToolCallback
     */
    private ToolCallback createToolCallback(McpToolInfo toolInfo) {
        try {
            final McpToolInfo finalToolInfo = toolInfo;
            String configJson = finalToolInfo.getConfigJson();
            JSONObject jsonObject = JSONUtil.parseObj(configJson);
            final String finalInputSchema = jsonObject.getStr("parameters");

            // 创建 ToolCallback 匿名实现
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder()
                            .name(finalToolInfo.getName())
                            .description(finalToolInfo.getDescription() != null ?
                                    finalToolInfo.getDescription() : "MCP Tool: " + finalToolInfo.getName())
                            .inputSchema(finalInputSchema)
                            .build();
                }

                @Override
                public String call(String toolInput) {
                    try {
                        Map<String, Object> requestParam = JSONUtil.parseObj(toolInput);
                        String executed = executeToolLogic(finalToolInfo, requestParam);
                        log.info("执行 MCP 工具成功: toolName={}, result={}", finalToolInfo.getName(), executed);
                        return executed;
                    } catch (Exception e) {
                        log.error("执行 MCP 工具失败: toolName={}", finalToolInfo.getName(), e);
                        return "{\"error\": \"" + e.getMessage() + "\"}";
                    }
                }
            };
        } catch (Exception e) {
            log.error("创建 ToolCallback 失败: toolId={}", toolInfo.getId(), e);
            return null;
        }
    }

    /**
     * 执行工具逻辑
     */
    private String executeToolLogic(McpToolInfo toolInfo, Map<String, Object> parameters) {
        try {
            log.info("执行 MCP 工具: name={}, type={}, parameters={}",
                    toolInfo.getName(), toolInfo.getType(), parameters);

            // 根据工具类型执行不同的逻辑
            String toolType = toolInfo.getType();
            if ("LOCAL".equals(toolType)) {
                return executeLocalTool(toolInfo, parameters);
            } else if ("REMOTE".equals(toolType)) {
                return executeRemoteTool(toolInfo, parameters);
            } else {
                return "{\"error\": \"Unknown tool type: " + toolType + "\"}";
            }
        } catch (Exception e) {
            log.error("执行工具失败: name={}", toolInfo.getName(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 执行本地工具
     * 支持两种配置格式：
     * 1. Java 反射调用: {className, methodName, parameterTypes, parameters}
     * 2. 简单方法调用: {beanName, methodName}
     */
    private String executeLocalTool(McpToolInfo toolInfo, Map<String, Object> parameters) {
        try {
            log.info("执行本地工具: name={}, config={}", toolInfo.getName(), toolInfo.getConfigJson());

            // 解析配置
            Map<String, Object> config = objectMapper.readValue(
                    toolInfo.getConfigJson(),
                    new TypeReference<>() {
                    }
            );

            String className = (String) config.get("className");
            String methodName = (String) config.get("methodName");
            String beanName = (String) config.get("beanName");

            if (methodName == null || methodName.isEmpty()) {
                return createErrorResult("工具配置错误：缺少 methodName");
            }

            // 获取目标对象
            Object targetBean;
            if (className != null && !className.isEmpty()) {
                // 通过类名获取 Bean
                targetBean = getBeanByClassName(className);
            } else if (beanName != null && !beanName.isEmpty()) {
                // 通过 Bean 名称获取
                targetBean = applicationContext.getBean(beanName);
            } else {
                return createErrorResult("工具配置错误：需要 className 或 beanName");
            }

            if (targetBean == null) {
                return createErrorResult("找不到工具实现类: " + (className != null ? className : beanName));
            }

            // 获取参数类型
            List<String> paramTypeNames = (List<String>) config.get("parameterTypes");
            List<Map<String, Object>> paramConfigs = (List<Map<String, Object>>) config.get("parameters");

            // 调用方法
            Object result = invokeMethod(targetBean, methodName, paramTypeNames, paramConfigs, parameters);

            // 返回结果
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("toolName", toolInfo.getName());
            response.put("result", result);
            response.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("执行本地工具失败: name={}", toolInfo.getName(), e);
            return createErrorResult("执行失败: " + e.getMessage());
        }
    }

    /**
     * 通过类名获取 Spring Bean
     */
    private Object getBeanByClassName(String className) {
        // 先从缓存获取
        Object cached = beanCache.get(className);
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> clazz = Class.forName(className);
            Object bean = applicationContext.getBean(clazz);
            beanCache.put(className, bean);
            return bean;
        } catch (Exception e) {
            log.warn("通过类名获取 Bean 失败: {}", className, e);
            return null;
        }
    }

    /**
     * 通过反射调用方法
     */
    private Object invokeMethod(Object target, String methodName, List<String> paramTypeNames,
                                List<Map<String, Object>> paramConfigs, Map<String, Object> inputParams) throws Exception {
        Class<?> targetClass = target.getClass();
        Method targetMethod = null;

        // 查找方法
        if (paramTypeNames != null && !paramTypeNames.isEmpty()) {
            // 有参数类型定义，精确匹配
            Class<?>[] paramTypes = new Class<?>[paramTypeNames.size()];
            for (int i = 0; i < paramTypeNames.size(); i++) {
                paramTypes[i] = getClassForName(paramTypeNames.get(i));
            }
            targetMethod = targetClass.getMethod(methodName, paramTypes);
        } else {
            // 没有参数类型，按名称查找
            for (Method method : targetClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    targetMethod = method;
                    break;
                }
            }
        }

        if (targetMethod == null) {
            throw new NoSuchMethodException("找不到方法: " + methodName);
        }

        // 准备参数
        Object[] args = prepareMethodArgs(targetMethod, paramConfigs, inputParams);

        // 调用方法
        log.info("调用方法: {}.{}({})", targetClass.getSimpleName(), methodName, Arrays.toString(args));
        return targetMethod.invoke(target, args);
    }

    /**
     * 准备方法调用参数
     */
    private Object[] prepareMethodArgs(Method method, List<Map<String, Object>> paramConfigs,
                                       Map<String, Object> inputParams) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (paramConfigs != null && !paramConfigs.isEmpty()) {
            // 按配置的参数名从输入中获取值
            for (int i = 0; i < Math.min(paramConfigs.size(), paramTypes.length); i++) {
                Map<String, Object> paramConfig = paramConfigs.get(i);
                String paramName = (String) paramConfig.get("name");
                Object value = inputParams.get(paramName);
                args[i] = convertValue(value, paramTypes[i]);
            }
        } else {
            // 按参数顺序传入
            int i = 0;
            for (Object value : inputParams.values()) {
                if (i >= paramTypes.length) break;
                args[i] = convertValue(value, paramTypes[i]);
                i++;
            }
        }

        return args;
    }

    /**
     * 转换参数值类型
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // 基本类型转换
        String strValue = value.toString();
        if (targetType == String.class) {
            return strValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(strValue);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(strValue);
        }

        // 尝试用 ObjectMapper 转换
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            log.warn("参数类型转换失败: value={}, targetType={}", value, targetType);
            return value;
        }
    }

    /**
     * 获取类名对应的 Class
     */
    private Class<?> getClassForName(String className) throws ClassNotFoundException {
        return switch (className) {
            case "String", "java.lang.String" -> String.class;
            case "Integer", "java.lang.Integer", "int" -> Integer.class;
            case "Long", "java.lang.Long", "long" -> Long.class;
            case "Double", "java.lang.Double", "double" -> Double.class;
            case "Float", "java.lang.Float", "float" -> Float.class;
            case "Boolean", "java.lang.Boolean", "boolean" -> Boolean.class;
            default -> Class.forName(className);
        };
    }

    /**
     * 执行远程工具
     * 支持两种配置格式：
     * 1. npx 命令: {command: "npx", args: [...]}
     * 2. HTTP 端点: {endpoint: "http://..."}
     */
    private String executeRemoteTool(McpToolInfo toolInfo, Map<String, Object> parameters) {
        try {
            Map<String, Object> config = objectMapper.readValue(
                    toolInfo.getConfigJson(),
                    new TypeReference<>() {
                    }
            );

            String command = (String) config.get("command");

            // 判断是 npx 命令还是 HTTP 端点
            if ("npx".equals(command) || "node".equals(command)) {
                return executeNpxTool(toolInfo, config, parameters);
            } else {
                return executeHttpTool(toolInfo, config, parameters);
            }
        } catch (Exception e) {
            log.error("执行远程工具失败: name={}", toolInfo.getName(), e);
            return createErrorResult("执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行 npx/node 命令工具
     * 配置格式: {command: "npx", args: ["-y", "@anthropics/mcp-server-time"]}
     */
    private String executeNpxTool(McpToolInfo toolInfo, Map<String, Object> config, Map<String, Object> parameters) {
        try {
            String command = (String) config.get("command");
            List<String> args = (List<String>) config.get("args");
            Map<String, String> env = (Map<String, String>) config.get("env");

            log.info("执行 npx 工具: name={}, command={}, args={}", toolInfo.getName(), command, args);

            // 构建命令
            List<String> cmdList = new ArrayList<>();
            cmdList.add(command);
            if (args != null) {
                cmdList.addAll(args);
            }

            // 将参数转为 JSON 并作为标准输入传递
            String inputJson = objectMapper.writeValueAsString(parameters);

            // 创建进程
            ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
            processBuilder.redirectErrorStream(true);

            // 设置环境变量
            if (env != null && !env.isEmpty()) {
                processBuilder.environment().putAll(env);
            }

            Process process = processBuilder.start();

            // 写入参数到标准输入
            if (!parameters.isEmpty()) {
                process.getOutputStream().write(inputJson.getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().close();
            }

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程结束（超时 30 秒）
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return createErrorResult("工具执行超时");
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();

            log.info("npx 工具执行完成: name={}, exitCode={}, output={}", toolInfo.getName(), exitCode, outputStr);

            // 构建结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", exitCode == 0);
            result.put("toolName", toolInfo.getName());
            result.put("exitCode", exitCode);
            result.put("output", outputStr);
            result.put("timestamp", System.currentTimeMillis());

            // 尝试解析输出为 JSON
            if (!outputStr.isEmpty()) {
                try {
                    Object parsedOutput = objectMapper.readValue(outputStr, Object.class);
                    result.put("result", parsedOutput);
                } catch (Exception e) {
                    result.put("result", outputStr);
                }
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("执行 npx 工具失败: name={}", toolInfo.getName(), e);
            return createErrorResult("npx 执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行 HTTP 端点工具
     * 配置格式: {endpoint: "http://...", method: "POST", headers: {...}}
     */
    private String executeHttpTool(McpToolInfo toolInfo, Map<String, Object> config, Map<String, Object> parameters) {
        try {
            String endpoint = (String) config.get("endpoint");
            if (endpoint == null || endpoint.isEmpty()) {
                return createErrorResult("工具配置错误：缺少 endpoint");
            }

            log.info("执行 HTTP 工具: name={}, endpoint={}", toolInfo.getName(), endpoint);

            // TODO: 实现 HTTP 调用
            // 这里可以注入 RestTemplate 或 WebClient 来发起远程调用
            // 目前返回占位结果

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("toolName", toolInfo.getName());
            result.put("endpoint", endpoint);
            result.put("message", "HTTP 工具调用功能待实现");
            result.put("parameters", parameters);
            result.put("timestamp", System.currentTimeMillis());

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("执行 HTTP 工具失败: name={}", toolInfo.getName(), e);
            return createErrorResult("HTTP 执行失败: " + e.getMessage());
        }
    }

    /**
     * 创建错误结果
     */
    private String createErrorResult(String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", message);
            error.put("timestamp", System.currentTimeMillis());
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"" + message + "\"}";
        }
    }
}

