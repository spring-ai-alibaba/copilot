import { create } from "zustand";
import type { PreferenceItem, UserProfile } from "@/types/memory";
import {
  getUserPreferences,
  getUserProfile,
  saveUserPreferences,
  saveUserProfile,
} from "@/api/memory";

interface MemoryState {
  userProfile: UserProfile | null;
  userPreferences: PreferenceItem[];

  // 用户级（持久化到 user_profiles.enablePreferenceLearning）
  enablePreferenceLearning: boolean;

  // 会话级（只影响本次会话的使用/学习，前端本地持久化）
  enablePreferencesInChat: boolean;
  enablePreferenceLearningInChat: boolean;

  loading: boolean;
  error: string | null;

  loadUserProfile: (userId: string | number) => Promise<void>;
  loadUserPreferences: (userId: string | number) => Promise<void>;

  updateUserProfile: (userId: string | number, patch: Partial<UserProfile>) => Promise<void>;

  addPreference: (userId: string | number, pref: PreferenceItem) => Promise<void>;
  removePreference: (userId: string | number, category: string, value: string) => Promise<void>;
  togglePreferenceEnabled: (
    userId: string | number,
    category: string,
    value: string,
    enabled: boolean
  ) => Promise<void>;

  setEnablePreferenceLearning: (userId: string | number, enabled: boolean) => Promise<void>;
  setEnablePreferencesInChat: (enabled: boolean) => void;
  setEnablePreferenceLearningInChat: (enabled: boolean) => void;

  clear: () => void;
}

const LS_KEYS = {
  enablePreferencesInChat: "enablePreferencesInChat",
  enablePreferenceLearningInChat: "enablePreferenceLearningInChat",
} as const;

function loadBool(key: string, defaultValue: boolean) {
  if (typeof window === "undefined") return defaultValue;
  const raw = localStorage.getItem(key);
  if (raw == null) return defaultValue;
  return raw === "true";
}

export const useMemoryStore = create<MemoryState>((set, get) => ({
  userProfile: null,
  userPreferences: [],

  enablePreferenceLearning: true,
  // 默认关闭：首次进入不注入偏好，用户需要手动开启（更符合隐私/可控性）
  enablePreferencesInChat: loadBool(LS_KEYS.enablePreferencesInChat, false),
  enablePreferenceLearningInChat: loadBool(LS_KEYS.enablePreferenceLearningInChat, true),

  loading: false,
  error: null,

  loadUserProfile: async (userId) => {
    set({ loading: true, error: null });
    try {
      const profile = await getUserProfile(userId);
      set({
        userProfile: profile,
        enablePreferenceLearning: profile?.enablePreferenceLearning !== false,
        loading: false,
      });
    } catch (e: any) {
      set({ error: e?.message || "loadUserProfile failed", loading: false });
    }
  },

  loadUserPreferences: async (userId) => {
    set({ loading: true, error: null });
    try {
      const prefs = await getUserPreferences(userId);
      set({ userPreferences: prefs, loading: false });
    } catch (e: any) {
      set({ error: e?.message || "loadUserPreferences failed", loading: false });
    }
  },

  updateUserProfile: async (userId, patch) => {
    const current = get().userProfile || {};
    const next = { ...current, ...patch };
    await saveUserProfile(userId, next);
    set({ userProfile: next });
  },

  addPreference: async (userId, pref) => {
    const current = await getUserPreferences(userId);
    const exists = current.some(
      (p) => p.category === pref.category && String(p.value).toLowerCase() === String(pref.value).toLowerCase()
    );
    if (exists) throw new Error("偏好已存在");
    const next = [
      ...current,
      {
        ...pref,
        source: pref.source || "manual",
        learnedAt: pref.learnedAt || new Date().toISOString(),
        usageCount: pref.usageCount ?? 0,
        enabled: pref.enabled ?? true,
      },
    ];
    await saveUserPreferences(userId, next);
    set({ userPreferences: next });
  },

  removePreference: async (userId, category, value) => {
    const current = await getUserPreferences(userId);
    const next = current.filter((p) => !(p.category === category && p.value === value));
    await saveUserPreferences(userId, next);
    set({ userPreferences: next });
  },

  togglePreferenceEnabled: async (userId, category, value, enabled) => {
    const current = await getUserPreferences(userId);
    const next = current.map((p) =>
      p.category === category && p.value === value ? { ...p, enabled } : p
    );
    await saveUserPreferences(userId, next);
    set({ userPreferences: next });
  },

  setEnablePreferenceLearning: async (userId, enabled) => {
    await get().updateUserProfile(userId, { enablePreferenceLearning: enabled });
    set({ enablePreferenceLearning: enabled });
  },

  setEnablePreferencesInChat: (enabled) => {
    set({ enablePreferencesInChat: enabled });
    if (typeof window !== "undefined") {
      localStorage.setItem(LS_KEYS.enablePreferencesInChat, String(enabled));
    }
  },

  setEnablePreferenceLearningInChat: (enabled) => {
    set({ enablePreferenceLearningInChat: enabled });
    if (typeof window !== "undefined") {
      localStorage.setItem(LS_KEYS.enablePreferenceLearningInChat, String(enabled));
    }
  },

  clear: () => {
    set({
      userProfile: null,
      userPreferences: [],
      error: null,
      loading: false,
      enablePreferenceLearning: true,
    });
  },
}));

