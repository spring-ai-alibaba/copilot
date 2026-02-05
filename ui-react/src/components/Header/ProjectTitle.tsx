import React, {useEffect, useRef, useState} from "react";
import {Sidebar} from "../Sidebar";
import {db} from "../../utils/indexDB";
import useUserStore from "../../stores/userSlice";
import {useConversationStore} from "../../stores/conversationSlice";
import {useTranslation} from "react-i18next";

export function ProjectTitle() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [chatCount, setChatCount] = useState(0);
  const timeoutRef = useRef<NodeJS.Timeout>();
  const { user, isAuthenticated } = useUserStore();
  // 使用 selector 直接订阅 pagination.total，确保能正确响应变化
  const paginationTotal = useConversationStore((state) => state.pagination.total);
  const loadConversations = useConversationStore((state) => state.loadConversations);
  const { t } = useTranslation();
  const getInitials = (name: string) => {
    return (
      name
        ?.split(" ")
        .map((word) => word[0])
        .join("")
        .toUpperCase()
        .slice(0, 2) || "?"
    );
  };

  // 获取聊天数量
  const loadChatCount = async () => {
    if (isAuthenticated) {
      // 已登录：从后端获取会话总数
      try {
        await loadConversations(1, 1); // 只获取第一页，主要是为了获取 total
        // loadConversations 完成后，从 store 中读取最新的 total
        const updatedTotal = useConversationStore.getState().pagination.total;
        setChatCount(updatedTotal);
      } catch (error) {
        console.error("获取会话总数失败:", error);
        setChatCount(0);
      }
    } else {
      // 未登录：从浏览器 IndexedDB 获取
      const uuids = await db.getAllUuids();
      setChatCount(uuids.length);
    }
  };

  // 已登录时，监听 paginationTotal 的变化并更新 chatCount（用于响应其他地方的更新）
  useEffect(() => {
    if (isAuthenticated && paginationTotal > 0) {
      setChatCount(paginationTotal);
    }
  }, [isAuthenticated, paginationTotal]);

  // 加载聊天数量
  useEffect(() => {
    if (isAuthenticated) {
      loadChatCount();
    } else {
      // 未登录：从 IndexedDB 加载并订阅更新
      loadChatCount();
      const unsubscribe = db.subscribe(loadChatCount);
      return () => {
        unsubscribe();
      };
    }
  }, [isAuthenticated]);

  const handleMouseEnter = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setIsSidebarOpen(true);
  };

  const handleMouseLeave = () => {
    timeoutRef.current = setTimeout(() => {
      setIsSidebarOpen(false);
    }, 300);
  };

  return (
    <div className="flex items-center gap-4">
      <div
        className="flex items-center gap-1.5 px-2 py-1 rounded hover:bg-gray-100 dark:hover:bg-white/10 transition-colors group"
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        <div
          className={`
          w-6 h-6 rounded-full
          flex items-center justify-center
          text-white text-xs font-medium
          ${user?.avatar ? "" : "bg-purple-500 dark:bg-purple-600"}
        `}
          style={
            user?.avatar
              ? {
                  backgroundImage: `url(${user.avatar})`,
                  backgroundSize: "cover",
                }
              : undefined
          }
        >
          {!user?.avatar && getInitials(user?.username || "?")}
        </div>
        <span className="text-gray-900 dark:text-white text-[14px] font-normal">
          {isAuthenticated ? user?.username : "Guest"}
        </span>

        <svg
          className="w-3.5 h-3.5 text-gray-400 transition-transform group-hover:text-gray-600 dark:group-hover:text-white"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 5l7 7-7 7"
          />
        </svg>

        <div className="flex items-center gap-1 text-gray-400 group-hover:text-gray-600 dark:group-hover:text-white">
          <svg
            className="w-3.5 h-3.5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
            />
          </svg>
          <span className="text-xs">{chatCount}</span>
        </div>
      </div>

      <Sidebar
        isOpen={isSidebarOpen}
        onClose={() => setIsSidebarOpen(false)}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        username={user?.username || t("login.guest")}
      />
    </div>
  );
}
