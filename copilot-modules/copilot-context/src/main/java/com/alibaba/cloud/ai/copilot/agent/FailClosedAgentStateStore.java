package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Records state-read failures so the request-scoped Agent hook can fail before
 * invoking a model. AgentScope 2.0.0 otherwise converts a failed read into a
 * fresh state and continues the run.
 */
public final class FailClosedAgentStateStore implements AgentStateStore {

    private final AgentStateStore delegate;

    public FailClosedAgentStateStore(AgentStateStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, State state) {
        delegate.save(userId, sessionId, stateKey, state);
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, List<? extends State> states) {
        delegate.save(userId, sessionId, stateKey, states);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String stateKey, Class<T> stateClass) {
        try {
            return delegate.get(userId, sessionId, stateKey, stateClass);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, sessionId, e);
        }
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String stateKey, Class<T> stateClass) {
        try {
            return delegate.getList(userId, sessionId, stateKey, stateClass);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, sessionId, e);
        }
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return delegate.exists(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId) {
        delegate.delete(userId, sessionId);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * AgentScope 2.0.0 catches {@link Exception} around state loading and silently
     * creates a fresh state. An Error is intentionally used as a compatibility
     * escape hatch so a read outage cannot be mistaken for an empty conversation.
     */
    public static final class StateReadFailureError extends Error {

        public StateReadFailureError(String userId, String sessionId, Throwable cause) {
            super("读取 AgentScope 会话上下文失败: userId=" + userId + ", sessionId=" + sessionId, cause);
        }
    }
}
