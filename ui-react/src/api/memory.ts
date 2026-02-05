import { apiUrl } from "./base";
import type { MemoryItem, PreferenceItem, UserProfile } from "@/types/memory";

export interface SaveMemoryRequest {
  namespace: string[];
  key: string;
  value: Record<string, any>;
}

export interface GetMemoryRequest {
  namespace: string[];
  key: string;
}

export interface SearchMemoryRequest {
  namespace: string[];
  filter?: Record<string, any>;
}

export interface DeleteMemoryRequest {
  namespace: string[];
  key: string;
}

export interface MemoryResponse {
  message: string;
  value: any;
  items?: Array<{ namespace: string[]; key: string; value: any }>;
}

function getAuthHeaders() {
  const token = localStorage.getItem("token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}`, satoken: token } : {}),
  };
}

export async function saveMemory(request: SaveMemoryRequest): Promise<MemoryResponse> {
  const res = await fetch(apiUrl("/api/memory/save"), {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error(`saveMemory failed: ${res.status} ${res.statusText}`);
  return res.json();
}

export async function getMemory(request: GetMemoryRequest): Promise<MemoryResponse> {
  const namespaceStr = encodeURIComponent(JSON.stringify(request.namespace));
  const keyStr = encodeURIComponent(request.key);
  const res = await fetch(apiUrl(`/api/memory/get?namespace=${namespaceStr}&key=${keyStr}`), {
    method: "GET",
    headers: getAuthHeaders(),
  });
  if (!res.ok) throw new Error(`getMemory failed: ${res.status} ${res.statusText}`);
  return res.json();
}

export async function searchMemory(request: SearchMemoryRequest): Promise<MemoryResponse> {
  const res = await fetch(apiUrl("/api/memory/search"), {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error(`searchMemory failed: ${res.status} ${res.statusText}`);
  return res.json();
}

export async function deleteMemory(request: DeleteMemoryRequest): Promise<MemoryResponse> {
  const res = await fetch(apiUrl("/api/memory/delete"), {
    method: "DELETE",
    headers: getAuthHeaders(),
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error(`deleteMemory failed: ${res.status} ${res.statusText}`);
  return res.json();
}

export async function getUserProfile(userId: string | number): Promise<UserProfile | null> {
  const res = await getMemory({ namespace: ["user_profiles"], key: `user_${userId}` });
  const value = res.value && Object.keys(res.value).length > 0 ? (res.value as UserProfile) : null;
  return value;
}

export async function saveUserProfile(userId: string | number, profile: UserProfile): Promise<void> {
  await saveMemory({ namespace: ["user_profiles"], key: `user_${userId}`, value: profile as any });
}

export async function getUserPreferences(userId: string | number): Promise<PreferenceItem[]> {
  const res = await getMemory({ namespace: ["user_preferences"], key: `user_${userId}` });
  const items = (res.value?.items || []) as PreferenceItem[];
  return Array.isArray(items) ? items : [];
}

export async function saveUserPreferences(userId: string | number, preferences: PreferenceItem[]): Promise<void> {
  await saveMemory({
    namespace: ["user_preferences"],
    key: `user_${userId}`,
    value: { items: preferences },
  });
}

export async function listMemoriesByNamespace(namespace: string[]): Promise<MemoryItem[]> {
  const res = await searchMemory({ namespace });
  const items = (res.items || []).map((it) => ({
    namespace: it.namespace,
    key: it.key,
    value: it.value,
  }));
  return items;
}

