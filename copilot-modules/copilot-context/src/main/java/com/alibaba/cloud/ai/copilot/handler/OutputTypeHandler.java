package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;

/**
 * 输出类型处理器接口
 */
public interface OutputTypeHandler {

    /**
     * 获取该处理器负责的输出类型
     */
    OutputType getOutputType();

    /**
     * 处理对应类型的流式输出
     */
    void handle(StreamingOutput output);
}