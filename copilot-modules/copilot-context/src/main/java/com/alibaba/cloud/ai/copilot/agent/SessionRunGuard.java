package com.alibaba.cloud.ai.copilot.agent;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Ensures that only one run mutates a user's conversation state at a time.
 *
 * <p>The in-memory map rejects duplicate work cheaply inside one JVM. A short-lived lease row in
 * the AgentScope MySQL table provides the same exclusion across application replicas. A watchdog
 * renews the short lease while the operation is healthy, with a hard ceiling derived from the run
 * timeout, so a crashed or stuck process cannot leave a conversation locked forever.</p>
 */
@Component
@Slf4j
public class SessionRunGuard {

    private static final String TABLE_NAME = "agentscope_sessions";
    private static final String LOCK_STATE_KEY = "__session_run_lock__";
    private static final String LOCK_SESSION_PREFIX = "__run_lock__:";
    private static final int LOCK_ITEM_INDEX = 0;
    private static final long LEASE_TTL_SECONDS = 60;
    private static final long MAXIMUM_HOLD_GRACE_SECONDS = 30;
    private static final int RENEWAL_THREADS = 4;
    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 5;
    private static final long MINIMUM_RENEWAL_INTERVAL_MILLIS = 1_000;
    private static final long MAXIMUM_RENEWAL_INTERVAL_MILLIS = 10_000;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    private final ConcurrentHashMap<SessionKey, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final String qualifiedTableName;
    private final long leaseMillis;
    private final long localLeaseNanos;
    private final long maximumHoldNanos;
    private final long renewalIntervalMillis;
    private final ScheduledExecutorService renewalExecutor;
    private final ScheduledExecutorService leaseTimerExecutor;

    public SessionRunGuard(
            DataSource dataSource,
            @Value("${app.conversation.run-timeout-seconds:300}") long runTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.qualifiedTableName = qualifyTable(resolveCatalog(dataSource));
        if (runTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("runTimeoutSeconds must be positive");
        }
        this.leaseMillis = TimeUnit.SECONDS.toMillis(LEASE_TTL_SECONDS);
        this.localLeaseNanos = TimeUnit.MILLISECONDS.toNanos(leaseMillis);
        this.maximumHoldNanos = TimeUnit.SECONDS.toNanos(
                Math.addExact(runTimeoutSeconds, MAXIMUM_HOLD_GRACE_SECONDS));
        this.renewalIntervalMillis = Math.max(
                MINIMUM_RENEWAL_INTERVAL_MILLIS,
                Math.min(MAXIMUM_RENEWAL_INTERVAL_MILLIS, leaseMillis / 3));
        this.renewalExecutor = Executors.newScheduledThreadPool(RENEWAL_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "session-run-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
        this.leaseTimerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "session-run-lease-expiry");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Lease acquire(String userId, String sessionId, String runId) {
        SessionKey key = new SessionKey(userId, sessionId);
        Objects.requireNonNull(runId, "runId must not be null");
        ActiveRun localRun = reserveLocal(key, runId);

        String ownerToken = UUID.randomUUID().toString();
        try {
            DistributedAcquireResult result = acquireDistributed(key, runId, ownerToken);
            if (result.lease() == null) {
                activeRuns.remove(key, localRun);
                throw new SessionRunConflictException(sessionId, extractRunId(result.stateData()));
            }
            // The database expiry is based on the statement execution time. Anchor the local TTL
            // at or before that point so it can never outlive the committed database lease.
            localRun.renew(result.lease().localAnchorNanos());
            Lease lease = new Lease(key, localRun, result.lease());
            try {
                lease.startRenewal();
                return lease;
            } catch (RuntimeException e) {
                lease.close();
                throw new SessionRunUnavailableException("无法启动会话锁续租", e);
            }
        } catch (SessionRunConflictException e) {
            throw e;
        } catch (RuntimeException e) {
            activeRuns.remove(key, localRun);
            throw e;
        }
    }

    private ActiveRun reserveLocal(SessionKey key, String runId) {
        ActiveRun candidate = new ActiveRun(runId, System.nanoTime());
        while (true) {
            ActiveRun existing = activeRuns.putIfAbsent(key, candidate);
            if (existing == null) {
                return candidate;
            }
            if (!existing.isExpired(System.nanoTime(), localLeaseNanos)) {
                throw new SessionRunConflictException(key.sessionId(), existing.runId());
            }
            if (activeRuns.replace(key, existing, candidate)) {
                return candidate;
            }
        }
    }

    String qualifiedStateTableName() {
        return qualifiedTableName;
    }

    public final class Lease implements AutoCloseable {

        private final SessionKey key;
        private final ActiveRun localRun;
        private final Object distributedMonitor = new Object();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean renewalStopped = new AtomicBoolean();
        private final AtomicBoolean renewalPaused = new AtomicBoolean();
        private final AtomicBoolean ownershipLost = new AtomicBoolean();
        private final AtomicBoolean lossHandlerInvoked = new AtomicBoolean();
        private final AtomicReference<Runnable> ownershipLostHandler = new AtomicReference<>();
        private volatile DistributedLease distributedLease;
        private volatile ScheduledFuture<?> renewalTask;
        private volatile ScheduledFuture<?> expiryTask;

        private Lease(SessionKey key, ActiveRun localRun, DistributedLease distributedLease) {
            this.key = key;
            this.localRun = localRun;
            this.distributedLease = distributedLease;
        }

        private void startRenewal() {
            renewalTask = renewalExecutor.scheduleWithFixedDelay(
                    this::renewSafely,
                    renewalIntervalMillis,
                    renewalIntervalMillis,
                    TimeUnit.MILLISECONDS);
            scheduleExpiryCheck();
        }

        /** Register the cancellation hook for a protected asynchronous operation. */
        public void onOwnershipLost(Runnable handler) {
            Objects.requireNonNull(handler, "handler must not be null");
            if (closed.get()) {
                return;
            }
            if (!ownershipLostHandler.compareAndSet(null, handler)) {
                throw new IllegalStateException("ownership-lost handler is already registered");
            }
            // A synchronous publisher may close the lease between the first check and the CAS.
            // Do not reattach its emitter/subscription closure to an already completed request.
            if (closed.get() && ownershipLostHandler.compareAndSet(handler, null)) {
                return;
            }
            notifyOwnershipLost();
        }

        /** Fail before the next mutation if the database lease has been taken over. */
        public void assertOwned() {
            long nowNanos = System.nanoTime();
            if (localRun.isExpired(nowNanos, localLeaseNanos)
                    || localRun.maximumHoldExceeded(nowNanos, maximumHoldNanos)) {
                markOwnershipLost();
            }
            if (ownershipLost.get() || closed.get()) {
                throw new SessionRunUnavailableException(
                        "会话分布式锁已失效",
                        new IllegalStateException("session lease is no longer owned"));
            }
        }

        void assertScope(String userId, String sessionId) {
            if (!key.userId().equals(userId) || !key.sessionId().equals(sessionId)) {
                throw new SessionRunUnavailableException(
                        "会话租约身份与状态存储作用域不一致",
                        new IllegalStateException("session lease scope mismatch"));
            }
        }

        String scopedUserId() {
            return key.userId();
        }

        String scopedSessionId() {
            return key.sessionId();
        }

        private void renewSafely() {
            if (closed.get() || renewalStopped.get() || renewalPaused.get()
                    || ownershipLost.get()) {
                return;
            }
            if (localRun.maximumHoldExceeded(System.nanoTime(), maximumHoldNanos)) {
                markOwnershipLost();
                return;
            }
            boolean lost = false;
            try {
                synchronized (distributedMonitor) {
                    if (closed.get() || renewalStopped.get() || renewalPaused.get()
                            || ownershipLost.get()) {
                        return;
                    }
                    DistributedLease renewed = renewDistributed(distributedLease);
                    if (renewed == null) {
                        lost = true;
                    } else {
                        distributedLease = renewed;
                        // Be conservative: database expiry was calculated no earlier than the
                        // beginning of this call, never at its later completion time.
                        localRun.renew(renewed.localAnchorNanos());
                        scheduleExpiryCheck();
                    }
                }
            } catch (SessionRunUnavailableException e) {
                // Fail closed. A timeout may have happened after the database committed, but the
                // process can no longer prove ownership and must stop mutating protected state.
                log.error("续租会话分布式锁失败，正在取消受保护操作: sessionId={}",
                        key.sessionId(), e);
                lost = true;
            }
            if (lost) {
                markOwnershipLost();
            }
        }

        private void markOwnershipLost() {
            if (closed.get() || !ownershipLost.compareAndSet(false, true)) {
                return;
            }
            activeRuns.remove(key, localRun);
            ScheduledFuture<?> task = renewalTask;
            if (task != null) {
                task.cancel(false);
            }
            ScheduledFuture<?> expiry = expiryTask;
            if (expiry != null) {
                expiry.cancel(false);
            }
            log.error("会话分布式锁所有权已丢失，正在取消受保护操作: sessionId={}, runId={}",
                    key.sessionId(), localRun.runId());
            notifyOwnershipLost();
        }

        private void notifyOwnershipLost() {
            Runnable handler = ownershipLostHandler.get();
            if (!ownershipLost.get()
                    || handler == null
                    || !lossHandlerInvoked.compareAndSet(false, true)) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    handler.run();
                } catch (RuntimeException e) {
                    log.error("会话分布式锁丢失回调执行失败: sessionId={}",
                            key.sessionId(), e);
                }
            });
        }

        /** Stop the watchdog before potentially blocking resource cleanup. */
        public void stopRenewal() {
            if (renewalStopped.compareAndSet(false, true)) {
                ScheduledFuture<?> task = renewalTask;
                if (task != null) {
                    task.cancel(false);
                }
                ScheduledFuture<?> expiry = expiryTask;
                if (expiry != null) {
                    expiry.cancel(false);
                }
                // Do not join the scheduler thread. This monitor only waits for an in-flight SQL
                // renewal and is reentrant when a lost callback performs cleanup on that thread.
                synchronized (distributedMonitor) {
                    // synchronization barrier
                }
            }
        }

        private boolean pauseRenewalForTransaction() {
            if (closed.get() || renewalStopped.get() || ownershipLost.get()) {
                return false;
            }
            if (!renewalPaused.compareAndSet(false, true)) {
                throw new SessionRunUnavailableException(
                        "同一会话租约不能同时参与多个写事务",
                        new IllegalStateException("session lease is already fenced by a transaction"));
            }
            // Keep the single periodic renewal task installed. While paused it performs a cheap
            // no-op, and after the transaction it resumes without creating duplicate fixed-delay
            // tasks when several fenced transactions complete back-to-back.
            ScheduledFuture<?> expiry = expiryTask;
            if (expiry != null) {
                expiry.cancel(false);
            }
            synchronized (distributedMonitor) {
                // Wait until an in-flight renewal has published its latest exact state_data.
            }
            return true;
        }

        private void resumeRenewalAfterTransaction() {
            if (!renewalPaused.compareAndSet(true, false)
                    || closed.get()
                    || renewalStopped.get()
                    || ownershipLost.get()) {
                return;
            }
            // afterCompletion still runs before Spring releases the transaction-bound JDBC
            // connection. Only clear the logical pause here; doing renewal SQL synchronously can
            // deadlock a saturated pool when every callback waits for a second connection.
            // Re-arm the independent local-expiry watchdog first. The renewal pool may be busy,
            // but the old owner must still stop at its original database-backed expiry boundary.
            scheduleExpiryCheck();
            try {
                renewalExecutor.execute(this::renewAfterTransaction);
            } catch (RuntimeException schedulingError) {
                log.error("无法调度事务后的会话锁续租: sessionId={}",
                        key.sessionId(), schedulingError);
                markOwnershipLost();
            }
        }

        private void renewAfterTransaction() {
            // A following transaction may already have paused this same lease by the time the
            // asynchronous continuation starts. Its own completion will schedule the next resume.
            if (closed.get() || renewalStopped.get() || renewalPaused.get()
                    || ownershipLost.get()) {
                return;
            }
            renewSafely();
        }

        /**
         * Fence a Spring-managed transaction with this exact lease row. The surrounding transaction
         * keeps that lock through commit or rollback, preventing another replica from taking over
         * while its writes are committing.
         */
        public void fenceCurrentTransaction() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException(
                        "fenceCurrentTransaction requires an active Spring transaction");
            }
            boolean resumeAfterCompletion = pauseRenewalForTransaction();
            if (resumeAfterCompletion) {
                try {
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCompletion(int status) {
                                    resumeRenewalAfterTransaction();
                                }
                            });
                } catch (RuntimeException registrationError) {
                    // Without the completion hook we cannot safely prove that renewal resumes
                    // only after the transaction releases its fence row. Fail closed instead.
                    renewalPaused.compareAndSet(true, false);
                    markOwnershipLost();
                    throw registrationError;
                }
            }
            synchronized (distributedMonitor) {
                assertOwned();
                Connection connection = null;
                try {
                    connection = DataSourceUtils.getConnection(dataSource);
                    if (connection.getAutoCommit()) {
                        throw new SQLException(
                                "lease fence connection is not bound to a JDBC transaction");
                    }
                    if (!lockOwnedForUpdate(connection, distributedLease)) {
                        markOwnershipLost();
                        throw lostLease("会话租约已过期或已被接管");
                    }
                } catch (SQLException e) {
                    markOwnershipLost();
                    throw new SessionRunUnavailableException("无法锁定会话租约", e);
                } catch (SessionRunUnavailableException e) {
                    throw e;
                } catch (RuntimeException e) {
                    markOwnershipLost();
                    throw new SessionRunUnavailableException("无法加入会话租约事务", e);
                } finally {
                    if (connection != null) {
                        DataSourceUtils.releaseConnection(connection, dataSource);
                    }
                }
            }
        }

        void executeFencedStateMutation(FencedStateMutation mutation) {
            Objects.requireNonNull(mutation, "mutation must not be null");
            synchronized (distributedMonitor) {
                assertOwned();
                try {
                    inTransaction(connection -> {
                        if (!lockOwnedForUpdate(connection, distributedLease)) {
                            throw new SQLException("session lease is expired or no longer owned");
                        }
                        mutation.execute(connection);
                        return null;
                    });
                } catch (SQLException e) {
                    markOwnershipLost();
                    throw new SessionRunUnavailableException("会话状态写入的租约校验失败", e);
                }
            }
        }

        private void scheduleExpiryCheck() {
            if (closed.get() || renewalStopped.get() || renewalPaused.get()
                    || ownershipLost.get()) {
                return;
            }
            long delayNanos = localRun.nanosUntilInvalid(
                    System.nanoTime(), localLeaseNanos, maximumHoldNanos);
            ScheduledFuture<?> previous = expiryTask;
            if (previous != null) {
                previous.cancel(false);
            }
            expiryTask = leaseTimerExecutor.schedule(
                    this::expireIfStale,
                    Math.max(1, delayNanos),
                    TimeUnit.NANOSECONDS);
        }

        private void expireIfStale() {
            if (closed.get() || renewalStopped.get() || renewalPaused.get()
                    || ownershipLost.get()) {
                return;
            }
            long nowNanos = System.nanoTime();
            if (localRun.isExpired(nowNanos, localLeaseNanos)
                    || localRun.maximumHoldExceeded(nowNanos, maximumHoldNanos)) {
                markOwnershipLost();
            } else {
                scheduleExpiryCheck();
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                ownershipLostHandler.set(null);
                stopRenewal();
                try {
                    try {
                        synchronized (distributedMonitor) {
                            if (!ownershipLost.get()) {
                                releaseDistributed(distributedLease);
                            }
                        }
                    } catch (SessionRunUnavailableException e) {
                        // The protected operation has already completed. Keep the local process
                        // usable and let the database lease expire instead of turning a successful
                        // reset/delete/run into a retryable failure.
                        log.warn("释放会话分布式锁失败，等待租约自动过期: sessionId={}",
                                key.sessionId(), e);
                    }
                } finally {
                    // An expired local reservation may already have been replaced. Remove only
                    // this exact owner so late cleanup cannot unlock a newer run.
                    activeRuns.remove(key, localRun);
                }
            }
        }
    }

    @PreDestroy
    void shutdownRenewalExecutor() {
        renewalExecutor.shutdownNow();
        leaseTimerExecutor.shutdownNow();
    }

    private DistributedAcquireResult acquireDistributed(
            SessionKey key,
            String runId,
            String ownerToken) {
        String lockSessionId = lockSessionId(key);
        String statePrefix = ownerToken + "|" + sanitizeRunId(runId) + "|";
        String nowMillis = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)";
        String sql = "INSERT INTO " + qualifiedTableName
                + " (session_id, state_key, item_index, state_data) "
                + "VALUES (?, ?, ?, CONCAT(?, CAST(" + nowMillis + " + ? AS UNSIGNED))) "
                + "ON DUPLICATE KEY UPDATE state_data = IF("
                + "CAST(SUBSTRING_INDEX(state_data, '|', -1) AS UNSIGNED) <= " + nowMillis
                + ", VALUES(state_data), state_data)";

        try {
            return inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                    statement.setString(1, lockSessionId);
                    statement.setString(2, LOCK_STATE_KEY);
                    statement.setInt(3, LOCK_ITEM_INDEX);
                    statement.setString(4, statePrefix);
                    statement.setLong(5, leaseMillis);
                    long localAnchorNanos = System.nanoTime();
                    statement.executeUpdate();
                    String storedState = readLockState(connection, lockSessionId);
                    DistributedLease lease = storedState != null && storedState.startsWith(statePrefix)
                            ? new DistributedLease(
                                    lockSessionId, statePrefix, storedState, localAnchorNanos)
                            : null;
                    return new DistributedAcquireResult(lease, storedState);
                }
            });
        } catch (SQLException e) {
            throw new SessionRunUnavailableException("无法获取会话分布式锁", e);
        }
    }

    private DistributedLease renewDistributed(DistributedLease lease) {
        String nowMillis = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)";
        String sql = "UPDATE " + qualifiedTableName
                + " SET state_data = CONCAT(?, CAST(" + nowMillis + " + ? AS UNSIGNED))"
                + " WHERE session_id = ? AND state_key = ? AND item_index = ? AND state_data = ?"
                + " AND CAST(SUBSTRING_INDEX(state_data, '|', -1) AS UNSIGNED) > " + nowMillis;
        try {
            return inTransaction(connection -> {
                int updated;
                long localAnchorNanos;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                    statement.setString(1, lease.statePrefix());
                    statement.setLong(2, leaseMillis);
                    statement.setString(3, lease.lockSessionId());
                    statement.setString(4, LOCK_STATE_KEY);
                    statement.setInt(5, LOCK_ITEM_INDEX);
                    statement.setString(6, lease.stateData());
                    localAnchorNanos = System.nanoTime();
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    return null;
                }
                String storedState = readLockState(connection, lease.lockSessionId());
                return storedState != null && storedState.startsWith(lease.statePrefix())
                        ? new DistributedLease(
                                lease.lockSessionId(),
                                lease.statePrefix(),
                                storedState,
                                localAnchorNanos)
                        : null;
            });
        } catch (SQLException e) {
            throw new SessionRunUnavailableException("无法续租会话分布式锁", e);
        }
    }

    private String readLockState(Connection connection, String lockSessionId) throws SQLException {
        String sql = "SELECT state_data FROM " + qualifiedTableName
                + " WHERE session_id = ? AND state_key = ? AND item_index = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.setString(1, lockSessionId);
            statement.setString(2, LOCK_STATE_KEY);
            statement.setInt(3, LOCK_ITEM_INDEX);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private boolean lockOwnedForUpdate(Connection connection, DistributedLease lease)
            throws SQLException {
        String nowMillis = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)";
        String sql = "SELECT 1 FROM " + qualifiedTableName
                + " WHERE session_id = ? AND state_key = ? AND item_index = ?"
                + " AND BINARY state_data = BINARY ?"
                + " AND CAST(SUBSTRING_INDEX(state_data, '|', -1) AS UNSIGNED) > " + nowMillis
                + " FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.setString(1, lease.lockSessionId());
            statement.setString(2, LOCK_STATE_KEY);
            statement.setInt(3, LOCK_ITEM_INDEX);
            statement.setString(4, lease.stateData());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void releaseDistributed(DistributedLease lease) {
        String sql = "DELETE FROM " + qualifiedTableName
                + " WHERE session_id = ? AND state_key = ? AND item_index = ? AND state_data = ?";
        try {
            inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
                    statement.setString(1, lease.lockSessionId());
                    statement.setString(2, LOCK_STATE_KEY);
                    statement.setInt(3, LOCK_ITEM_INDEX);
                    statement.setString(4, lease.stateData());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new SessionRunUnavailableException("无法释放会话分布式锁", e);
        }
    }

    private static String lockSessionId(SessionKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateLengthPrefixed(digest, key.userId());
            updateLengthPrefixed(digest, key.sessionId());
            return LOCK_SESSION_PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String sanitizeRunId(String runId) {
        if (runId.indexOf('|') >= 0) {
            throw new IllegalArgumentException("runId must not contain '|'");
        }
        return runId;
    }

    private static String extractRunId(String stateData) {
        if (stateData == null) {
            return "unknown";
        }
        String[] parts = stateData.split("\\|", 3);
        return parts.length == 3 && !parts[1].isBlank() ? parts[1] : "unknown";
    }

    private static String resolveCatalog(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                throw new IllegalStateException("无法从 DataSource 解析数据库名（catalog 为空）");
            }
            return catalog;
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 SessionRunGuard 失败：无法解析数据库名", e);
        }
    }

    private static String qualifyTable(String catalog) {
        if (!SAFE_IDENTIFIER.matcher(catalog).matches()) {
            throw new IllegalStateException("数据库名包含非法字符: " + catalog);
        }
        return "`" + catalog + "`.`" + TABLE_NAME + "`";
    }

    private <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            if (originalAutoCommit) {
                connection.setAutoCommit(false);
            }
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                if (connection.getAutoCommit() != originalAutoCommit) {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {

        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface FencedStateMutation {

        void execute(Connection connection) throws SQLException;
    }

    private record DistributedAcquireResult(
            DistributedLease lease,
            String stateData) {
    }

    private record DistributedLease(
            String lockSessionId,
            String statePrefix,
            String stateData,
            long localAnchorNanos) {
    }

    private record SessionKey(String userId, String sessionId) {

        private SessionKey {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            if (userId.isBlank() || sessionId.isBlank()) {
                throw new IllegalArgumentException("userId and sessionId must not be blank");
            }
        }
    }

    public static final class SessionRunConflictException extends RuntimeException {

        public SessionRunConflictException(String sessionId, String activeRunId) {
            super("会话正在处理中: sessionId=" + sessionId + ", activeRunId=" + activeRunId);
        }
    }

    private static final class ActiveRun {

        private final String runId;
        private final long acquiredAtNanos;
        private final AtomicLong renewedAtNanos;

        private ActiveRun(String runId, long startedAtNanos) {
            this.runId = runId;
            this.acquiredAtNanos = startedAtNanos;
            this.renewedAtNanos = new AtomicLong(startedAtNanos);
        }

        private String runId() {
            return runId;
        }

        private void renew(long renewalStartedAtNanos) {
            renewedAtNanos.set(renewalStartedAtNanos);
        }

        private boolean isExpired(long nowNanos, long durationNanos) {
            return nowNanos - renewedAtNanos.get() >= durationNanos;
        }

        private boolean maximumHoldExceeded(long nowNanos, long durationNanos) {
            return nowNanos - acquiredAtNanos >= durationNanos;
        }

        private long nanosUntilInvalid(
                long nowNanos,
                long leaseDurationNanos,
                long maximumDurationNanos) {
            long untilLeaseExpiry = leaseDurationNanos - (nowNanos - renewedAtNanos.get());
            long untilMaximumHold = maximumDurationNanos - (nowNanos - acquiredAtNanos);
            return Math.min(untilLeaseExpiry, untilMaximumHold);
        }
    }

    public static final class SessionRunUnavailableException extends RuntimeException {

        public SessionRunUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static SessionRunUnavailableException lostLease(String message) {
        return new SessionRunUnavailableException(
                message,
                new IllegalStateException("session lease is no longer owned"));
    }
}
