package com.alibaba.cloud.ai.copilot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 为恢复中的 Plan Mode 工具调用补充 AgentScope 原生人工确认结果。
 *
 * <p>HarnessAgent 在 plan_exit 请求人工确认后，会把 ASKING 状态的 ToolUseBlock
 * 持久化到会话上下文。恢复执行时必须通过最新用户消息的
 * {@link Msg#METADATA_CONFIRM_RESULTS} 传回确认结果，单独再发送一条“批准”文本
 * 或修改权限规则都不能恢复这个已经暂停的工具调用。</p>
 */
public final class PlanApprovalAgent implements Agent {

    private final HarnessAgent delegate;
    private final ConfirmResult confirmResult;

    public PlanApprovalAgent(
            HarnessAgent delegate,
            ToolUseBlock pendingToolCall,
            boolean confirmed) {
        this.delegate = delegate;
        this.confirmResult = new ConfirmResult(confirmed, pendingToolCall);
    }

    List<Msg> withConfirmation(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        List<Msg> enriched = new ArrayList<>(messages);
        int lastIndex = enriched.size() - 1;
        Msg latest = enriched.get(lastIndex);
        Map<String, Object> metadata = new HashMap<>(latest.getMetadata());
        metadata.put(Msg.METADATA_CONFIRM_RESULTS, List.of(confirmResult));
        enriched.set(
                lastIndex,
                Msg.builder()
                        .id(latest.getId())
                        .name(latest.getName())
                        .role(latest.getRole())
                        .content(latest.getContent())
                        .metadata(metadata)
                        .timestamp(latest.getTimestamp())
                        .usage(latest.getUsage())
                        .build());
        return enriched;
    }

    @Override
    public Mono<Msg> call(List<Msg> messages) {
        return delegate.call(withConfirmation(messages));
    }

    @Override
    public Mono<Msg> call(List<Msg> messages, Class<?> structuredModelClass) {
        return delegate.call(withConfirmation(messages), structuredModelClass);
    }

    @Override
    public Mono<Msg> call(List<Msg> messages, JsonNode structuredModelSchema) {
        return delegate.call(withConfirmation(messages), structuredModelSchema);
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, StreamOptions options) {
        return delegate.stream(withConfirmation(messages), options);
    }

    @Override
    public Flux<Event> stream(
            List<Msg> messages,
            StreamOptions options,
            RuntimeContext runtimeContext) {
        return delegate.stream(withConfirmation(messages), options, runtimeContext);
    }

    @Override
    public Flux<Event> stream(
            List<Msg> messages,
            StreamOptions options,
            Class<?> structuredModelClass) {
        return delegate.stream(withConfirmation(messages), options, structuredModelClass);
    }

    @Override
    public Flux<Event> stream(
            List<Msg> messages,
            StreamOptions options,
            JsonNode structuredModelSchema) {
        return delegate.stream(withConfirmation(messages), options, structuredModelSchema);
    }

    @Override
    public Mono<Void> observe(Msg message) {
        return delegate.observe(message);
    }

    @Override
    public Mono<Void> observe(List<Msg> messages) {
        return delegate.observe(messages);
    }

    @Override
    public String getAgentId() {
        return delegate.getAgentId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void interrupt() {
        delegate.interrupt();
    }

    @Override
    public void interrupt(Msg message) {
        delegate.interrupt(message);
    }

    @Override
    public AgentState getAgentState() {
        return delegate.getAgentState();
    }

    @Override
    public Toolkit getToolkit() {
        return delegate.getToolkit();
    }
}
