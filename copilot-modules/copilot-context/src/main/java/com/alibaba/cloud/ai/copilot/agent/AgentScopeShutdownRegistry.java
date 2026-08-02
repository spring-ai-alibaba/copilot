package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.shutdown.GracefulShutdownManager;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Compatibility adapter for AgentScope 2.0.0's write-only shutdown-saver registry.
 *
 * <p>The pinned version registers one saver under every newly built Agent UUID but exposes no
 * unregister operation. This application intentionally builds an Agent per chat request, so only
 * replacing the value would still leak one map node and UUID per request. Keep the version-specific
 * reflection isolated here and remove only the exact, no-longer-used Agent id.</p>
 */
public final class AgentScopeShutdownRegistry {

    private static final Field STATE_SAVERS_FIELD = resolveStateSaversField();

    private AgentScopeShutdownRegistry() {
    }

    /** @return whether a saver was registered for and removed from this exact Agent id. */
    public static boolean unregister(Agent agent) {
        if (agent == null) {
            return false;
        }
        String agentId = agent.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        if (STATE_SAVERS_FIELD == null) {
            throw new IllegalStateException(
                    "AgentScope shutdown registry layout is incompatible with version 2.0.0");
        }
        try {
            Object registry = STATE_SAVERS_FIELD.get(GracefulShutdownManager.getInstance());
            if (!(registry instanceof Map<?, ?> stateSavers)) {
                throw new IllegalStateException("AgentScope shutdown registry is not a Map");
            }
            return stateSavers.remove(agentId) != null;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access AgentScope shutdown registry", error);
        }
    }

    private static Field resolveStateSaversField() {
        try {
            Field field = GracefulShutdownManager.class.getDeclaredField("stateSavers");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException error) {
            return null;
        }
    }
}
