package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationContextMeta;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContextServiceImplTest {

    private static final String CONVERSATION_ID = "conversation-1";
    private static final Long USER_ID = 42L;

    @Test
    void migratesAnonymousStateAndRebindsIdentityAfterOwnershipCheck() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        delegate.save(
                null,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().sessionId(CONVERSATION_ID).build());
        ConversationService conversationService = ownedConversationService(USER_ID);
        ServiceFixture fixture = service(delegate, conversationService);

        fixture.service().getStatus(CONVERSATION_ID, USER_ID);

        Optional<AgentState> migrated = delegate.get(
                String.valueOf(USER_ID),
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class);
        assertTrue(migrated.isPresent());
        assertEquals(String.valueOf(USER_ID), migrated.orElseThrow().getUserId());
        assertEquals(CONVERSATION_ID, migrated.orElseThrow().getSessionId());
        assertFalse(delegate.exists(null, CONVERSATION_ID));
        verify(conversationService, times(2))
                .checkConversationPermission(CONVERSATION_ID, USER_ID);
        verify(fixture.lease()).close();
    }

    @Test
    void neverAdoptsAnonymousStateWithoutAnOwnedConversation() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        delegate.save(
                null,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().sessionId(CONVERSATION_ID).build());
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.getConversation(CONVERSATION_ID)).thenReturn(null);
        ServiceFixture fixture = service(delegate, conversationService);

        fixture.service().assertStoreReadable(CONVERSATION_ID, USER_ID, fixture.lease());

        assertTrue(delegate.exists(null, CONVERSATION_ID));
        assertFalse(delegate.exists(String.valueOf(USER_ID), CONVERSATION_ID));
        verify(conversationService, never())
                .checkConversationPermission(CONVERSATION_ID, USER_ID);
    }

    @Test
    void neverAdoptsAnonymousStateWhenOwnershipCheckFails() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        delegate.save(
                null,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().sessionId(CONVERSATION_ID).build());
        ConversationService conversationService = ownedConversationService(7L);
        doThrow(new IllegalArgumentException("forbidden"))
                .when(conversationService)
                .checkConversationPermission(CONVERSATION_ID, USER_ID);
        ServiceFixture fixture = service(delegate, conversationService);

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().assertStoreReadable(
                        CONVERSATION_ID, USER_ID, fixture.lease()));

        assertTrue(delegate.exists(null, CONVERSATION_ID));
        assertFalse(delegate.exists(String.valueOf(USER_ID), CONVERSATION_ID));
    }

    @Test
    void statusReturnsAuthenticatedSnapshotWithoutMigratingWhenSessionIsBusy() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        delegate.save(
                null,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().sessionId(CONVERSATION_ID).build());
        ServiceFixture fixture = service(delegate, ownedConversationService(USER_ID));
        when(fixture.sessionRunGuard().acquire(anyString(), anyString(), anyString()))
                .thenThrow(new SessionRunGuard.SessionRunConflictException(
                        CONVERSATION_ID, "active-run"));

        ConversationContextStatus status = fixture.service().getStatus(CONVERSATION_ID, USER_ID);

        assertEquals(ConversationContextStatus.ContextState.EMPTY, status.state());
        assertTrue(delegate.exists(null, CONVERSATION_ID));
        assertFalse(delegate.exists(String.valueOf(USER_ID), CONVERSATION_ID));
    }

    @Test
    void resetMapsLeaseStoreOutageToServiceUnavailable() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        ServiceFixture fixture = service(delegate, ownedConversationService(USER_ID));
        when(fixture.sessionRunGuard().acquire(anyString(), anyString(), anyString()))
                .thenThrow(new SessionRunGuard.SessionRunUnavailableException(
                        "database unavailable", new IllegalStateException("offline")));

        ServiceException error = assertThrows(
                ServiceException.class,
                () -> fixture.service().reset(CONVERSATION_ID, USER_ID));

        assertEquals(Integer.valueOf(503), error.getCode());
    }

    @Test
    void resetMetadataFailureRollsBackNamespaceDeletionAndKeepsRevisionRecoverable() {
        FailingMetaStateStore delegate = new FailingMetaStateStore();
        String userKey = String.valueOf(USER_ID);
        delegate.save(
                userKey,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().userId(userKey).sessionId(CONVERSATION_ID).build());
        delegate.save(
                null,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().sessionId(CONVERSATION_ID).build());
        ConversationContextMeta previousMeta = new ConversationContextMeta();
        previousMeta.setRevision(7);
        previousMeta.setUpdatedAt("2026-08-01T00:00:00Z");
        delegate.save(
                userKey,
                ConversationStateNamespace.contextMetaSessionId(CONVERSATION_ID),
                ConversationStateNamespace.CONTEXT_META_SLOT,
                previousMeta);

        ConversationService conversationService = ownedConversationService(USER_ID);
        ServiceFixture fixture = service(delegate, conversationService);
        delegate.failMetaWrites = true;

        assertThrows(
                ServiceException.class,
                () -> fixture.service().reset(CONVERSATION_ID, USER_ID));

        assertTrue(delegate.exists(userKey, CONVERSATION_ID));
        assertTrue(delegate.exists(null, CONVERSATION_ID));
        ConversationContextMeta unchangedMeta = delegate.get(
                        userKey,
                        ConversationStateNamespace.contextMetaSessionId(CONVERSATION_ID),
                        ConversationStateNamespace.CONTEXT_META_SLOT,
                        ConversationContextMeta.class)
                .orElseThrow();
        assertEquals(7, unchangedMeta.getRevision());

        delegate.failMetaWrites = false;
        ConversationContextStatus retried = fixture.service().reset(CONVERSATION_ID, USER_ID);
        assertEquals(8, retried.revision());
        assertEquals(ConversationContextStatus.ContextState.EMPTY, retried.state());
        assertTrue(retried.resetAt() != null && !retried.resetAt().isBlank());
        assertFalse(delegate.exists(userKey, CONVERSATION_ID));
        assertFalse(delegate.exists(null, CONVERSATION_ID));
        verify(fixture.lease(), times(2)).close();
    }

    @Test
    void resetMovesInlineMetadataBeforeDeletingTheMainSession() {
        InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();
        String userKey = String.valueOf(USER_ID);
        delegate.save(
                userKey,
                CONVERSATION_ID,
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.builder().userId(userKey).sessionId(CONVERSATION_ID).build());
        ConversationContextMeta inlineMeta = new ConversationContextMeta();
        inlineMeta.setRevision(11);
        inlineMeta.setUpdatedAt("2026-08-01T00:00:00Z");
        delegate.save(
                userKey,
                CONVERSATION_ID,
                ConversationStateNamespace.CONTEXT_META_SLOT,
                inlineMeta);
        ServiceFixture fixture = service(delegate, ownedConversationService(USER_ID));

        ConversationContextStatus reset = fixture.service().reset(CONVERSATION_ID, USER_ID);

        assertEquals(12, reset.revision());
        assertFalse(delegate.exists(userKey, CONVERSATION_ID));
        ConversationContextMeta sidecar = delegate.get(
                        userKey,
                        ConversationStateNamespace.contextMetaSessionId(CONVERSATION_ID),
                        ConversationStateNamespace.CONTEXT_META_SLOT,
                        ConversationContextMeta.class)
                .orElseThrow();
        assertEquals(12, sidecar.getRevision());
    }

    private static ConversationService ownedConversationService(Long ownerId) {
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.getConversation(CONVERSATION_ID)).thenReturn(
                ConversationDTO.builder()
                        .conversationId(CONVERSATION_ID)
                        .userId(ownerId)
                        .build());
        return conversationService;
    }

    private static ServiceFixture service(
            InMemoryAgentStateStore delegate,
            ConversationService conversationService) {
        SessionRunGuard sessionRunGuard = mock(SessionRunGuard.class);
        SessionRunGuard.Lease lease = mock(SessionRunGuard.Lease.class);
        when(sessionRunGuard.acquire(anyString(), anyString(), anyString())).thenReturn(lease);
        ConversationContextServiceImpl service = new ConversationContextServiceImpl(
                new FailClosedAgentStateStore(delegate),
                conversationService,
                sessionRunGuard,
                new AppProperties());
        return new ServiceFixture(service, sessionRunGuard, lease);
    }

    private record ServiceFixture(
            ConversationContextServiceImpl service,
            SessionRunGuard sessionRunGuard,
            SessionRunGuard.Lease lease) {
    }

    private static final class FailingMetaStateStore extends InMemoryAgentStateStore {

        private boolean failMetaWrites;

        @Override
        public void save(String userId, String sessionId, String key, State value) {
            if (failMetaWrites
                    && sessionId.equals(
                            ConversationStateNamespace.contextMetaSessionId(CONVERSATION_ID))
                    && ConversationStateNamespace.CONTEXT_META_SLOT.equals(key)) {
                throw new IllegalStateException("simulated metadata outage");
            }
            super.save(userId, sessionId, key, value);
        }
    }
}
