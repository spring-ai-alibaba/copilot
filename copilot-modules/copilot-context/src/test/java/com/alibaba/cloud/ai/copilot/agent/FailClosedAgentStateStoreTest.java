package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
