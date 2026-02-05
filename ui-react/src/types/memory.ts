export type PreferenceCategory =
  | "programming_language"
  | "framework_preference"
  | "tool_preference"
  | "coding_style"
  | "response_style"
  | "language_preference"
  | "other";

export interface PreferenceItem {
  category: PreferenceCategory | string;
  value: string;
  context?: string;
  confidence?: number;
  learnedAt?: string;
  usageCount?: number;
  source?: "auto" | "manual" | "agent" | "post_process" | string;
  enabled?: boolean;
}

export interface UserProfile {
  name?: string;
  language?: string;
  enablePreferenceLearning?: boolean;
  [key: string]: any;
}

export interface MemoryItem {
  namespace: string[];
  key: string;
  value: Record<string, any>;
}

