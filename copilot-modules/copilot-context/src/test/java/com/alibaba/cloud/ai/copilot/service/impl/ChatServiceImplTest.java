package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.Message;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    private static final long USER_ID = 42L;
    private static final String MODEL_ID = "1";

    @Mock
    private CopilotAgentFactory agentFactory;
    @Mock
    private SseEventService sseEventService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private AppProperties appProperties;
    @Mock
    private KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;
    @Mock
    private ConversationContextService contextService;
    @Mock
    private SessionRunGuard sessionRunGuard;
    @Mock
    private SessionRunGuard.Lease lease;
    @Mock
    private FailClosedAgentStateStore agentStateStore;
    @Mock
    private FailClosedAgentStateStore.LeaseBoundAgentStateStore requestStateStore;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                agentFactory,
                sseEventService,
                conversationService,
                modelConfigMapper,
                appProperties,
                knowledgeAvailabilityChecker,
                contextService,
                sessionRunGuard,
                agentStateStore);
    }

    @Test
    void doesNotCreateConversationWhenAgentBuildFails() {
        arrangeAuthorizedRequest();
        when(agentFactory.buildAgent(eq(MODEL_ID), eq(requestStateStore), anyString()))
                .thenThrow(new IllegalStateException("agent setup failed"));

        ServiceException error = invokeAsLoggedIn(newChatRequest());

        assertEquals(500, error.getCode());
        verify(conversationService, never())
                .createConversation(eq(USER_ID), any(), anyString(), eq(lease));
        verify(requestStateStore).close();
        verify(lease).close();
    }

    @Test
    void mapsUnavailableSessionGuardToServiceUnavailable() {
        ModelConfigEntity model = new ModelConfigEntity();
        model.setEnabled(true);
        model.setVisibility("PUBLIC");
        when(modelConfigMapper.selectById(1L)).thenReturn(model);
        when(sessionRunGuard.acquire(anyString(), anyString(), anyString()))
                .thenThrow(new SessionRunGuard.SessionRunUnavailableException(
                        "lock database unavailable",
                        new IllegalStateException("offline")));

        ServiceException error = invokeAsLoggedIn(newChatRequest());

        assertEquals(503, error.getCode());
        verify(agentFactory, never()).buildAgent(anyString(), any(), anyString());
        verify(conversationService, never())
                .createConversation(eq(USER_ID), any(), anyString(), any());
    }

    @Test
    void closesAgentWhenConversationCreationFails() {
        arrangeAuthorizedRequest();
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agentFactory.buildAgent(eq(MODEL_ID), eq(requestStateStore), anyString()))
                .thenReturn(agent);
        when(conversationService.createConversation(eq(USER_ID), any(), anyString(), eq(lease)))
                .thenThrow(new IllegalStateException("database unavailable"));

        ServiceException error = invokeAsLoggedIn(newChatRequest());

        assertEquals(500, error.getCode());
        verify(requestStateStore).close();
        verify(agent).close();
        verify(lease).close();
        verify(conversationService, never()).deleteConversation(anyString(), eq(USER_ID));
    }

    @Test
    void compensatesPersistedConversationWhenLaterSetupFails() {
        arrangeAuthorizedRequest();
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agentFactory.buildAgent(eq(MODEL_ID), eq(requestStateStore), anyString()))
                .thenReturn(agent);
        when(conversationService.createConversation(
                eq(USER_ID), any(), anyString(), eq(lease)))
                .thenAnswer(invocation -> invocation.<String>getArgument(2));
        doThrow(new IllegalStateException("message insert failed"))
                .when(conversationService)
                .appendUserMessage(
                        anyString(), anyString(), eq(MODEL_ID), eq(USER_ID), eq(lease));

        ServiceException error = invokeAsLoggedIn(newChatRequest());

        assertEquals(500, error.getCode());
        InOrder cleanupOrder = inOrder(agent, lease, conversationService);
        cleanupOrder.verify(agent).close();
        cleanupOrder.verify(lease).close();
        cleanupOrder.verify(conversationService).deleteConversation(anyString(), eq(USER_ID));
    }

    @Test
    void overallTimeoutCancelsEvenWhenSourceKeepsEmitting() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AguiEvent event = new AguiEvent.RunFinished("thread", "run");
        Flux<AguiEvent> endlessEvents = Flux.interval(Duration.ofMillis(5))
                .map(ignored -> event)
                .doOnCancel(() -> cancelled.set(true));

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                ChatServiceImpl.withOverallTimeout(endlessEvents, Duration.ofMillis(50))
                        .blockLast(Duration.ofSeconds(2)));

        assertInstanceOf(TimeoutException.class, Exceptions.unwrap(error));
        assertTrue(cancelled.get());
    }

    @Test
    void persistsAssistantTimelineBeforeSuccessfulRunFinishedIsEmitted() {
        AuthenticatedAgentDelegate requestAgent = mock(AuthenticatedAgentDelegate.class);
        AuthenticatedAgentDelegate.TokenUsageSnapshot usage =
                new AuthenticatedAgentDelegate.TokenUsageSnapshot(1, 2, 0, 3);
        ConversationContextStatus status = new ConversationContextStatus(
                "conversation",
                1,
                ConversationContextStatus.ContextState.ACTIVE,
                2,
                false,
                0,
                new ConversationContextStatus.TokenUsage(1, 2, 0, 3),
                null,
                null);
        when(requestAgent.getTokenUsage()).thenReturn(usage);
        when(contextService.recordSuccessfulRun("conversation", USER_ID, usage, lease))
                .thenReturn(status);
        AtomicBoolean runFailed = new AtomicBoolean();
        AguiEvent.RunFinished finished = new AguiEvent.RunFinished("conversation", "run");

        List<AguiEvent> events = chatService.finalizeSuccessfulRun(
                        finished,
                        "conversation",
                        "run",
                        "assistant reply",
                        "hello",
                        USER_ID,
                        requestAgent,
                        lease,
                        runFailed)
                .collectList()
                .block();

        InOrder order = inOrder(conversationService, contextService);
        order.verify(conversationService).persistSuccessfulAssistantTurn(
                "conversation", "assistant reply", "hello", USER_ID, lease);
        order.verify(contextService).recordSuccessfulRun("conversation", USER_ID, usage, lease);
        assertEquals(finished, events.get(events.size() - 1));
        assertFalse(runFailed.get());
    }

    @Test
    void emitsRunErrorBeforeFinishedWhenAssistantTimelinePersistenceFails() {
        AuthenticatedAgentDelegate requestAgent = mock(AuthenticatedAgentDelegate.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(conversationService)
                .persistSuccessfulAssistantTurn(
                        "conversation", "assistant reply", "hello", USER_ID, lease);
        AtomicBoolean runFailed = new AtomicBoolean();
        AguiEvent.RunFinished finished = new AguiEvent.RunFinished("conversation", "run");

        List<AguiEvent> events = chatService.finalizeSuccessfulRun(
                        finished,
                        "conversation",
                        "run",
                        "assistant reply",
                        "hello",
                        USER_ID,
                        requestAgent,
                        lease,
                        runFailed)
                .collectList()
                .block();

        assertEquals(2, events.size());
        assertInstanceOf(AguiEvent.RunError.class, events.get(0));
        assertEquals(finished, events.get(1));
        assertTrue(runFailed.get());
        verify(contextService, never()).recordSuccessfulRun(anyString(), any(), any(), any());
    }

    @Test
    void metadataLeaseLossAfterTimelineCommitDoesNotMakeRunRetryable() {
        AuthenticatedAgentDelegate requestAgent = mock(AuthenticatedAgentDelegate.class);
        AuthenticatedAgentDelegate.TokenUsageSnapshot usage =
                new AuthenticatedAgentDelegate.TokenUsageSnapshot(1, 2, 0, 3);
        when(requestAgent.getTokenUsage()).thenReturn(usage);
        when(contextService.recordSuccessfulRun("conversation", USER_ID, usage, lease))
                .thenThrow(new SessionRunGuard.SessionRunUnavailableException(
                        "lease expired",
                        new IllegalStateException("lost")));
        AtomicBoolean runFailed = new AtomicBoolean();
        AguiEvent.RunFinished finished = new AguiEvent.RunFinished("conversation", "run");

        List<AguiEvent> events = chatService.finalizeSuccessfulRun(
                        finished,
                        "conversation",
                        "run",
                        "assistant reply",
                        "hello",
                        USER_ID,
                        requestAgent,
                        lease,
                        runFailed)
                .collectList()
                .block();

        verify(conversationService).persistSuccessfulAssistantTurn(
                "conversation", "assistant reply", "hello", USER_ID, lease);
        assertEquals(List.of(finished), events);
        assertFalse(runFailed.get());
    }

    private void arrangeAuthorizedRequest() {
        ModelConfigEntity model = new ModelConfigEntity();
        model.setEnabled(true);
        model.setVisibility("PUBLIC");
        when(modelConfigMapper.selectById(1L)).thenReturn(model);
        when(sessionRunGuard.acquire(anyString(), anyString(), anyString())).thenReturn(lease);
        when(agentStateStore.bind(eq(lease), anyString(), anyString()))
                .thenReturn(requestStateStore);
    }

    private ServiceException invokeAsLoggedIn(ChatRequest request) {
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(USER_ID);
            return assertThrows(ServiceException.class, () -> chatService.handleBuilderMode(request));
        }
    }

    private ChatRequest newChatRequest() {
        Message message = new Message();
        message.setContent("hello");
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setModelConfigId(MODEL_ID);
        return request;
    }
}
