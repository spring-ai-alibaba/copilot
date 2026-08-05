package com.alibaba.cloud.ai.copilot.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotAgentFactoryTest {

    @Test
    void isolatesAndSanitizesPlanFileByConversation() {
        CopilotAgentFactory factory = new CopilotAgentFactory(null, null, null, null, null);

        Path planFile = factory.resolvePlanFile("../conversation/42");
        Path workspace = Path.of(System.getProperty("user.dir"), "workspace").normalize();

        assertEquals(
                workspace.resolve(
                        "___conversation_42/plans/___conversation_42/PLAN.md"),
                planFile);
        assertEquals(
                workspace.resolve("___conversation_42"),
                factory.resolveConversationWorkspace("../conversation/42"));
        assertFalse(planFile.toString().contains("../"));
    }

    @Test
    void requiresCompleteAndChunkedFileToolCalls() {
        CopilotAgentFactory factory = new CopilotAgentFactory(null, null, null, null, null);

        String prompt = factory.buildSystemPrompt("/workspace", "plans/session", false, false);

        assertTrue(prompt.contains("完整、合法的 JSON 参数"));
        assertTrue(prompt.contains("不得超过 8000 个字符"));
        assertTrue(prompt.contains("禁止使用相同参数原样重试"));
    }

    @Test
    void constrainsPlanShellExplorationToReadOnlyCommands() {
        CopilotAgentFactory factory = new CopilotAgentFactory(null, null, null, null, null);

        String prompt = factory.buildSystemPrompt(
                "/workspace", "plans/session", true, true);

        assertTrue(prompt.contains("只读 Shell 探索"));
        assertTrue(prompt.contains("git status/log/diff/show"));
        assertTrue(prompt.contains("git add/commit/push/reset/checkout"));
        assertTrue(prompt.contains("不得用管道、子 Shell 或脚本包装绕过"));
        assertTrue(prompt.contains("CodeGraph 工具"));
        assertTrue(prompt.contains("存在真实风险的项目必须改为 [x]"));
    }
}
