package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate.TokenUsageSnapshot;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;

public interface ConversationContextService {

    ConversationContextStatus getStatus(String conversationId, Long userId);

    ConversationContextStatus reset(String conversationId, Long userId);

    ConversationContextStatus recordSuccessfulRun(
            String conversationId,
            Long userId,
            TokenUsageSnapshot tokenUsage,
            SessionRunGuard.Lease lease);

    /**
     * Probes and prepares context state before a run. The caller must already hold the session
     * run lease so any legacy namespace migration is serialized with reset and deletion.
     */
    void assertStoreReadable(
            String conversationId,
            Long userId,
            SessionRunGuard.Lease lease);

    void deleteSession(String conversationId, Long userId);
}
