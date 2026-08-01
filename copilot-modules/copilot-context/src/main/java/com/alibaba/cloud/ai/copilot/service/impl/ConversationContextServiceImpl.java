package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate.TokenUsageSnapshot;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus.ContextState;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus.TokenUsage;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationContextMeta;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import io.agentscope.core.state.AgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final String AGENT_STATE_SLOT = "agent_state";
    private static final String CONTEXT_META_SLOT = "context_meta";
    private static final String COMPACTION_SUMMARY_NAME = "__compaction_summary__";

    private final FailClosedAgentStateStore stateStore;
    private final ConversationService conversationService;
    private final SessionRunGuard sessionRunGuard;
    private final AppProperties appProperties;

    @Override
    public ConversationContextStatus getStatus(String conversationId, Long userId) {
        conversationService.checkConversationPermission(conversationId, userId);
        return loadStatus(conversationId, userId);
    }

    @Override
    public ConversationContextStatus reset(String conversationId, Long userId) {
        conversationService.checkConversationPermission(conversationId, userId);
        String userKey = String.valueOf(userId);
        SessionRunGuard.Lease lease = sessionRunGuard.acquire(
                userKey, conversationId, "reset-" + UUID.randomUUID());
        try {
            conversationService.checkConversationPermission(conversationId, userId);
            return resetWithLease(conversationId, userKey);
        } finally {
            lease.close();
        }
    }

    private ConversationContextStatus resetWithLease(String conversationId, String userKey) {
        Optional<AgentState> existingState = loadAgentState(userKey, conversationId);
        ConversationContextMeta previousMeta = loadMeta(userKey, conversationId).orElseGet(ConversationContextMeta::new);
        if (existingState.isEmpty() && previousMeta.getResetAt() != null) {
            return toStatus(conversationId, Optional.empty(), previousMeta);
        }

        try {
            stateStore.delete(userKey, conversationId);
            ConversationContextMeta resetMeta = new ConversationContextMeta();
            resetMeta.setRevision(Math.max(1, previousMeta.getRevision()) + 1);
            resetMeta.setResetAt(Instant.now().toString());
            resetMeta.setUpdatedAt(resetMeta.getResetAt());
            try {
                stateStore.save(userKey, conversationId, CONTEXT_META_SLOT, resetMeta);
            } catch (Exception e) {
                // The destructive part already succeeded. Returning a failure would invite
                // the client to retry an operation that has cleared the model context.
                log.error("上下文已重置，但重置元数据保存失败: conversationId={}", conversationId, e);
            }
            return toStatus(conversationId, Optional.empty(), resetMeta);
        } catch (Exception e) {
            throw storeUnavailable("重置上下文失败", e);
        }
    }

    @Override
    public ConversationContextStatus recordSuccessfulRun(
            String conversationId,
            Long userId,
            TokenUsageSnapshot tokenUsage) {
        String userKey = String.valueOf(userId);
        ConversationContextMeta meta = loadMeta(userKey, conversationId).orElseGet(ConversationContextMeta::new);
        meta.setRevision(Math.max(1, meta.getRevision()));
        meta.setUpdatedAt(Instant.now().toString());
        meta.setInputTokens(tokenUsage.inputTokens());
        meta.setOutputTokens(tokenUsage.outputTokens());
        meta.setCachedTokens(tokenUsage.cachedTokens());
        meta.setTotalTokens(tokenUsage.totalTokens());
        try {
            stateStore.save(userKey, conversationId, CONTEXT_META_SLOT, meta);
        } catch (Exception e) {
            throw storeUnavailable("保存上下文元数据失败", e);
        }
        return toStatus(conversationId, loadAgentState(userKey, conversationId), meta);
    }

    @Override
    public void assertStoreReadable(String conversationId, Long userId) {
        String userKey = String.valueOf(userId);
        // Probe every slot that the request can read or write. A single successful
        // agent_state read must not hide a metadata-table outage until after inference.
        loadAgentState(userKey, conversationId);
        loadMeta(userKey, conversationId);
    }

    @Override
    public void deleteSession(String conversationId, Long userId) {
        try {
            stateStore.delete(String.valueOf(userId), conversationId);
        } catch (Exception e) {
            throw storeUnavailable("删除会话上下文失败", e);
        }
    }

    private ConversationContextStatus loadStatus(String conversationId, Long userId) {
        String userKey = String.valueOf(userId);
        Optional<AgentState> agentState = loadAgentState(userKey, conversationId);
        ConversationContextMeta meta = loadMeta(userKey, conversationId).orElseGet(ConversationContextMeta::new);
        return toStatus(conversationId, agentState, meta);
    }

    private Optional<AgentState> loadAgentState(String userId, String conversationId) {
        try {
            return stateStore.get(userId, conversationId, AGENT_STATE_SLOT, AgentState.class);
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取上下文失败", e);
        }
    }

    private Optional<ConversationContextMeta> loadMeta(String userId, String conversationId) {
        try {
            return stateStore.get(userId, conversationId, CONTEXT_META_SLOT, ConversationContextMeta.class);
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取上下文元数据失败", e);
        }
    }

    private ConversationContextStatus toStatus(
            String conversationId,
            Optional<AgentState> agentState,
            ConversationContextMeta meta) {
        int messageCount = agentState.map(state -> state.getContext().size()).orElse(0);
        boolean summaryPresent = agentState.stream()
                .flatMap(state -> state.getContext().stream())
                .anyMatch(message -> COMPACTION_SUMMARY_NAME.equals(message.getName()));
        ContextState state = summaryPresent
                ? ContextState.COMPACTED
                : messageCount > 0 ? ContextState.ACTIVE : ContextState.EMPTY;
        TokenUsage usage = meta.getTotalTokens() == null
                ? null
                : new TokenUsage(
                        valueOrZero(meta.getInputTokens()),
                        valueOrZero(meta.getOutputTokens()),
                        valueOrZero(meta.getCachedTokens()),
                        valueOrZero(meta.getTotalTokens()));
        return new ConversationContextStatus(
                conversationId,
                Math.max(1, meta.getRevision()),
                state,
                messageCount,
                summaryPresent,
                appProperties.getConversation().getSummarization().getMaxTokensBeforeSummary(),
                usage,
                meta.getResetAt(),
                meta.getUpdatedAt());
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private ServiceException storeUnavailable(String message, Throwable cause) {
        log.error(message, cause);
        return new ServiceException(message, 503).setDetailMessage(cause.getMessage());
    }
}
