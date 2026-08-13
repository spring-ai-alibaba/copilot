package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.LegacyStateLoader;
import io.agentscope.core.state.legacy.ToolkitState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FailClosedAgentStateStoreTest {

    @Mock
    private AgentStateStore delegate;
    @Mock
    private SessionRunGuard sessionRunGuard;
    @Mock
    private SessionRunGuard.Lease lease;

    private FailClosedAgentStateStore store;

    @BeforeEach
    void setUp() {
        store = new FailClosedAgentStateStore(delegate, sessionRunGuard);
    }

    @Test
    void boundReadEscapesAgentScopeExceptionFallbackWhenLeaseIsLost() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");
        doThrow(new SessionRunGuard.SessionRunUnavailableException(
                "lost", new IllegalStateException("expired")))
                .when(lease).assertOwned();

        assertThrows(
                FailClosedAgentStateStore.StateReadFailureError.class,
                () -> bound.exists("42", "conversation"));
    }

    @Test
    void boundReadFailsClosedAfterRequestStoreIsDetached() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");
        bound.close();

        assertThrows(
                FailClosedAgentStateStore.StateReadFailureError.class,
                () -> bound.exists("42", "conversation"));
    }

    @Test
    void boundReadFailsClosedOnScopeMismatch() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");

        assertThrows(
                FailClosedAgentStateStore.StateReadFailureError.class,
                () -> bound.exists("43", "conversation"));
    }

    @Test
    void boundReadMapsAgentScopeDefaultStateProbeToAuthenticatedUser() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");
        AgentState expectedState = AgentState.builder()
                .userId("42")
                .sessionId("conversation")
                .build();
        when(delegate.get(
                "42",
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class))
                .thenReturn(Optional.of(expectedState));

        AgentState authenticatedState = bound.get(
                "42",
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class).orElseThrow();
        AgentState frameworkProbeState = bound.get(
                null,
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class).orElseThrow();

        assertSame(authenticatedState, frameworkProbeState);
        verify(delegate, times(1)).get(
                "42",
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class);
    }

    @Test
    void boundReadMapsEmptyDefaultProbeLegacyFallbackToAuthenticatedUser() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");
        when(delegate.get(
                "42",
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class))
                .thenReturn(Optional.empty());
        when(delegate.getList(
                "42", "conversation", "memory_messages", Msg.class))
                .thenReturn(List.of());
        when(delegate.get(
                "42", "conversation", "toolkit_activeGroups", ToolkitState.class))
                .thenReturn(Optional.empty());

        assertTrue(bound.get(
                null,
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class).isEmpty());
        AgentState legacyState = LegacyStateLoader.loadFromLegacySession(
                bound, null, "conversation");

        assertNotNull(legacyState);
        verify(delegate).get(
                "42",
                "conversation",
                ConversationStateNamespace.AGENT_STATE_SLOT,
                AgentState.class);
        verify(delegate).getList(
                "42", "conversation", "memory_messages", Msg.class);
        verify(delegate).get(
                "42", "conversation", "toolkit_activeGroups", ToolkitState.class);
    }

    @Test
    void boundReadRejectsAnonymousProbeOutsideAgentStateSlot() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");

        assertThrows(
                FailClosedAgentStateStore.StateReadFailureError.class,
                () -> bound.get(null, "conversation", "other_state", AgentState.class));
        verify(delegate, never()).get(any(), any(), any(), any());
    }

    @Test
    void boundReadRejectsAnonymousProbeForAnotherSession() {
        FailClosedAgentStateStore.LeaseBoundAgentStateStore bound =
                store.bind(lease, "42", "conversation");

        assertThrows(
                FailClosedAgentStateStore.StateReadFailureError.class,
                () -> bound.get(
                        null,
                        "copilot_agent",
                        ConversationStateNamespace.AGENT_STATE_SLOT,
                        AgentState.class));
        verify(delegate, never()).get(any(), any(), any(), any());
    }

    @Test
    void fencedMutationRejectsAnotherConversationBeforeExecutingSql() {
        stubLeaseScope();

        assertThrows(
                SessionRunGuard.SessionRunUnavailableException.class,
                () -> store.delete(lease, "42", "another-conversation"));
        assertThrows(
                SessionRunGuard.SessionRunUnavailableException.class,
                () -> store.delete(lease, "43", "conversation"));
        verify(lease, never()).executeFencedStateMutation(any());
    }

    @Test
    void fencedMutationAllowsLegacyAndMetadataNamespacesForItsConversation() {
        stubLeaseScope();

        store.delete(lease, null, "conversation");
        store.delete(
                lease,
                "42",
                ConversationStateNamespace.contextMetaSessionId("conversation"));
        store.delete(lease, "42", "conversation", "agent_state");

        verify(lease, times(3)).executeFencedStateMutation(any());
    }

    @Test
    void unboundKeyDeleteIsRejectedInProductionMode() {
        assertThrows(
                SessionRunGuard.SessionRunUnavailableException.class,
                () -> store.delete("42", "conversation", "agent_state"));
        verify(lease, never()).executeFencedStateMutation(any());
    }

    private void stubLeaseScope() {
        when(lease.scopedUserId()).thenReturn("42");
        when(lease.scopedSessionId()).thenReturn("conversation");
    }
}
