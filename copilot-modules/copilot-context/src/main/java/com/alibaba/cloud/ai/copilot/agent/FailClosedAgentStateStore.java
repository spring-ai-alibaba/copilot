package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.domain.state.ConversationStateNamespace;
import io.agentscope.core.message.Msg;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.ListHashUtil;
import io.agentscope.core.state.State;
import io.agentscope.core.state.legacy.ToolkitState;
import io.agentscope.core.util.JsonUtils;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Records state-read failures so the request-scoped Agent hook can fail before
 * invoking a model. AgentScope 2.0.0 otherwise converts a failed read into a
 * fresh state and continues the run.
 */
public final class FailClosedAgentStateStore implements AgentStateStore {

    private static final String ANONYMOUS_USER = "__anon__";
    private static final String HASH_KEY_SUFFIX = ":_hash";
    private static final int SINGLE_STATE_INDEX = 0;
    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 5;

    private final AgentStateStore delegate;
    private final SessionRunGuard sessionRunGuard;
    private final String qualifiedTableName;

    public FailClosedAgentStateStore(AgentStateStore delegate) {
        this(delegate, null);
    }

    public FailClosedAgentStateStore(
            AgentStateStore delegate,
            SessionRunGuard sessionRunGuard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.sessionRunGuard = sessionRunGuard;
        this.qualifiedTableName = sessionRunGuard == null
                ? null
                : sessionRunGuard.qualifiedStateTableName();
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, State state) {
        if (sessionRunGuard == null) {
            delegate.save(userId, sessionId, stateKey, state);
            return;
        }
        throw missingLease();
    }

    public void save(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey,
            State state) {
        if (sessionRunGuard == null) {
            delegate.save(userId, sessionId, stateKey, state);
            return;
        }
        saveFenced(Objects.requireNonNull(lease, "lease must not be null"),
                userId, sessionId, stateKey, state);
    }

    @Override
    public void save(String userId, String sessionId, String stateKey, List<? extends State> states) {
        if (sessionRunGuard == null) {
            delegate.save(userId, sessionId, stateKey, states);
            return;
        }
        throw missingLease();
    }

    public void save(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey,
            List<? extends State> states) {
        if (sessionRunGuard == null) {
            delegate.save(userId, sessionId, stateKey, states);
            return;
        }
        saveListFenced(Objects.requireNonNull(lease, "lease must not be null"),
                userId, sessionId, stateKey, states);
    }

    @Override
    public <T extends State> Optional<T> get(
            String userId, String sessionId, String stateKey, Class<T> stateClass) {
        try {
            return delegate.get(userId, sessionId, stateKey, stateClass);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, sessionId, e);
        }
    }

    @Override
    public <T extends State> List<T> getList(
            String userId, String sessionId, String stateKey, Class<T> stateClass) {
        try {
            return delegate.getList(userId, sessionId, stateKey, stateClass);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, sessionId, e);
        }
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        try {
            return delegate.exists(userId, sessionId);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, sessionId, e);
        }
    }

    @Override
    public void delete(String userId, String sessionId) {
        if (sessionRunGuard == null) {
            delegate.delete(userId, sessionId);
            return;
        }
        throw missingLease();
    }

    public void delete(SessionRunGuard.Lease lease, String userId, String sessionId) {
        if (sessionRunGuard == null) {
            delegate.delete(userId, sessionId);
            return;
        }
        deleteFenced(Objects.requireNonNull(lease, "lease must not be null"),
                userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String stateKey) {
        if (sessionRunGuard == null) {
            delegate.delete(userId, sessionId, stateKey);
            return;
        }
        throw missingLease();
    }

    public void delete(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey) {
        if (sessionRunGuard == null) {
            delegate.delete(userId, sessionId, stateKey);
            return;
        }
        deleteKeyFenced(
                Objects.requireNonNull(lease, "lease must not be null"),
                userId,
                sessionId,
                stateKey);
    }

    /** Delete several namespaces atomically behind one lease fence. */
    public void deleteAll(SessionRunGuard.Lease lease, List<SessionRef> sessions) {
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(sessions, "sessions must not be null");
        if (sessions.isEmpty()) {
            return;
        }
        if (sessionRunGuard == null) {
            for (SessionRef session : sessions) {
                delegate.delete(session.userId(), session.sessionId());
            }
            return;
        }

        List<String> slotIds = new ArrayList<>(sessions.size());
        for (SessionRef session : sessions) {
            assertMutationScope(lease, session.userId(), session.sessionId());
            slotIds.add(slotId(session.userId(), session.sessionId()));
        }
        executeFenced(lease, connection -> {
            String placeholders = String.join(", ", java.util.Collections.nCopies(
                    slotIds.size(), "?"));
            String sql = "DELETE FROM " + qualifiedTableName
                    + " WHERE session_id IN (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                for (int index = 0; index < slotIds.size(); index++) {
                    statement.setString(index + 1, slotIds.get(index));
                }
                statement.executeUpdate();
            }
        });
    }

    /** Atomically delete source namespaces and upsert one destination state. */
    public void deleteAllAndSave(
            SessionRunGuard.Lease lease,
            List<SessionRef> sessionsToDelete,
            SessionRef destination,
            String stateKey,
            State state) {
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(sessionsToDelete, "sessionsToDelete must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        validateStateKey(stateKey);
        if (sessionRunGuard == null) {
            // Tests and non-MySQL stores cannot share the JDBC transaction. Save first so a
            // serialization/write failure cannot destroy the only source copy.
            delegate.save(
                    destination.userId(),
                    destination.sessionId(),
                    stateKey,
                    Objects.requireNonNull(state, "state must not be null"));
            for (SessionRef session : sessionsToDelete) {
                delegate.delete(session.userId(), session.sessionId());
            }
            return;
        }

        List<String> sourceSlotIds = new ArrayList<>(sessionsToDelete.size());
        for (SessionRef session : sessionsToDelete) {
            assertMutationScope(lease, session.userId(), session.sessionId());
            sourceSlotIds.add(slotId(session.userId(), session.sessionId()));
        }
        assertMutationScope(lease, destination.userId(), destination.sessionId());
        String destinationSlotId = slotId(destination.userId(), destination.sessionId());
        String json = JsonUtils.getJsonCodec().toJson(
                Objects.requireNonNull(state, "state must not be null"));
        executeFenced(lease, connection -> {
            if (!sourceSlotIds.isEmpty()) {
                String placeholders = String.join(", ", java.util.Collections.nCopies(
                        sourceSlotIds.size(), "?"));
                String deleteSql = "DELETE FROM " + qualifiedTableName
                        + " WHERE session_id IN (" + placeholders + ")";
                try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                    statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                    for (int index = 0; index < sourceSlotIds.size(); index++) {
                        statement.setString(index + 1, sourceSlotIds.get(index));
                    }
                    statement.executeUpdate();
                }
            }
            upsertSingleState(connection, destinationSlotId, stateKey, json);
        });
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        try {
            return delegate.listSessionIds(userId);
        } catch (RuntimeException e) {
            throw new StateReadFailureError(userId, "*", e);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    /** Bind every write performed by an AgentScope request to that request's original lease. */
    public LeaseBoundAgentStateStore bind(
            SessionRunGuard.Lease lease,
            String expectedUserId,
            String expectedSessionId) {
        SessionRunGuard.Lease requiredLease =
                Objects.requireNonNull(lease, "lease must not be null");
        String requiredUserId = requireText(expectedUserId, "expectedUserId");
        String requiredSessionId = requireText(expectedSessionId, "expectedSessionId");
        requiredLease.assertScope(requiredUserId, requiredSessionId);
        return new LeaseBoundAgentStateStore(
                this,
                requiredLease,
                requiredUserId,
                requiredSessionId);
    }

    private void saveFenced(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey,
            State state) {
        assertMutationScope(lease, userId, sessionId);
        String slotId = slotId(userId, sessionId);
        validateStateKey(stateKey);
        String json = JsonUtils.getJsonCodec().toJson(Objects.requireNonNull(state, "state"));
        executeFenced(lease, connection -> {
            upsertSingleState(connection, slotId, stateKey, json);
        });
    }

    private void saveListFenced(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey,
            List<? extends State> states) {
        Objects.requireNonNull(states, "states must not be null");
        assertMutationScope(lease, userId, sessionId);
        if (states.isEmpty()) {
            return;
        }
        String slotId = slotId(userId, sessionId);
        validateStateKey(stateKey);
        String hashKey = stateKey + HASH_KEY_SUFFIX;
        validateStateKey(hashKey);
        List<String> jsonItems = new ArrayList<>(states.size());
        for (State state : states) {
            jsonItems.add(JsonUtils.getJsonCodec().toJson(
                    Objects.requireNonNull(state, "state item")));
        }
        String hash = ListHashUtil.computeHash(states);

        executeFenced(lease, connection -> {
            String deleteSql = "DELETE FROM " + qualifiedTableName
                    + " WHERE session_id = ? AND state_key IN (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                statement.setString(1, slotId);
                statement.setString(2, stateKey);
                statement.setString(3, hashKey);
                statement.executeUpdate();
            }

            String insertSql = "INSERT INTO " + qualifiedTableName
                    + " (session_id, state_key, item_index, state_data) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                for (int index = 0; index < jsonItems.size(); index++) {
                    statement.setString(1, slotId);
                    statement.setString(2, stateKey);
                    statement.setInt(3, index);
                    statement.setString(4, jsonItems.get(index));
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                statement.setString(1, slotId);
                statement.setString(2, hashKey);
                statement.setInt(3, SINGLE_STATE_INDEX);
                statement.setString(4, hash);
                statement.executeUpdate();
            }
        });
    }

    private void deleteFenced(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId) {
        assertMutationScope(lease, userId, sessionId);
        String slotId = slotId(userId, sessionId);
        executeFenced(lease, connection -> {
            String sql = "DELETE FROM " + qualifiedTableName + " WHERE session_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                statement.setString(1, slotId);
                statement.executeUpdate();
            }
        });
    }

    private void deleteKeyFenced(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId,
            String stateKey) {
        assertMutationScope(lease, userId, sessionId);
        String slotId = slotId(userId, sessionId);
        validateStateKey(stateKey);
        String hashKey = stateKey + HASH_KEY_SUFFIX;
        executeFenced(lease, connection -> {
            // A key can represent either a single state or a list. Remove the companion list hash
            // as well so a later save cannot mistake deleted rows for an unchanged list.
            String sql = "DELETE FROM " + qualifiedTableName
                    + " WHERE session_id = ? AND state_key IN (?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                statement.setString(1, slotId);
                statement.setString(2, stateKey);
                statement.setString(3, hashKey);
                statement.executeUpdate();
            }
        });
    }

    private void executeFenced(
            SessionRunGuard.Lease lease,
            SessionRunGuard.FencedStateMutation mutation) {
        Objects.requireNonNull(lease, "lease must not be null")
                .executeFencedStateMutation(mutation);
    }

    private static void assertMutationScope(
            SessionRunGuard.Lease lease,
            String userId,
            String sessionId) {
        String normalizedUser = userId == null || userId.isBlank() ? ANONYMOUS_USER : userId;
        boolean mainNamespace = lease.scopedSessionId().equals(sessionId)
                && (lease.scopedUserId().equals(normalizedUser)
                || ANONYMOUS_USER.equals(normalizedUser));
        boolean metadataNamespace = lease.scopedUserId().equals(normalizedUser)
                && ConversationStateNamespace.contextMetaSessionId(lease.scopedSessionId())
                .equals(sessionId);
        if (!mainNamespace && !metadataNamespace) {
            throw new SessionRunGuard.SessionRunUnavailableException(
                    "会话租约不能修改其他会话的 AgentStateStore namespace",
                    new IllegalStateException(
                            "lease user/session=" + lease.scopedUserId()
                                    + "/" + lease.scopedSessionId()
                                    + ", target=" + normalizedUser + "/" + sessionId));
        }
    }

    private void upsertSingleState(
            java.sql.Connection connection,
            String slotId,
            String stateKey,
            String json) throws java.sql.SQLException {
        String sql = "INSERT INTO " + qualifiedTableName
                + " (session_id, state_key, item_index, state_data) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE state_data = VALUES(state_data)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.setString(1, slotId);
            statement.setString(2, stateKey);
            statement.setInt(3, SINGLE_STATE_INDEX);
            statement.setString(4, json);
            statement.executeUpdate();
        }
    }

    private static String slotId(String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        String normalizedUser = userId == null || userId.isBlank() ? ANONYMOUS_USER : userId;
        String slotId = normalizedUser + ":" + sessionId;
        if (slotId.length() > 255) {
            throw new IllegalArgumentException("AgentStateStore ID cannot exceed 255 characters");
        }
        if (slotId.contains("/") || slotId.contains("\\")) {
            throw new IllegalArgumentException("AgentStateStore ID cannot contain path separators");
        }
        return slotId;
    }

    private static void validateStateKey(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            throw new IllegalArgumentException("stateKey must not be blank");
        }
        if (stateKey.length() > 255) {
            throw new IllegalArgumentException("stateKey cannot exceed 255 characters");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static SessionRunGuard.SessionRunUnavailableException missingLease() {
        return new SessionRunGuard.SessionRunUnavailableException(
                "生产环境中的 AgentStateStore 写入必须绑定原始会话租约",
                new IllegalStateException("state mutation is not bound to a lease"));
    }

    public record SessionRef(String userId, String sessionId) {

        public SessionRef {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
        }
    }

    public static final class LeaseBoundAgentStateStore implements AgentStateStore {

        private static final String LEGACY_MEMORY_MESSAGES_SLOT = "memory_messages";
        private static final String LEGACY_TOOLKIT_ACTIVE_GROUPS_SLOT = "toolkit_activeGroups";

        private volatile Binding binding;
        private volatile AgentState authenticatedAgentState;

        private LeaseBoundAgentStateStore(
                FailClosedAgentStateStore store,
                SessionRunGuard.Lease lease,
                String expectedUserId,
                String expectedSessionId) {
            this.binding = new Binding(store, lease, expectedUserId, expectedSessionId);
        }

        @Override
        public void save(String userId, String sessionId, String stateKey, State state) {
            Binding current = requireBinding();
            assertScope(current, userId, sessionId);
            current.store().save(current.lease(), userId, sessionId, stateKey, state);
        }

        @Override
        public void save(
                String userId,
                String sessionId,
                String stateKey,
                List<? extends State> states) {
            Binding current = requireBinding();
            assertScope(current, userId, sessionId);
            current.store().save(current.lease(), userId, sessionId, stateKey, states);
        }

        @Override
        public <T extends State> Optional<T> get(
                String userId,
                String sessionId,
                String stateKey,
                Class<T> stateClass) {
            return failClosedRead(userId, sessionId, () -> {
                Binding current = requireBinding();
                boolean frameworkAgentStateProbe = isAgentScopeDefaultStateProbe(
                        current, userId, sessionId, stateKey, stateClass);
                boolean frameworkLegacyToolkitProbe = isAgentScopeLegacyToolkitProbe(
                        current, userId, sessionId, stateKey, stateClass);
                boolean frameworkProbe = frameworkAgentStateProbe
                        || frameworkLegacyToolkitProbe;
                String resolvedUserId = frameworkProbe
                        ? current.expectedUserId()
                        : userId;
                assertScope(current, resolvedUserId, sessionId);
                current.lease().assertOwned();
                Optional<T> value = frameworkAgentStateProbe && authenticatedAgentState != null
                        ? Optional.of(stateClass.cast(authenticatedAgentState))
                        : current.store().get(
                                resolvedUserId, sessionId, stateKey, stateClass);
                rememberAuthenticatedAgentState(
                        current, resolvedUserId, sessionId, stateKey, stateClass, value);
                current.lease().assertOwned();
                return value;
            });
        }

        @Override
        public <T extends State> List<T> getList(
                String userId,
                String sessionId,
                String stateKey,
                Class<T> stateClass) {
            return failClosedRead(userId, sessionId, () -> {
                Binding current = requireBinding();
                boolean frameworkLegacyMessagesProbe = isAgentScopeLegacyMessagesProbe(
                        current, userId, sessionId, stateKey, stateClass);
                String resolvedUserId = frameworkLegacyMessagesProbe
                        ? current.expectedUserId()
                        : userId;
                assertScope(current, resolvedUserId, sessionId);
                current.lease().assertOwned();
                List<T> values = current.store().getList(
                        resolvedUserId, sessionId, stateKey, stateClass);
                current.lease().assertOwned();
                return values;
            });
        }

        @Override
        public boolean exists(String userId, String sessionId) {
            return failClosedRead(userId, sessionId, () -> {
                Binding current = requireBinding();
                assertScope(current, userId, sessionId);
                current.lease().assertOwned();
                boolean exists = current.store().exists(userId, sessionId);
                current.lease().assertOwned();
                return exists;
            });
        }

        @Override
        public void delete(String userId, String sessionId) {
            Binding current = requireBinding();
            assertScope(current, userId, sessionId);
            current.store().delete(current.lease(), userId, sessionId);
        }

        @Override
        public void delete(String userId, String sessionId, String stateKey) {
            Binding current = requireBinding();
            assertScope(current, userId, sessionId);
            current.store().delete(current.lease(), userId, sessionId, stateKey);
        }

        @Override
        public Set<String> listSessionIds(String userId) {
            return failClosedRead(userId, "*", () -> {
                Binding current = requireBinding();
                if (!current.expectedUserId().equals(userId)) {
                    throw scopeMismatch(current, userId, "*");
                }
                current.lease().assertOwned();
                Set<String> sessionIds = current.store().listSessionIds(userId);
                current.lease().assertOwned();
                return sessionIds;
            });
        }

        @Override
        public void close() {
            // AgentScope 2.0.0 retains one shutdown saver per request-built Agent for the JVM
            // lifetime. Detach the request graph while leaving the shared store/DataSource open.
            authenticatedAgentState = null;
            binding = null;
        }

        private Binding requireBinding() {
            Binding current = binding;
            if (current == null) {
                throw missingLease();
            }
            return current;
        }

        private <T> T failClosedRead(
                String userId,
                String sessionId,
                Supplier<T> operation) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                // AgentScope 2.0.0 catches Exception while loading state and silently creates a
                // fresh state. Preserve the fail-closed contract for lease/scope failures too.
                throw new StateReadFailureError(userId, sessionId, e);
            }
        }

        private void assertScope(Binding current, String userId, String sessionId) {
            if (!current.expectedUserId().equals(userId)
                    || !current.expectedSessionId().equals(sessionId)) {
                throw scopeMismatch(current, userId, sessionId);
            }
        }

        private boolean isAgentScopeDefaultStateProbe(
                Binding current,
                String userId,
                String sessionId,
                String stateKey,
                Class<?> stateClass) {
            // AgentScope 2.0.0's shutdown recovery check calls getAgentState() without the
            // active RuntimeContext. Its default session is set to this conversation by the
            // factory; map only that read-only agent_state probe back to the authenticated slot.
            return userId == null
                    && current.expectedSessionId().equals(sessionId)
                    && ConversationStateNamespace.AGENT_STATE_SLOT.equals(stateKey)
                    && AgentState.class.equals(stateClass);
        }

        private boolean isAgentScopeLegacyMessagesProbe(
                Binding current,
                String userId,
                String sessionId,
                String stateKey,
                Class<?> stateClass) {
            return userId == null
                    && current.expectedSessionId().equals(sessionId)
                    && LEGACY_MEMORY_MESSAGES_SLOT.equals(stateKey)
                    && Msg.class.equals(stateClass);
        }

        private boolean isAgentScopeLegacyToolkitProbe(
                Binding current,
                String userId,
                String sessionId,
                String stateKey,
                Class<?> stateClass) {
            return userId == null
                    && current.expectedSessionId().equals(sessionId)
                    && LEGACY_TOOLKIT_ACTIVE_GROUPS_SLOT.equals(stateKey)
                    && ToolkitState.class.equals(stateClass);
        }

        private <T extends State> void rememberAuthenticatedAgentState(
                Binding current,
                String userId,
                String sessionId,
                String stateKey,
                Class<T> stateClass,
                Optional<T> value) {
            if (current.expectedUserId().equals(userId)
                    && current.expectedSessionId().equals(sessionId)
                    && ConversationStateNamespace.AGENT_STATE_SLOT.equals(stateKey)
                    && AgentState.class.equals(stateClass)
                    && value.isPresent()) {
                authenticatedAgentState = (AgentState) value.get();
            }
        }

        private SessionRunGuard.SessionRunUnavailableException scopeMismatch(
                Binding current,
                String userId,
                String sessionId) {
            return new SessionRunGuard.SessionRunUnavailableException(
                    "AgentStateStore 请求身份与会话租约不一致",
                    new IllegalStateException(
                            "expected user/session=" + current.expectedUserId()
                                    + "/" + current.expectedSessionId()
                                    + ", actual=" + userId + "/" + sessionId));
        }

        private record Binding(
                FailClosedAgentStateStore store,
                SessionRunGuard.Lease lease,
                String expectedUserId,
                String expectedSessionId) {
        }
    }

    /**
     * AgentScope 2.0.0 catches {@link Exception} around state loading and silently
     * creates a fresh state. An Error is intentionally used as a compatibility
     * escape hatch so a read outage cannot be mistaken for an empty conversation.
     */
    public static final class StateReadFailureError extends Error {

        public StateReadFailureError(String userId, String sessionId, Throwable cause) {
            super("读取 AgentScope 会话上下文失败: userId=" + userId + ", sessionId=" + sessionId, cause);
        }
    }
}
