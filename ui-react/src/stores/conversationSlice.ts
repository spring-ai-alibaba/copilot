import { create } from "zustand";
import { Conversation } from "@/api/conversation";
import * as conversationApi from "@/api/conversation";

interface ConversationState {
  // 会话列表
  conversations: Conversation[];
  // 当前选中的会话ID
  currentConversationId: string | null;
  // 加载状态
  loading: boolean;
  // 分页信息
  pagination: {
    page: number;
    size: number;
    total: number;
  };

  // Actions
  setCurrentConversation: (id: string | null) => void;
  loadConversations: (page?: number, size?: number) => Promise<void>;
  createConversation: (modelConfigId?: string) => Promise<string>;
  deleteConversation: (id: string) => Promise<void>;
  refreshConversations: () => Promise<void>;
}

export const useConversationStore = create<ConversationState>((set, get) => ({
  conversations: [],
  currentConversationId: null,
  loading: false,
  pagination: {
    page: 1,
    size: 20,
    total: 0,
  },

  setCurrentConversation: (id) => {
    set({ currentConversationId: id });
  },

  loadConversations: async (page = 1, size = 20) => {
    set({ loading: true });
    try {
      const result = await conversationApi.listConversations(page, size);
      set({
        conversations: result.records,
        pagination: {
          page: Number(result.current) || page,
          size: Number(result.size) || size,
          total: Number(result.total) || 0,
        },
        loading: false,
      });
    } catch (error) {
      console.error("加载会话列表失败:", error);
      set({ loading: false });
    }
  },

  createConversation: async (modelConfigId?: string) => {
    try {
      const conversation = await conversationApi.createConversation(modelConfigId);
      // 添加到列表顶部
      set((state) => ({
        conversations: [conversation, ...state.conversations],
        currentConversationId: conversation.conversationId,
      }));
      return conversation.conversationId;
    } catch (error) {
      console.error("创建会话失败:", error);
      throw error;
    }
  },

  deleteConversation: async (id: string) => {
    try {
      await conversationApi.deleteConversation(id);
      // 从列表中移除
      set((state) => ({
        conversations: state.conversations.filter((c) => c.conversationId !== id),
        currentConversationId: state.currentConversationId === id ? null : state.currentConversationId,
      }));
    } catch (error) {
      console.error("删除会话失败:", error);
      throw error;
    }
  },

  refreshConversations: async () => {
    const { pagination } = get();
    await get().loadConversations(pagination.page, pagination.size);
  },
}));

