package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 输出处理器注册中心
 * 自动收集所有 OutputTypeHandler 实现类并提供统一的处理入口
 */
@Slf4j
@Component
public class OutputHandlerRegistry {

    private final Map<OutputType, OutputTypeHandler> handlers = new HashMap<>();

    /**
     * 构造函数，Spring 会自动注入所有 OutputTypeHandler 实现类
     */
    public OutputHandlerRegistry(List<OutputTypeHandler> handlerList) {
        handlerList.forEach(handler -> {
            OutputType outputType = handler.getOutputType();
            if (handlers.containsKey(outputType)) {
                log.warn("发现重复的处理器类型: {}, 已存在: {}, 新增: {}",
                    outputType, handlers.get(outputType).getClass().getSimpleName(),
                    handler.getClass().getSimpleName());
            }
            handlers.put(outputType, handler);
            log.debug("注册输出处理器: {} -> {}", outputType, handler.getClass().getSimpleName());
        });

        log.info("输出处理器注册完成，共注册 {} 个处理器", handlers.size());
    }

    /**
     * 处理流式输出
     */
    public void handle(StreamingOutput output) {
        OutputType outputType = output.getOutputType();
        OutputTypeHandler handler = handlers.get(outputType);

        if (handler != null) {
            try {
                handler.handle(output);
            } catch (Exception e) {
                log.error("处理输出类型 {} 时发生异常", outputType, e);
            }
        } else {
            log.warn("未找到处理器，输出类型: {}", outputType);
        }
    }

    /**
     * 获取已注册的处理器数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * 检查是否支持某种输出类型
     */
    public boolean supports(OutputType outputType) {
        return handlers.containsKey(outputType);
    }
}