import React, { useEffect, useState } from "react";
import { useConversationStore } from "@/stores/conversationSlice";
import { Conversation } from "@/api/conversation";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";

interface ConversationListProps {
  onSelectConversation?: (conversationId: string | null) => void;
  selectedConversationId?: string | null;
}

export const ConversationList: React.FC<ConversationListProps> = ({
  onSelectConversation,
  selectedConversationId,
}) => {
  const { t } = useTranslation();
  const {
    conversations,
    loading,
    currentConversationId,
    loadConversations,
    createConversation,
    deleteConversation,
    setCurrentConversation,
  } = useConversationStore();

  const [searchTerm, setSearchTerm] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    loadConversations(1, 20);
  }, [loadConversations]);

  // 同步外部选中的会话ID
  useEffect(() => {
    if (selectedConversationId !== undefined) {
      setCurrentConversation(selectedConversationId);
    }
  }, [selectedConversationId, setCurrentConversation]);

  const handleCreateConversation = async () => {
    if (isCreating) return;
    setIsCreating(true);
    try {
      const conversationId = await createConversation();
      onSelectConversation?.(conversationId);
    } catch (error) {
      toast.error("创建会话失败");
      console.error("Failed to create conversation:", error);
    } finally {
      setIsCreating(false);
    }
  };

  const handleSelectConversation = (conversationId: string) => {
    setCurrentConversation(conversationId);
    onSelectConversation?.(conversationId);
  };

  const handleDeleteConversation = async (
    e: React.MouseEvent,
    conversationId: string
  ) => {
    e.stopPropagation();
    if (window.confirm("确定要删除这个会话吗？")) {
      try {
        await deleteConversation(conversationId);
        // 如果删除的是当前选中的会话，切换到空会话
        if (conversationId === currentConversationId) {
          onSelectConversation?.(null);
        }
        toast.success("会话已删除");
      } catch (error) {
        toast.error("删除会话失败");
        console.error("Failed to delete conversation:", error);
      }
    }
  };

  const formatTime = (timeStr?: string) => {
    if (!timeStr) return "";
    const date = new Date(timeStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return date.toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
      });
    } else if (days === 1) {
      return "昨天";
    } else if (days < 7) {
      return `${days}天前`;
    } else {
      return date.toLocaleDateString("zh-CN", {
        month: "short",
        day: "numeric",
      });
    }
  };

  const filteredConversations = conversations.filter(
    (conv) =>
      conv.title?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      conv.conversationId.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* 新建会话按钮 */}
      <button
        onClick={handleCreateConversation}
        disabled={isCreating}
        className="mx-3 my-2 p-2 flex items-center gap-2 text-purple-600 dark:text-blue-400 hover:bg-gray-100 dark:hover:bg-white/5 rounded-lg transition-colors text-[14px] disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0"
      >
        <svg
          className="w-[16px] h-[16px]"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 4v16m8-8H4"
          />
        </svg>
        <span className="translate">
          {isCreating ? "创建中..." : t("sidebar.start_new_chat")}
        </span>
      </button>

      {/* 搜索框 */}
      <div className="px-3 py-2 flex-shrink-0">
        <input
          type="text"
          placeholder={t("sidebar.search") || "搜索会话..."}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full bg-gray-100 dark:bg-[#2C2C2C] text-gray-900 dark:text-white rounded-lg px-3 py-1.5 outline-none text-[14px] border border-gray-200 dark:border-gray-700"
        />
      </div>

      {/* 会话列表 */}
      <div className="flex-1 px-2 mt-1 overflow-y-auto min-h-0">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <div className="w-6 h-6 border-2 border-purple-600 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : filteredConversations.length === 0 ? (
          <div className="text-center py-8 text-gray-500 dark:text-gray-400 text-sm">
            {searchTerm ? "没有找到匹配的会话" : "还没有会话，创建一个新的吧"}
          </div>
        ) : (
          filteredConversations.map((conversation) => {
            const isSelected =
              conversation.conversationId === currentConversationId;
            return (
              <div
                key={conversation.conversationId}
                onClick={() => handleSelectConversation(conversation.conversationId)}
                className={`
                  group flex items-center w-full text-left px-2 py-1.5 
                  rounded text-[14px] cursor-pointer transition-colors
                  ${
                    isSelected
                      ? "bg-purple-100 dark:bg-purple-900/30 text-purple-900 dark:text-purple-200"
                      : "text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/5"
                  }
                `}
              >
                <div className="flex-1 min-w-0">
                  <div className="truncate font-medium">
                    {conversation.title || "新对话"}
                  </div>
                  {conversation.lastMessageTime && (
                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                      {formatTime(conversation.lastMessageTime)}
                    </div>
                  )}
                </div>
                <button
                  onClick={(e) =>
                    handleDeleteConversation(e, conversation.conversationId)
                  }
                  className="hidden text-gray-500 group-hover:block dark:text-gray-400 hover:text-red-500 dark:hover:text-red-400 ml-2"
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                    />
                  </svg>
                </button>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

