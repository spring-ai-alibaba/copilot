package com.alibaba.cloud.ai.copilot.domain.dto;

public record ConversationContextStatus(
        String conversationId,
        int revision,
        ContextState state,
        int messageCount,
        boolean summaryPresent,
        int triggerTokens,
        TokenUsage lastRunTokenUsage,
        String resetAt,
        String updatedAt) {

    public enum ContextState {
        EMPTY,
        ACTIVE,
        COMPACTED
    }

    public record TokenUsage(
            int inputTokens,
            int outputTokens,
            int cachedTokens,
            int totalTokens) {
    }
}
