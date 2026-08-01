package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate.TokenUsageSnapshot;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;

public interface ConversationContextService {

    ConversationContextStatus getStatus(String conversationId, Long userId);

    ConversationContextStatus reset(String conversationId, Long userId);

    ConversationContextStatus recordSuccessfulRun(
            String conversationId,
            Long userId,
            TokenUsageSnapshot tokenUsage);

    void assertStoreReadable(String conversationId, Long userId);

    void deleteSession(String conversationId, Long userId);
}
