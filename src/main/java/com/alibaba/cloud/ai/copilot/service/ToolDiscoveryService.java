package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import com.alibaba.cloud.ai.copilot.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具发现服务
 * 负责发现系统中所有已注册的工具（包括系统默认工具和MCP工具）
 */
@Service
public class ToolDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolDiscoveryService.class);
    
    private final ApplicationContext applicationContext;
    
    public ToolDiscoveryService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 获取所有工具信息（包括系统默认工具和MCP工具）
     */
    public List<ToolInfo> getAllTools() {
        List<ToolInfo> allTools = new ArrayList<>();
        
        // 1. 获取系统默认工具（@Tool注解的方法）
        allTools.addAll(getSystemTools());
        
        // 2. 获取MCP服务器工具（暂时返回空列表，后续实现）
        allTools.addAll(getMcpTools());
        
        logger.info("发现 {} 个工具: {} 个系统工具, {} 个MCP工具", 
            allTools.size(), 
            allTools.stream().filter(t -> "SYSTEM".equals(t.getType())).count(),
            allTools.stream().filter(t -> "MCP".equals(t.getType())).count());
        
        return allTools;
    }
    
    /**
     * 获取系统默认工具
     */
    public List<ToolInfo> getSystemTools() {
        List<ToolInfo> systemTools = new ArrayList<>();

        try {
            logger.info("开始扫描系统工具...");

            // 获取所有Bean，不限制注解类型
            String[] beanNames = applicationContext.getBeanDefinitionNames();
            logger.info("找到 {} 个Bean定义", beanNames.length);

            for (String beanName : beanNames) {
                try {
                    Object bean = applicationContext.getBean(beanName);
                    Class<?> clazz = getTargetClass(bean);

                    // 查找带@Tool注解的方法
                    Method[] methods = clazz.getDeclaredMethods();
                    for (Method method : methods) {
                        Tool toolAnnotation = method.getAnnotation(Tool.class);
                        if (toolAnnotation != null) {
                            logger.info("发现@Tool注解方法: {}.{}", clazz.getSimpleName(), method.getName());
                            ToolInfo toolInfo = createToolInfoFromMethod(method, toolAnnotation, bean);
                            if (toolInfo != null) {
                                toolInfo.setType("SYSTEM");
                                toolInfo.setSource(clazz.getSimpleName());
                                systemTools.add(toolInfo);
                                logger.info("成功添加系统工具: {} 来自 {}", toolInfo.getName(), toolInfo.getSource());
                            }
                        }
                    }
                } catch (Exception e) {
                    // 跳过无法获取的Bean
                    logger.debug("跳过Bean: {} - {}", beanName, e.getMessage());
                }
            }

            logger.info("系统工具扫描完成，共发现 {} 个工具", systemTools.size());

        } catch (Exception e) {
            logger.error("获取系统工具时发生错误", e);
        }

        return systemTools;
    }

    /**
     * 获取目标类（处理Spring代理）
     */
    private Class<?> getTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();

        // 处理Spring AOP代理
        if (clazz.getName().contains("$$")) {
            // CGLIB代理
            clazz = clazz.getSuperclass();
        } else if (clazz.getName().contains("$Proxy")) {
            // JDK动态代理，获取接口
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length > 0) {
                clazz = interfaces[0];
            }
        }

        return clazz;
    }
    
    /**
     * 获取MCP工具（暂时返回空列表）
     */
    public List<ToolInfo> getMcpTools() {
        List<ToolInfo> mcpTools = new ArrayList<>();
        
        // TODO: 实现MCP工具发现
        // 这里将在后续实现MCP服务器管理时添加
        
        return mcpTools;
    }
    
    /**
     * 从方法和注解创建工具信息
     */
    private ToolInfo createToolInfoFromMethod(Method method, Tool toolAnnotation, Object bean) {
        try {
            logger.debug("创建工具信息: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());

            ToolInfo toolInfo = new ToolInfo();

            // 设置工具名称
            String toolName = toolAnnotation.name();
            if (toolName == null || toolName.trim().isEmpty()) {
                toolName = method.getName();
            }
            toolInfo.setName(toolName);
            toolInfo.setDisplayName(toolName);

            // 设置描述
            String description = toolAnnotation.description();
            if (description == null || description.trim().isEmpty()) {
                description = "No description available";
            }
            toolInfo.setDescription(description);

            // 设置为启用状态
            toolInfo.setEnabled(true);
            toolInfo.setBuiltIn(true);

            // 解析参数
            List<ToolParameter> parameters = parseMethodParameters(method);
            toolInfo.setParameters(parameters);

            logger.debug("工具信息创建成功: {} ({}个参数)", toolName, parameters.size());
            return toolInfo;

        } catch (Exception e) {
            logger.error("创建工具信息时发生错误: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName(), e);
            return null;
        }
    }
    
    /**
     * 解析方法参数
     */
    private List<ToolParameter> parseMethodParameters(Method method) {
        List<ToolParameter> parameters = new ArrayList<>();

        Parameter[] methodParams = method.getParameters();
        logger.debug("解析方法参数: {} 个参数", methodParams.length);

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            ToolParam toolParam = param.getAnnotation(ToolParam.class);

            if (toolParam != null) {
                ToolParameter toolParameter = new ToolParameter();

                // 参数名称
                String paramName = param.getName();
                if (paramName.startsWith("arg")) {
                    // 如果编译时没有保留参数名，尝试从注解获取
                    paramName = "param" + i;
                }
                toolParameter.setName(paramName);

                // 参数类型
                String paramType = getSimpleTypeName(param.getType());
                toolParameter.setType(paramType);

                // 参数描述
                String description = toolParam.description();
                if (description == null || description.trim().isEmpty()) {
                    description = "No description";
                }
                toolParameter.setDescription(description);

                // 是否必需
                toolParameter.setRequired(toolParam.required());

                parameters.add(toolParameter);
                logger.debug("添加参数: {} ({}) - {}", paramName, paramType, description);
            } else {
                logger.debug("跳过无@ToolParam注解的参数: {}", param.getName());
            }
        }

        return parameters;
    }
    
    /**
     * 获取简化的类型名称
     */
    private String getSimpleTypeName(Class<?> type) {
        if (type == String.class) {
            return "string";
        } else if (type == Integer.class || type == int.class) {
            return "number";
        } else if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        } else if (type == Long.class || type == long.class) {
            return "number";
        } else if (type == Double.class || type == double.class) {
            return "number";
        } else if (type.isArray() || List.class.isAssignableFrom(type)) {
            return "array";
        } else {
            return "object";
        }
    }
}
