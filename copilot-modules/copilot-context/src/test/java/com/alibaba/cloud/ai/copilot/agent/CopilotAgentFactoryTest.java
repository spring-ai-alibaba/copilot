package com.alibaba.cloud.ai.copilot.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CopilotAgentFactoryTest {

    @Test
    void isolatesAndSanitizesPlanFileByConversation() {
        CopilotAgentFactory factory = new CopilotAgentFactory(null, null, null);

        Path planFile = factory.resolvePlanFile("../conversation/42");
        Path workspace = Path.of(System.getProperty("user.dir"), "workspace").normalize();

        assertEquals(
                workspace.resolve("plans/___conversation_42/PLAN.md"),
                planFile);
        assertFalse(planFile.toString().contains("../"));
    }
}
