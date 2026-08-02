package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate.TokenUsageSnapshot;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore.SessionRef;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus.ContextState;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus.TokenUsage;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationContextMeta;
import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.LegacyStateLoader;
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

    private static final String COMPACTION_SUMMARY_NAME = "__compaction_summary__";

    private final FailClosedAgentStateStore stateStore;
    private final ConversationService conversationService;
    private final SessionRunGuard sessionRunGuard;
    private final AppProperties appProperties;

    @Override
    public ConversationContextStatus getStatus(String conversationId, Long userId) {
        conversationService.checkConversationPermission(conversationId, userId);
        String userKey = String.valueOf(userId);
        SessionSnapshot snapshot = loadAuthenticatedSnapshot(conversationId, userKey);
        if (!sessionExists(null, conversationId)) {
            return toStatus(conversationId, snapshot.agentState(), snapshot.meta());
        }

        SessionRunGuard.Lease lease;
        try {
            lease = sessionRunGuard.acquire(
                    userKey, conversationId, "migrate-" + UUID.randomUUID());
        } catch (SessionRunGuard.SessionRunConflictException e) {
            // Status reads stay available while inference/reset owns the session. The next read or
            // chat preflight will retry migration; this response never writes or deletes state.
            return toStatus(conversationId, snapshot.agentState(), snapshot.meta());
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw storeUnavailable("无法获取会话迁移锁", e);
        }
        try {
            conversationService.checkConversationPermission(conversationId, userId);
            lease.assertOwned();
            snapshot = prepareOwnedSession(conversationId, userKey, lease);
            lease.assertOwned();
            return toStatus(conversationId, snapshot.agentState(), snapshot.meta());
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw storeUnavailable("会话迁移期间锁失效", e);
        } finally {
            lease.close();
        }
    }

    @Override
    public ConversationContextStatus reset(String conversationId, Long userId) {
        conversationService.checkConversationPermission(conversationId, userId);
        String userKey = String.valueOf(userId);
        SessionRunGuard.Lease lease;
        try {
            lease = sessionRunGuard.acquire(
                    userKey, conversationId, "reset-" + UUID.randomUUID());
        } catch (SessionRunGuard.SessionRunConflictException e) {
            throw new ServiceException("会话正在处理中，请稍后重试", 409);
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw storeUnavailable("无法获取上下文重置锁", e);
        }
        try {
            conversationService.checkConversationPermission(conversationId, userId);
            return resetWithLease(conversationId, userKey, lease);
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw storeUnavailable("上下文重置期间会话锁失效", e);
        } finally {
            lease.close();
        }
    }

    private ConversationContextStatus resetWithLease(
            String conversationId,
            String userKey,
            SessionRunGuard.Lease lease) {
        lease.assertOwned();
        Optional<ConversationContextMeta> storedMeta =
                loadMetaForMutation(userKey, conversationId, lease);
        ConversationContextMeta previousMeta = storedMeta.orElseGet(ConversationContextMeta::new);
        boolean authenticatedSessionExists = sessionExists(userKey, conversationId);
        boolean legacySessionExists = sessionExists(null, conversationId);
        if (!authenticatedSessionExists
                && !legacySessionExists
                && storedMeta.isPresent()
                && previousMeta.getResetAt() != null) {
            return toStatus(conversationId, Optional.empty(), previousMeta);
        }

        ConversationContextMeta resetMeta = new ConversationContextMeta();
        resetMeta.setRevision(Math.max(1, previousMeta.getRevision()) + 1);
        resetMeta.setResetAt(Instant.now().toString());
        resetMeta.setUpdatedAt(resetMeta.getResetAt());
        try {
            lease.assertOwned();
            stateStore.deleteAllAndSave(
                    lease,
                    java.util.List.of(
                            new SessionRef(userKey, conversationId),
                            new SessionRef(null, conversationId)),
                    new SessionRef(
                            userKey,
                            ConversationStateNamespace.contextMetaSessionId(conversationId)),
                    ConversationStateNamespace.CONTEXT_META_SLOT,
                    resetMeta);
            lease.assertOwned();
        } catch (Exception e) {
            // MySQL performs namespace deletion and tombstone upsert in one fenced transaction;
            // either both commit or both roll back, so a retry cannot get stuck on an old resetAt.
            throw storeUnavailable("重置上下文失败", e);
        }
        return toStatus(conversationId, Optional.empty(), resetMeta);
    }

    @Override
    public ConversationContextStatus recordSuccessfulRun(
            String conversationId,
            Long userId,
            TokenUsageSnapshot tokenUsage,
            SessionRunGuard.Lease lease) {
        String userKey = String.valueOf(userId);
        lease.assertOwned();
        ConversationContextMeta meta = loadMetaForMutation(userKey, conversationId, lease)
                .orElseGet(ConversationContextMeta::new);
        lease.assertOwned();
        meta.setRevision(Math.max(1, meta.getRevision()));
        meta.setUpdatedAt(Instant.now().toString());
        meta.setInputTokens(tokenUsage.inputTokens());
        meta.setOutputTokens(tokenUsage.outputTokens());
        meta.setCachedTokens(tokenUsage.cachedTokens());
        meta.setTotalTokens(tokenUsage.totalTokens());
        try {
            lease.assertOwned();
            saveMeta(userKey, conversationId, meta, lease);
            lease.assertOwned();
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw storeUnavailable("保存上下文元数据失败", e);
        }
        ConversationContextStatus status = toStatus(
                conversationId, loadAgentState(userKey, conversationId), meta);
        lease.assertOwned();
        return status;
    }

    @Override
    public void assertStoreReadable(
            String conversationId,
            Long userId,
            SessionRunGuard.Lease lease) {
        String userKey = String.valueOf(userId);
        lease.assertOwned();
        ConversationDTO conversation = conversationService.getConversation(conversationId);
        if (conversation != null) {
            // Anonymous state is considered only after database-backed ownership is confirmed.
            conversationService.checkConversationPermission(conversationId, userId);
            lease.assertOwned();
            prepareOwnedSession(conversationId, userKey, lease);
            lease.assertOwned();
            return;
        }

        // A new conversation has no ownership row yet. Probe only its authenticated namespace;
        // never adopt anonymous data for an unowned identifier.
        loadAgentState(userKey, conversationId);
        loadMeta(userKey, conversationId);
        lease.assertOwned();
    }

    @Override
    public void deleteSession(String conversationId, Long userId) {
        String userKey = String.valueOf(userId);
        SessionRunGuard.Lease lease;
        try {
            conversationService.checkConversationPermission(conversationId, userId);
            lease = sessionRunGuard.acquire(
                    userKey, conversationId, "delete-state-" + UUID.randomUUID());
        } catch (SessionRunGuard.SessionRunConflictException e) {
            throw new ServiceException("会话正在处理中，请稍后重试", 409);
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw storeUnavailable("无法获取会话上下文删除锁", e);
        }
        try {
            conversationService.checkConversationPermission(conversationId, userId);
            stateStore.deleteAll(lease, java.util.List.of(
                    new SessionRef(userKey, conversationId),
                    new SessionRef(null, conversationId),
                    new SessionRef(
                            userKey,
                            ConversationStateNamespace.contextMetaSessionId(conversationId))));
        } catch (Exception e) {
            throw storeUnavailable("删除会话上下文失败", e);
        } finally {
            lease.close();
        }
    }

    /**
     * Loads the authenticated state and performs a one-time migration from the anonymous namespace.
     * Every caller must confirm conversation ownership and hold the session lease before entering
     * this method. The lease covers both the destination write and source deletion.
     */
    private SessionSnapshot prepareOwnedSession(
            String conversationId,
            String userKey,
            SessionRunGuard.Lease lease) {
        lease.assertOwned();
        Optional<ConversationContextMeta> storedMeta =
                loadMetaForMutation(userKey, conversationId, lease);
        ConversationContextMeta meta = storedMeta.orElseGet(ConversationContextMeta::new);
        Optional<AgentState> authenticatedState = loadAgentState(userKey, conversationId);
        lease.assertOwned();
        if (!sessionExists(null, conversationId)) {
            return new SessionSnapshot(authenticatedState, meta);
        }

        // Authenticated state or metadata marks the new namespace as authoritative. This prevents
        // a stale anonymous copy from resurrecting context after reset or a later authenticated run.
        if (authenticatedState.isPresent() || storedMeta.isPresent()) {
            lease.assertOwned();
            deleteLegacyAnonymousSession(conversationId, lease);
            lease.assertOwned();
            return new SessionSnapshot(authenticatedState, meta);
        }

        Optional<AgentState> legacyState = loadLegacyAnonymousState(conversationId);
        if (legacyState.isEmpty()) {
            lease.assertOwned();
            deleteLegacyAnonymousSession(conversationId, lease);
            lease.assertOwned();
            return new SessionSnapshot(Optional.empty(), meta);
        }

        AgentState migratedState = rebindIdentity(legacyState.get(), userKey, conversationId);
        try {
            lease.assertOwned();
            stateStore.deleteAllAndSave(
                    lease,
                    java.util.List.of(new SessionRef(null, conversationId)),
                    new SessionRef(userKey, conversationId),
                    ConversationStateNamespace.AGENT_STATE_SLOT,
                    migratedState);
            lease.assertOwned();
        } catch (Exception e) {
            throw storeUnavailable("迁移旧会话上下文失败", e);
        }

        // Verify the destination produced by the atomic source-delete/destination-save operation.
        Optional<AgentState> persistedState = loadAgentState(userKey, conversationId);
        if (persistedState.isEmpty()) {
            throw storeUnavailable(
                    "迁移旧会话上下文失败",
                    new IllegalStateException("migrated state is missing from destination"));
        }
        lease.assertOwned();
        return new SessionSnapshot(persistedState, meta);
    }

    private SessionSnapshot loadAuthenticatedSnapshot(String conversationId, String userKey) {
        ConversationContextMeta meta = loadMeta(userKey, conversationId)
                .orElseGet(ConversationContextMeta::new);
        return new SessionSnapshot(loadAgentState(userKey, conversationId), meta);
    }

    private Optional<AgentState> loadAgentState(String userId, String conversationId) {
        try {
            return stateStore.get(
                    userId,
                    conversationId,
                    ConversationStateNamespace.AGENT_STATE_SLOT,
                    AgentState.class);
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取上下文失败", e);
        }
    }

    private Optional<ConversationContextMeta> loadMeta(String userId, String conversationId) {
        String metaSessionId = ConversationStateNamespace.contextMetaSessionId(conversationId);
        Optional<ConversationContextMeta> sidecarMeta = readMeta(userId, metaSessionId);
        if (sidecarMeta.isPresent()) {
            return sidecarMeta;
        }

        return readMeta(userId, conversationId);
    }

    private Optional<ConversationContextMeta> loadMetaForMutation(
            String userId,
            String conversationId,
            SessionRunGuard.Lease lease) {
        String metaSessionId = ConversationStateNamespace.contextMetaSessionId(conversationId);
        Optional<ConversationContextMeta> sidecarMeta = readMeta(userId, metaSessionId);
        if (sidecarMeta.isPresent()) {
            return sidecarMeta;
        }

        // Upgrade metadata written inline by the first context-status implementation. It must be
        // copied before a reset can delete the main session and its revision base.
        Optional<ConversationContextMeta> inlineMeta = readMeta(userId, conversationId);
        if (inlineMeta.isPresent()) {
            try {
                saveMeta(userId, conversationId, inlineMeta.get(), lease);
            } catch (RuntimeException e) {
                throw storeUnavailable("迁移上下文元数据失败", e);
            }
        }
        return inlineMeta;
    }

    private Optional<ConversationContextMeta> readMeta(String userId, String sessionId) {
        try {
            return stateStore.get(
                    userId,
                    sessionId,
                    ConversationStateNamespace.CONTEXT_META_SLOT,
                    ConversationContextMeta.class);
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取上下文元数据失败", e);
        }
    }

    private void saveMeta(
            String userId,
            String conversationId,
            ConversationContextMeta meta,
            SessionRunGuard.Lease lease) {
        stateStore.save(
                lease,
                userId,
                ConversationStateNamespace.contextMetaSessionId(conversationId),
                ConversationStateNamespace.CONTEXT_META_SLOT,
                meta);
    }

    private boolean sessionExists(String userId, String conversationId) {
        try {
            return stateStore.exists(userId, conversationId);
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取上下文失败", e);
        }
    }

    private Optional<AgentState> loadLegacyAnonymousState(String conversationId) {
        Optional<AgentState> currentFormat = loadAgentState(null, conversationId);
        if (currentFormat.isPresent()) {
            return currentFormat;
        }
        try {
            AgentState legacy = LegacyStateLoader.loadFromLegacySession(
                    stateStore, null, conversationId);
            boolean meaningful = !legacy.getContext().isEmpty()
                    || !legacy.getToolContext().getActivatedGroups().isEmpty();
            return meaningful ? Optional.of(legacy) : Optional.empty();
        } catch (FailClosedAgentStateStore.StateReadFailureError | RuntimeException e) {
            throw storeUnavailable("读取旧会话上下文失败", e);
        }
    }

    private void deleteLegacyAnonymousSession(
            String conversationId,
            SessionRunGuard.Lease lease) {
        try {
            stateStore.delete(lease, null, conversationId);
        } catch (RuntimeException e) {
            throw storeUnavailable("清理旧会话上下文失败", e);
        }
    }

    private static AgentState rebindIdentity(
            AgentState source, String userId, String conversationId) {
        return AgentState.builder()
                .sessionId(conversationId)
                .userId(userId)
                .summary(source.getSummary())
                .context(source.getContext())
                .replyId(source.getReplyId())
                .curIter(source.getCurIter())
                .shutdownInterrupted(source.isShutdownInterrupted())
                .permissionContext(source.getPermissionContext())
                .toolContext(source.getToolContext())
                .tasksContext(source.getTasksContext())
                .planModeContext(source.getPlanModeContext())
                .build();
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

    private record SessionSnapshot(
            Optional<AgentState> agentState,
            ConversationContextMeta meta) {
    }
}
