package com.alibaba.cloud.ai.copilot.mcp.localmcp;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地 MCP 工具实现
 * 使用 @McpTool 注解定义工具，Spring AI 会自动扫描并注册
 *
 * @author Administrator
 */
@Slf4j
@Service
public class LocalMcpTools {

    /**
     * 获取当前时间工具
     */
    @McpTool(name = "get_current_time", description = "获取当前系统时间")
    public String getCurrentTime(
            @McpToolParam(description = "时间格式，可选值：datetime(默认), date, time", required = false) String format) {
        LocalDateTime now = LocalDateTime.now();

        if (format == null || format.isEmpty()) {
            format = "datetime";
        }

        DateTimeFormatter formatter = switch (format.toLowerCase()) {
            case "date" -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
            case "time" -> DateTimeFormatter.ofPattern("HH:mm:ss");
            default -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        };
        log.info("当前时间：{}", now);
        return now.format(formatter);
    }

    /**
     * 计算器工具 - 加法
     */
    @McpTool(name = "calculator_add", description = "计算两个数字的和")
    public Map<String, Object> calculatorAdd(@McpToolParam(description = "第一个数字") Double a,
                                             @McpToolParam(description = "第二个数字") Double b) {
        log.info("计算两个数字的和：{} + {}", a, b);
        log.info("a: {}", a);
        log.info("b: {}", b);
        Map<String, Object> result = new HashMap<>();
        result.put("operation", "add");
        result.put("a", a);
        result.put("b", b);
        result.put("result", a + b);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    /**
     * 计算器工具 - 乘法
     */
    @McpTool(name = "calculator_multiply", description = "计算两个数字的乘积")
    public Map<String, Object> calculatorMultiply(@McpToolParam(description = "第一个数字") Double a,
                                                  @McpToolParam(description = "第二个数字") Double b) {
        log.info("计算两个数字的乘积：{} * {}", a, b);
        Map<String, Object> result = new HashMap<>();
        result.put("operation", "multiply");
        result.put("a", a);
        result.put("b", b);
        result.put("result", a * b);
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    /**
     * 字符串处理工具 - 反转字符串
     */
    @McpTool(name = "string_reverse", description = "反转字符串")
    public Map<String, Object> stringReverse(@McpToolParam(description = "要反转的字符串") String text) {
        log.info("反转字符串：{}", text);
        Map<String, Object> result = new HashMap<>();
        result.put("original", text);
        result.put("reversed", new StringBuilder(text).reverse().toString());
        result.put("length", text.length());
        return result;
    }

    /**
     * 字符串处理工具 - 转大写
     */
    @McpTool(name = "string_uppercase", description = "将字符串转换为大写")
    public Map<String, Object> stringUppercase(@McpToolParam(description = "要转换的字符串") String text) {
        log.info("将字符串转换为大写：{}", text);
        Map<String, Object> result = new HashMap<>();
        result.put("original", text);
        result.put("uppercase", text.toUpperCase());
        result.put("length", text.length());
        return result;
    }
}

