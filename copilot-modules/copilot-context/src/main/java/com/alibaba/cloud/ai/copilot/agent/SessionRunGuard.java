package com.alibaba.cloud.ai.copilot.agent;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ensures that only one run mutates a user's conversation state at a time. */
@Component
public class SessionRunGuard {

    private final ConcurrentHashMap<SessionKey, String> activeRuns = new ConcurrentHashMap<>();

    public Lease acquire(String userId, String sessionId, String runId) {
        SessionKey key = new SessionKey(userId, sessionId);
        String existingRun = activeRuns.putIfAbsent(key, runId);
        if (existingRun != null) {
            throw new SessionRunConflictException(sessionId, existingRun);
        }
        return new Lease(key, runId);
    }

    public final class Lease implements AutoCloseable {

        private final SessionKey key;
        private final String runId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(SessionKey key, String runId) {
            this.key = key;
            this.runId = runId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                activeRuns.remove(key, runId);
            }
        }
    }

    private record SessionKey(String userId, String sessionId) {

        private SessionKey {
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(sessionId, "sessionId must not be null");
        }
    }

    public static final class SessionRunConflictException extends RuntimeException {

        public SessionRunConflictException(String sessionId, String activeRunId) {
            super("会话正在处理中: sessionId=" + sessionId + ", activeRunId=" + activeRunId);
        }
    }
}
