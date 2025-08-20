import React, { useState, useRef, useEffect } from "react";
import { Sidebar } from "../Sidebar";
import { db } from "../../utils/indexDB";
import useUserStore from "../../stores/userSlice";
import { useTranslation } from "react-i18next";

export function ProjectTitle() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [chatCount, setChatCount] = useState(0);
  const timeoutRef = useRef<NodeJS.Timeout>();
  const { user, isAuthenticated } = useUserStore();
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
    const uuids = await db.getAllUuids();
    setChatCount(uuids.length);
  };

  useEffect(() => {
    loadChatCount();
    // 订阅数据库更新
    const unsubscribe = db.subscribe(loadChatCount);
    return () => {
      unsubscribe();
    };
  }, []);

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
