package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PlanApprovalAgentTest {

    @Test
    void attachesApprovedPlanExitConfirmationToLatestMessage() {
        ToolUseBlock planExit = pendingPlanExit();
        PlanApprovalAgent agent =
                new PlanApprovalAgent(mock(HarnessAgent.class), planExit, true);
        Msg original = Msg.builder()
                .role(MsgRole.USER)
                .textContent("计划已批准")
                .metadata(Map.of("existing", "value"))
                .build();

        List<Msg> enriched = agent.withConfirmation(List.of(original));

        assertEquals("value", enriched.getFirst().getMetadata().get("existing"));
        ConfirmResult result = confirmation(enriched.getFirst());
        assertTrue(result.isConfirmed());
        assertSame(planExit, result.getToolCall());
    }

    @Test
    void attachesRejectedPlanExitConfirmation() {
        ToolUseBlock planExit = pendingPlanExit();
        PlanApprovalAgent agent =
                new PlanApprovalAgent(mock(HarnessAgent.class), planExit, false);
        Msg original = Msg.builder()
                .role(MsgRole.USER)
                .textContent("请修改计划")
                .build();

        ConfirmResult result = confirmation(
                agent.withConfirmation(List.of(original)).getFirst());

        assertFalse(result.isConfirmed());
        assertSame(planExit, result.getToolCall());
    }

    private ToolUseBlock pendingPlanExit() {
        return new ToolUseBlock(
                "tool-call",
                "plan_exit",
                Map.of("summary", "完成计划"),
                null,
                Map.of(),
                ToolCallState.ASKING);
    }

    @SuppressWarnings("unchecked")
    private ConfirmResult confirmation(Msg message) {
        List<ConfirmResult> results = (List<ConfirmResult>) message
                .getMetadata()
                .get(Msg.METADATA_CONFIRM_RESULTS);
        assertEquals(1, results.size());
        return results.getFirst();
    }
}
