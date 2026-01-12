package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.stereotype.Component;

/**
 * Hook 执行完成处理器
 * 处理 AGENT_HOOK_FINISHED 类型的输出
 */
@Component
public class HookFinishedHandler implements OutputTypeHandler {

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_HOOK_FINISHED;
    }

    @Override
    public void handle(StreamingOutput output) {
        // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
        System.out.println("Hook 执行完成: " + output.node());

        // 这里可以添加更复杂的业务逻辑
        // 比如记录Hook执行状态、触发后续操作等
    }
}