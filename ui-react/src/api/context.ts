import { apiUrl } from "./base";

export type ContextState = "EMPTY" | "ACTIVE" | "COMPACTED";
export type ContextOperation = "load" | "reset";
export type ContextErrorReason =
  | "unauthorized"
  | "forbidden"
  | "notFound"
  | "conflict"
  | "unavailable"
  | "unknown";

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  cachedTokens?: number;
  totalTokens: number;
}

export interface ConversationContextStatus {
  conversationId: string;
  revision: number;
  state: ContextState;
  messageCount: number;
  summaryPresent: boolean;
  triggerTokens: number;
  lastRunTokenUsage?: TokenUsage | null;
  resetAt?: string | null;
  updatedAt?: string | null;
}

const isNonNegativeNumber = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value) && value >= 0;

export const isTokenUsage = (value: unknown): value is TokenUsage => {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<TokenUsage>;
  return (
    isNonNegativeNumber(candidate.inputTokens) &&
    isNonNegativeNumber(candidate.outputTokens) &&
    (candidate.cachedTokens == null ||
      isNonNegativeNumber(candidate.cachedTokens)) &&
    isNonNegativeNumber(candidate.totalTokens)
  );
};

export const isConversationContextStatus = (
  value: unknown,
): value is ConversationContextStatus => {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ConversationContextStatus>;
  return (
    typeof candidate.conversationId === "string" &&
    Number.isInteger(candidate.revision) &&
    isNonNegativeNumber(candidate.revision) &&
    (candidate.state === "EMPTY" ||
      candidate.state === "ACTIVE" ||
      candidate.state === "COMPACTED") &&
    Number.isInteger(candidate.messageCount) &&
    isNonNegativeNumber(candidate.messageCount) &&
    typeof candidate.summaryPresent === "boolean" &&
    isNonNegativeNumber(candidate.triggerTokens) &&
    (candidate.lastRunTokenUsage == null ||
      isTokenUsage(candidate.lastRunTokenUsage)) &&
    (candidate.resetAt == null || typeof candidate.resetAt === "string") &&
    (candidate.updatedAt == null || typeof candidate.updatedAt === "string")
  );
};

const reasonForStatus = (
  status?: number,
  code?: number,
): ContextErrorReason => {
  if (status == null && code == null) return "unavailable";
  const value = status && status >= 300 ? status : code || status;
  if (value === 401) return "unauthorized";
  if (value === 403) return "forbidden";
  if (value === 404) return "notFound";
  if (value === 409) return "conflict";
  if (typeof value === "number" && value >= 500) return "unavailable";
  return "unknown";
};

export class ContextApiError extends Error {
  readonly status?: number;
  readonly code?: number;
  readonly operation: ContextOperation;
  readonly reason: ContextErrorReason;

  constructor(
    message: string,
    status?: number,
    code?: number,
    operation: ContextOperation = "load",
  ) {
    super(message);
    this.name = "ContextApiError";
    this.status = status;
    this.code = code;
    this.operation = operation;
    this.reason = reasonForStatus(status, code);
  }
}

const authHeaders = (): HeadersInit => ({
  Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
});

const parseResponse = async <T>(
  response: Response,
  operation: ContextOperation,
): Promise<T> => {
  let payload: any = null;
  try {
    payload = await response.json();
  } catch {
    // Some gateways return an empty body. The HTTP status still identifies the failure.
  }
  if (!response.ok || payload?.code !== 200) {
    throw new ContextApiError(
      payload?.msg || `CONTEXT_${operation.toUpperCase()}_FAILED`,
      response.status,
      payload?.code,
      operation,
    );
  }
  return payload.data as T;
};

const request = async (
  input: RequestInfo | URL,
  init: RequestInit,
  operation: ContextOperation,
): Promise<Response> => {
  try {
    return await fetch(input, init);
  } catch (error) {
    throw new ContextApiError(
      error instanceof Error ? error.message : "CONTEXT_REQUEST_FAILED",
      undefined,
      undefined,
      operation,
    );
  }
};

const parseContextStatus = async (
  response: Response,
  operation: ContextOperation,
  conversationId: string,
) => {
  const status = await parseResponse<unknown>(response, operation);
  if (
    !isConversationContextStatus(status) ||
    status.conversationId !== conversationId
  ) {
    throw new ContextApiError(
      "CONTEXT_INVALID_RESPONSE",
      response.status,
      undefined,
      operation,
    );
  }
  return status;
};

export const getConversationContext = async (
  conversationId: string,
): Promise<ConversationContextStatus> => {
  const response = await request(
    apiUrl(`/api/chat/conversations/${encodeURIComponent(conversationId)}/context`),
    { headers: authHeaders() },
    "load",
  );
  return parseContextStatus(response, "load", conversationId);
};

export const resetConversationContext = async (
  conversationId: string,
): Promise<ConversationContextStatus> => {
  const response = await request(
    apiUrl(`/api/chat/conversations/${encodeURIComponent(conversationId)}/context`),
    { method: "DELETE", headers: authHeaders() },
    "reset",
  );
  return parseContextStatus(response, "reset", conversationId);
};
