import { create } from "zustand";
import {
  ContextApiError,
  ContextErrorReason,
  ContextOperation,
  ConversationContextStatus,
  TokenUsage,
  getConversationContext,
  isConversationContextStatus,
  isTokenUsage,
  resetConversationContext,
} from "@/api/context";

export interface ContextErrorState {
  operation: ContextOperation;
  reason: ContextErrorReason;
  status?: number;
  code?: number;
}

interface ContextStore {
  statuses: Record<string, ConversationContextStatus>;
  errors: Record<string, ContextErrorState | undefined>;
  loadingByConversation: Record<string, boolean | undefined>;
  resettingByConversation: Record<string, boolean | undefined>;
  runningByConversation: Record<string, number | undefined>;
  loadRequestIds: Record<string, number | undefined>;
  resetRequestIds: Record<string, number | undefined>;
  pendingTokenUsage: Record<string, TokenUsage | undefined>;
  load: (conversationId: string) => Promise<ConversationContextStatus | null>;
  reset: (conversationId: string) => Promise<ConversationContextStatus>;
  setRunning: (conversationId: string | null, running: boolean) => void;
  updateFromEvent: (threadId: string, name: string, value: unknown) => void;
  clear: (conversationId: string) => void;
  clearAll: () => void;
}

export const isAtLeastAsFresh = (
  incoming: ConversationContextStatus,
  current?: ConversationContextStatus,
) => {
  if (!current) return true;
  if (incoming.revision !== current.revision) {
    return incoming.revision > current.revision;
  }
  if (!current.updatedAt) return true;
  if (!incoming.updatedAt) return false;
  const incomingTime = Date.parse(incoming.updatedAt);
  const currentTime = Date.parse(current.updatedAt);
  if (!Number.isNaN(incomingTime) && !Number.isNaN(currentTime)) {
    return incomingTime >= currentTime;
  }
  return incoming.updatedAt >= current.updatedAt;
};

const toContextError = (
  error: unknown,
  operation: ContextOperation,
): ContextErrorState => {
  if (error instanceof ContextApiError) {
    return {
      operation,
      reason: error.reason,
      status: error.status,
      code: error.code,
    };
  }
  return { operation, reason: "unavailable" };
};

const clearRecordValue = <T>(record: Record<string, T>, key: string) => {
  const next = { ...record };
  delete next[key];
  return next;
};

export const useContextStore = create<ContextStore>((set) => {
  let requestSequence = 0;

  return {
    statuses: {},
    errors: {},
    loadingByConversation: {},
    resettingByConversation: {},
    runningByConversation: {},
    loadRequestIds: {},
    resetRequestIds: {},
    pendingTokenUsage: {},

    load: async (conversationId) => {
      const requestId = ++requestSequence;
      set((state) => ({
        loadingByConversation: {
          ...state.loadingByConversation,
          [conversationId]: true,
        },
        errors: { ...state.errors, [conversationId]: undefined },
        loadRequestIds: { ...state.loadRequestIds, [conversationId]: requestId },
      }));

      try {
        const status = await getConversationContext(conversationId);
        set((state) => {
          if (state.loadRequestIds[conversationId] !== requestId) return state;
          const current = state.statuses[conversationId];
          const nextStatuses = isAtLeastAsFresh(status, current)
            ? { ...state.statuses, [conversationId]: status }
            : state.statuses;
          return {
            statuses: nextStatuses,
            loadingByConversation: clearRecordValue(
              state.loadingByConversation,
              conversationId,
            ),
          };
        });
        return status;
      } catch (error) {
        set((state) => {
          if (state.loadRequestIds[conversationId] !== requestId) return state;
          return {
            loadingByConversation: clearRecordValue(
              state.loadingByConversation,
              conversationId,
            ),
            errors: {
              ...state.errors,
              [conversationId]: toContextError(error, "load"),
            },
          };
        });
        return null;
      }
    },

    reset: async (conversationId) => {
      const requestId = ++requestSequence;
      set((state) => ({
        resettingByConversation: {
          ...state.resettingByConversation,
          [conversationId]: true,
        },
        errors: { ...state.errors, [conversationId]: undefined },
        loadingByConversation: clearRecordValue(
          state.loadingByConversation,
          conversationId,
        ),
        // A reset invalidates any GET that was started before it.
        loadRequestIds: { ...state.loadRequestIds, [conversationId]: requestId },
        resetRequestIds: { ...state.resetRequestIds, [conversationId]: requestId },
      }));

      try {
        const status = await resetConversationContext(conversationId);
        set((state) => {
          if (state.resetRequestIds[conversationId] !== requestId) return state;
          return {
            statuses: { ...state.statuses, [conversationId]: status },
            resettingByConversation: clearRecordValue(
              state.resettingByConversation,
              conversationId,
            ),
          };
        });
        return status;
      } catch (error) {
        set((state) => {
          if (state.resetRequestIds[conversationId] !== requestId) return state;
          return {
            resettingByConversation: clearRecordValue(
              state.resettingByConversation,
              conversationId,
            ),
            errors: {
              ...state.errors,
              [conversationId]: toContextError(error, "reset"),
            },
          };
        });
        throw error;
      }
    },

    setRunning: (conversationId, running) => {
      if (!conversationId) return;
      set((state) => {
        const currentCount = state.runningByConversation[conversationId] ?? 0;
        const nextCount = running
          ? currentCount + 1
          : Math.max(0, currentCount - 1);
        return {
          runningByConversation: nextCount
            ? { ...state.runningByConversation, [conversationId]: nextCount }
            : clearRecordValue(state.runningByConversation, conversationId),
        };
      });
    },

    updateFromEvent: (threadId, name, value) => {
      set((state) => {
        const current = state.statuses[threadId];
        if (
          name === "context_status" &&
          isConversationContextStatus(value) &&
          value.conversationId === threadId
        ) {
          const pendingUsage = state.pendingTokenUsage[threadId];
          const incoming = pendingUsage && !value.lastRunTokenUsage
            ? { ...value, lastRunTokenUsage: pendingUsage }
            : value;
          if (!isAtLeastAsFresh(incoming, current)) return state;
          return {
            statuses: { ...state.statuses, [threadId]: incoming },
            errors: clearRecordValue(state.errors, threadId),
            loadingByConversation: clearRecordValue(
              state.loadingByConversation,
              threadId,
            ),
            loadRequestIds: clearRecordValue(state.loadRequestIds, threadId),
            pendingTokenUsage: clearRecordValue(
              state.pendingTokenUsage,
              threadId,
            ),
          };
        }
        if (name === "token_usage" && isTokenUsage(value)) {
          if (current) {
            return {
              statuses: {
                ...state.statuses,
                [threadId]: { ...current, lastRunTokenUsage: value },
              },
            };
          }
          return {
            pendingTokenUsage: { ...state.pendingTokenUsage, [threadId]: value },
          };
        }
        return state;
      });
    },

    clear: (conversationId) => {
      set((state) => ({
        statuses: clearRecordValue(state.statuses, conversationId),
        errors: clearRecordValue(state.errors, conversationId),
        loadingByConversation: clearRecordValue(
          state.loadingByConversation,
          conversationId,
        ),
        resettingByConversation: clearRecordValue(
          state.resettingByConversation,
          conversationId,
        ),
        loadRequestIds: clearRecordValue(state.loadRequestIds, conversationId),
        resetRequestIds: clearRecordValue(state.resetRequestIds, conversationId),
        pendingTokenUsage: clearRecordValue(
          state.pendingTokenUsage,
          conversationId,
        ),
        runningByConversation: clearRecordValue(
          state.runningByConversation,
          conversationId,
        ),
      }));
    },

    clearAll: () => {
      set({
        statuses: {},
        errors: {},
        loadingByConversation: {},
        resettingByConversation: {},
        runningByConversation: {},
        loadRequestIds: {},
        resetRequestIds: {},
        pendingTokenUsage: {},
      });
    },
  };
});
