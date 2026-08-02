package com.alibaba.cloud.ai.copilot.domain.state;

/** Shared AgentScope slot names used by conversation context persistence. */
public final class ConversationStateNamespace {

    public static final String AGENT_STATE_SLOT = "agent_state";
    public static final String CONTEXT_META_SLOT = "context_meta";

    private static final String CONTEXT_META_SESSION_PREFIX = "__context_meta__:";

    private ConversationStateNamespace() {
    }

    /**
     * Metadata lives in a sidecar session so clearing the model context cannot also erase the
     * revision boundary needed by clients to order status updates.
     */
    public static String contextMetaSessionId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        return CONTEXT_META_SESSION_PREFIX + conversationId;
    }
}
