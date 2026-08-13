package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScopeShutdownRegistryTest {

    @Test
    void removesOnlyTheCompletedAgentsRegistryEntry() {
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agent.getAgentId()).thenReturn("completed-agent");
        GracefulShutdownManager.getInstance().bindStateSaver(agent, ignored -> {
        });

        assertTrue(AgentScopeShutdownRegistry.unregister(agent));
        assertFalse(AgentScopeShutdownRegistry.unregister(agent));
    }
}
