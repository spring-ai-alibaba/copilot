package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatServiceImplTest {

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
                mock(KnowledgeAvailabilityChecker.class));
        SseEmitter emitter = new SseEmitter();

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(null);

            service.handleBuilderMode(new ChatRequest(), emitter);
        }

        verifyNoInteractions(conversationService);
        verify(sseEventService).sendRunError(emitter, "登录状态异常，请重新登录后再试");
        verify(sseEventService).sendComplete(emitter);
    }
}
