package com.alibaba.cloud.ai.copilot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request-scoped Agent delegate that replaces AG-UI supplied identity with trusted server identity.
 */
public final class AuthenticatedAgentDelegate implements Agent, AutoCloseable {

    private final HarnessAgent delegate;
    private final String userId;
    private final String sessionId;
    private final Map<String, ChatUsage> usageByMessage = new ConcurrentHashMap<>();

    public AuthenticatedAgentDelegate(HarnessAgent delegate, String userId, String sessionId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.userId = requireText(userId, "userId");
        this.sessionId = requireText(sessionId, "sessionId");
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, StreamOptions options, RuntimeContext runtimeContext) {
        RuntimeContext trustedContext = runtimeContext == null
                ? RuntimeContext.builder().userId(userId).sessionId(sessionId).build()
                : RuntimeContext.builder(runtimeContext).userId(userId).sessionId(sessionId).build();
        return delegate.stream(messages, options, trustedContext)
                .doOnNext(this::captureUsage);
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, StreamOptions options) {
        return stream(messages, options, RuntimeContext.empty());
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, StreamOptions options, Class<?> structuredOutputClass) {
        return delegate.stream(messages, options, structuredOutputClass);
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, StreamOptions options, JsonNode structuredOutputSchema) {
        return delegate.stream(messages, options, structuredOutputSchema);
    }

    @Override
    public Mono<Msg> call(List<Msg> messages) {
        return delegate.call(messages);
    }

    @Override
    public Mono<Msg> call(List<Msg> messages, Class<?> structuredOutputClass) {
        return delegate.call(messages, structuredOutputClass);
    }

    @Override
    public Mono<Msg> call(List<Msg> messages, JsonNode structuredOutputSchema) {
        return delegate.call(messages, structuredOutputSchema);
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
        delegate.getDelegate().interrupt(userId, sessionId);
    }

    @Override
    public void interrupt(Msg message) {
        delegate.getDelegate().interrupt(userId, sessionId, message);
    }

    @Override
    public AgentState getAgentState() {
        return delegate.getDelegate().getAgentState(userId, sessionId);
    }

    @Override
    public Toolkit getToolkit() {
        return delegate.getToolkit();
    }

    public TokenUsageSnapshot getTokenUsage() {
        int inputTokens = 0;
        int outputTokens = 0;
        int cachedTokens = 0;
        int totalTokens = 0;
        for (ChatUsage usage : usageByMessage.values()) {
            inputTokens += usage.getInputTokens();
            outputTokens += usage.getOutputTokens();
            cachedTokens += usage.getCachedTokens();
            totalTokens += usage.getTotalTokens();
        }
        return new TokenUsageSnapshot(inputTokens, outputTokens, cachedTokens, totalTokens);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void captureUsage(Event event) {
        Msg message = event.getMessage();
        if (message == null || message.getChatUsage() == null) {
            return;
        }
        String messageKey = message.getId() != null
                ? message.getId()
                : "message@" + System.identityHashCode(message);
        usageByMessage.put(messageKey, message.getChatUsage());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record TokenUsageSnapshot(
            int inputTokens,
            int outputTokens,
            int cachedTokens,
            int totalTokens) {
    }
}
