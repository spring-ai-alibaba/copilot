package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.PlanWorkspaceStateService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    @Test
    void checksConversationOwnershipBeforeReturningPlanWorkspace() {
        ConversationService conversationService = mock(ConversationService.class);
        PlanWorkspaceStateService workspaceService = mock(PlanWorkspaceStateService.class);
        ConversationController controller = new ConversationController(
                conversationService, workspaceService);
        when(workspaceService.getWorkspace("conversation"))
                .thenReturn(new PlanWorkspaceDTO());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            controller.getPlanWorkspace("conversation");
        }

        verify(conversationService).checkConversationPermission("conversation", 7L);
        verify(workspaceService).getWorkspace("conversation");
    }

    @Test
    void doesNotExposeWorkspaceWhenOwnershipCheckFails() {
        ConversationService conversationService = mock(ConversationService.class);
        PlanWorkspaceStateService workspaceService = mock(PlanWorkspaceStateService.class);
        ConversationController controller = new ConversationController(
                conversationService, workspaceService);
        doThrow(new IllegalArgumentException("无权访问该会话"))
                .when(conversationService)
                .checkConversationPermission("conversation", 8L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(8L);
            controller.getPlanWorkspace("conversation");
        }

        verifyNoInteractions(workspaceService);
    }
}
