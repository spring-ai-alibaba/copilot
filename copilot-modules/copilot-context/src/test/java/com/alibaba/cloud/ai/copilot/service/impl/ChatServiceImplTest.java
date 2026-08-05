package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.service.PlanWorkspaceStateService;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.permission.PermissionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatServiceImplTest {

    private ChatServiceImpl newService() {
        return new ChatServiceImpl(
                mock(CopilotAgentFactory.class),
                mock(SseEventService.class),
                mock(ConversationService.class),
                mock(ChatMessageMapper.class),
                mock(AppProperties.class),
                mock(KnowledgeAvailabilityChecker.class),
                mock(PlanWorkspaceStateService.class),
                new PlanReviewSummaryParser());
    }

    @Test
    void treatsPlanExitHumanConfirmationAsExpectedPause() {
        ChatServiceImpl service = newService();
        AguiEvent.RunError planPause = new AguiEvent.RunError(
                "thread",
                "run",
                "Agent is paused for human-in-the-loop confirmation: [plan_exit]",
                "AGENT_PAUSED");
        AguiEvent.RunError realError = new AguiEvent.RunError(
                "thread",
                "run",
                "Provider request failed",
                "MODEL_ERROR");

        assertTrue(service.isExpectedPlanReviewPause(planPause, true));
        assertFalse(service.isExpectedPlanReviewPause(planPause, false));
        assertFalse(service.isExpectedPlanReviewPause(realError, true));
    }

    @Test
    void mapsPlanRiskToExecutionPermission() {
        ChatServiceImpl service = newService();

        ChatServiceImpl.PlanRiskLevel high =
                service.assessRisk("执行数据库 migration，并包含不可逆操作");
        ChatServiceImpl.PlanRiskLevel medium =
                service.assessRisk("调用外部 API，并关注线程安全");
        ChatServiceImpl.PlanRiskLevel low =
                service.assessRisk("修改一个组件并补充单元测试");

        assertEquals(ChatServiceImpl.PlanRiskLevel.HIGH, high);
        assertEquals(PermissionMode.DEFAULT, high.permissionMode());
        assertEquals(ChatServiceImpl.PlanRiskLevel.MEDIUM, medium);
        assertEquals(PermissionMode.DONT_ASK, medium.permissionMode());
        assertEquals(ChatServiceImpl.PlanRiskLevel.LOW, low);
        assertEquals(PermissionMode.BYPASS, low.permissionMode());
    }

    @Test
    void extractsRootAndNestedFilesFromPlan() {
        List<String> files = newService().extractAffectedFiles("""
                涉及文件：`demo.html`、`src/main/App.java:20-40`

                | 文件 | 操作 | 影响范围 |
                |---|---|---|
                | `README.md` | 修改 | 说明 |
                """);

        assertEquals(
                List.of("demo.html", "src/main/App.java:20-40", "README.md"),
                files);
    }

    @Test
    void buildsBoundedFilePreviewsInsideConversationWorkspace(
            @TempDir Path workspace) throws Exception {
        ChatServiceImpl service = newService();
        Path source = workspace.resolve("src/Foo.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                "line 1\nline 2\nline 3\nline 4\nline 5\n");

        List<ChatServiceImpl.PlanFilePreview> previews =
                service.buildFilePreviews(
                        workspace,
                        List.of("src/Foo.java:2-4", "../outside.txt"));

        assertEquals(2, previews.size());
        assertEquals("AVAILABLE", previews.getFirst().status());
        assertEquals(2, previews.getFirst().startLine());
        assertEquals(4, previews.getFirst().endLine());
        assertTrue(previews.getFirst().content().contains("2 | line 2"));
        assertEquals("UNAVAILABLE", previews.get(1).status());
        assertTrue(previews.get(1).content().contains("超出会话工作区"));
    }

    @Test
    void explainsWhenConversationWorkspaceIsNotGitRepository(
            @TempDir Path workspace) {
        assertEquals(
                "当前会话工作区不是 Git 仓库",
                newService().collectGitStatus(workspace));
    }

    @Test
    void returnsVisibleRunErrorWhenUserIdCannotBeResolved() {
        SseEventService sseEventService = mock(SseEventService.class);
        ConversationService conversationService = mock(ConversationService.class);
        ChatServiceImpl service = new ChatServiceImpl(
                mock(CopilotAgentFactory.class),
                sseEventService,
                conversationService,
                mock(ChatMessageMapper.class),
                mock(AppProperties.class),
                mock(KnowledgeAvailabilityChecker.class),
                mock(PlanWorkspaceStateService.class),
                new PlanReviewSummaryParser());
        SseEmitter emitter = new SseEmitter();

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(null);

            service.handleBuilderMode(new ChatRequest(), emitter);
        }

        verifyNoInteractions(conversationService);
        verify(sseEventService).sendRunError(emitter, "登录状态异常，请重新登录后再试");
        verify(sseEventService).sendComplete(emitter);
    }

    @Test
    void rejectsPlanApprovalWithoutConversationId() {
        SseEventService sseEventService = mock(SseEventService.class);
        ConversationService conversationService = mock(ConversationService.class);
        ChatServiceImpl service = new ChatServiceImpl(
                mock(CopilotAgentFactory.class),
                sseEventService,
                conversationService,
                mock(ChatMessageMapper.class),
                mock(AppProperties.class),
                mock(KnowledgeAvailabilityChecker.class),
                mock(PlanWorkspaceStateService.class),
                new PlanReviewSummaryParser());
        SseEmitter emitter = new SseEmitter();
        ChatRequest request = new ChatRequest();
        request.setPlanAction("APPROVE");
        request.setPlanMode(true);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(1L);

            service.handleBuilderMode(request, emitter);
        }

        verifyNoInteractions(conversationService);
        verify(sseEventService).sendRunError(emitter, "审批计划时缺少会话ID");
        verify(sseEventService).sendComplete(emitter);
    }

}
