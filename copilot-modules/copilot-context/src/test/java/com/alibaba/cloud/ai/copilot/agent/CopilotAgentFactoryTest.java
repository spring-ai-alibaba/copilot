package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotAgentFactoryTest {

    @Test
    void usesConversationIdAsAgentDefaultSession() {
        DynamicModelService dynamicModelService = mock(DynamicModelService.class);
        Model model = mock(Model.class);
        AgentStateStore stateStore = mock(AgentStateStore.class);
        when(dynamicModelService.getChatModelWithConfigId("model-config"))
                .thenReturn(model);

        CopilotAgentFactory factory = new CopilotAgentFactory(
                dynamicModelService,
                new AppProperties());

        HarnessAgent agent = factory.buildAgent(
                "model-config", stateStore, "conversation-42");
        try {
            assertEquals("conversation-42", agent.getDefaultSessionId());
            assertSame(stateStore, agent.getStateStore());
        } finally {
            AgentScopeShutdownRegistry.unregister(agent);
            agent.close();
        }
    }
}
