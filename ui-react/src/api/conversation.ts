import { apiUrl } from "./base";

export interface Conversation {
  conversationId: string;
  userId: number;
  title: string;
  modelConfigId?: number;
  messageCount?: number;
  lastMessageTime?: string;
  createdTime: string;
  updatedTime: string;
}

export interface CreateConversationRequest {
  modelConfigId?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export interface ChatMessage {
  role: string;
  content: string;
  conversationId: string;
  createdAt?: string;
}

/**
 * 创建会话
 */
export const createConversation = async (
  modelConfigId?: string
): Promise<Conversation> => {
  const response = await fetch(apiUrl("/api/chat/conversations"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
    },
    body: JSON.stringify({ modelConfigId }),
  });

  if (!response.ok) {
    throw new Error("创建会话失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "创建会话失败");
  }

  return result.data;
};

/**
 * 获取会话列表
 */
export const listConversations = async (
  page: number = 1,
  size: number = 20
): Promise<PageResult<Conversation>> => {
  const response = await fetch(
    apiUrl(`/api/chat/conversations?page=${page}&size=${size}`),
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error("获取会话列表失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "获取会话列表失败");
  }

  return result.data;
};

/**
 * 获取会话信息
 */
export const getConversation = async (
  conversationId: string
): Promise<Conversation> => {
  const response = await fetch(
    apiUrl(`/api/chat/conversations/${conversationId}`),
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error("获取会话信息失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "获取会话信息失败");
  }

  return result.data;
};

/**
 * 获取会话历史消息
 */
export const getConversationMessages = async (
  conversationId: string
): Promise<ChatMessage[]> => {
  const response = await fetch(
    apiUrl(`/api/chat/conversations/${conversationId}/messages`),
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error("获取会话消息失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "获取会话消息失败");
  }

  return result.data;
};

/**
 * 更新会话标题
 */
export const updateConversationTitle = async (
  conversationId: string,
  title: string
): Promise<void> => {
  const response = await fetch(
    apiUrl(`/api/chat/conversations/${conversationId}/title?title=${encodeURIComponent(title)}`),
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error("更新会话标题失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "更新会话标题失败");
  }
};

/**
 * 删除会话
 */
export const deleteConversation = async (
  conversationId: string
): Promise<void> => {
  const response = await fetch(
    apiUrl(`/api/chat/conversations/${conversationId}`),
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${localStorage.getItem("token") || ""}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error("删除会话失败");
  }

  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.msg || "删除会话失败");
  }
};

