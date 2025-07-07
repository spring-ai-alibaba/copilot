package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.service.ToolDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调试控制器 - 用于调试工具发现问题
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {
    
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);
    
    private final ApplicationContext applicationContext;
    private final ToolDiscoveryService toolDiscoveryService;
    
    public DebugController(ApplicationContext applicationContext, ToolDiscoveryService toolDiscoveryService) {
        this.applicationContext = applicationContext;
        this.toolDiscoveryService = toolDiscoveryService;
    }
    
    /**
     * 调试Bean扫描
     */
    @GetMapping("/beans")
    public Map<String, Object> debugBeans() {
        List<String> allBeans = new ArrayList<>();
        List<String> componentBeans = new ArrayList<>();
        List<String> toolBeans = new ArrayList<>();
        
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            allBeans.add(beanName);
            
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> clazz = bean.getClass();
                
                // 检查是否有@Component注解
                if (clazz.isAnnotationPresent(Component.class)) {
                    componentBeans.add(beanName + " (" + clazz.getSimpleName() + ")");
                }
                
                // 检查是否有@Tool注解的方法
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        toolBeans.add(beanName + "." + method.getName() + " (" + clazz.getSimpleName() + ")");
                    }
                }
                
            } catch (Exception e) {
                // 忽略无法获取的Bean
            }
        }
        
        return Map.of(
            "totalBeans", allBeans.size(),
            "componentBeans", componentBeans,
            "toolBeans", toolBeans,
            "discoveredTools", toolDiscoveryService.getAllTools().size()
        );
    }
    
    /**
     * 调试工具发现
     */
    @GetMapping("/tools")
    public Map<String, Object> debugTools() {
        logger.info("开始调试工具发现...");
        
        var systemTools = toolDiscoveryService.getSystemTools();
        var mcpTools = toolDiscoveryService.getMcpTools();
        var allTools = toolDiscoveryService.getAllTools();
        
        return Map.of(
            "systemToolsCount", systemTools.size(),
            "mcpToolsCount", mcpTools.size(),
            "allToolsCount", allTools.size(),
            "systemTools", systemTools,
            "mcpTools", mcpTools,
            "allTools", allTools
        );
    }
    
    /**
     * 强制刷新工具发现
     */
    @GetMapping("/refresh")
    public Map<String, Object> refreshTools() {
        logger.info("强制刷新工具发现...");
        
        var tools = toolDiscoveryService.getAllTools();
        
        return Map.of(
            "message", "工具发现已刷新",
            "toolsCount", tools.size(),
            "tools", tools
        );
    }
}
