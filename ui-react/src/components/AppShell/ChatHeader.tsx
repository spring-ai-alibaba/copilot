import {
  Code2,
  MessageSquare,
  Moon,
  PanelLeft,
  PanelRightClose,
  PanelRightOpen,
  Settings,
  Sun,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import useChatModeStore from "@/stores/chatModeSlice";
import useThemeStore from "@/stores/themeSlice";
import { useConversationStore } from "@/stores/conversationSlice";
import { HeaderActions } from "@/components/Header/HeaderActions";
import { cn } from "@/utils/cn";

export type ChatHeaderProps = {
  sidebarOpen: boolean;
  dockOpen: boolean;
  dockAvailable: boolean;
  onOpenSidebar: () => void;
  onToggleDock: () => void;
  onOpenSettings: () => void;
};

export function ChatHeader({
  sidebarOpen,
  dockOpen,
  dockAvailable,
  onOpenSidebar,
  onToggleDock,
  onOpenSettings,
}: ChatHeaderProps) {
  const { t } = useTranslation();
  const { isDarkMode, setTheme } = useThemeStore();
  const { mode, setMode } = useChatModeStore();
  const currentConversationId = useConversationStore(
    (state) => state.currentConversationId,
  );
  const conversations = useConversationStore((state) => state.conversations);
  const title = useMemo(
    () =>
      conversations.find(
        (conversation) => conversation.conversationId === currentConversationId,
      )?.title,
    [conversations, currentConversationId],
  );

  const changeTheme = () => {
    const nextDark = !isDarkMode;
    localStorage.setItem("theme", nextDark ? "dark" : "light");
    setTheme(nextDark);
  };

  const changeMode = (nextMode: "chat" | "builder") => {
    setMode(nextMode as Parameters<typeof setMode>[0]);
  };

  return (
    <header className="relative z-30 flex h-12 shrink-0 items-center justify-between gap-3 px-3.5">
      <div className="flex min-w-0 items-center gap-2">
        {!sidebarOpen ? (
          <button
            type="button"
            onClick={onOpenSidebar}
            className="arc-icon-button"
            title={t("appShell.header.openSidebar", {
              defaultValue: "打开侧栏",
            })}
            aria-label={t("appShell.header.openSidebar", {
              defaultValue: "打开侧栏",
            })}
          >
            <PanelLeft className="h-[18px] w-[18px]" />
          </button>
        ) : null}
        <div className="min-w-0">
          <div className="max-w-[36vw] truncate text-xs font-medium text-foreground/80">
            {title ||
              (mode === "builder"
                ? t("appShell.header.builderWorkspace", {
                    defaultValue: "开发工作区",
                  })
                : t("appShell.header.newConversation", {
                    defaultValue: "新对话",
                  }))}
          </div>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1">
        <div className="mr-1 hidden h-8 items-center rounded-lg bg-muted/65 p-0.5 sm:flex">
          <button
            type="button"
            onClick={() => changeMode("chat")}
            className={cn(
              "inline-flex h-7 items-center gap-1.5 rounded-md px-2.5 text-[11px] font-medium transition-colors",
              mode === "chat"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            <MessageSquare className="h-3.5 w-3.5" />
            {t("appShell.header.modes.chat", { defaultValue: "Chat" })}
          </button>
          <button
            type="button"
            onClick={() => changeMode("builder")}
            className={cn(
              "inline-flex h-7 items-center gap-1.5 rounded-md px-2.5 text-[11px] font-medium transition-colors",
              mode === "builder"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Code2 className="h-3.5 w-3.5" />
            {t("appShell.header.modes.builder", { defaultValue: "Builder" })}
          </button>
        </div>

        <HeaderActions />

        <button
          type="button"
          onClick={changeTheme}
          className="arc-icon-button"
          title={
            isDarkMode
              ? t("appShell.header.switchToLight", {
                  defaultValue: "切换到亮色",
                })
              : t("appShell.header.switchToDark", {
                  defaultValue: "切换到暗色",
                })
          }
          aria-label={
            isDarkMode
              ? t("appShell.header.switchToLight", {
                  defaultValue: "切换到亮色",
                })
              : t("appShell.header.switchToDark", {
                  defaultValue: "切换到暗色",
                })
          }
        >
          {isDarkMode ? (
            <Sun className="h-4 w-4" />
          ) : (
            <Moon className="h-4 w-4" />
          )}
        </button>

        <button
          type="button"
          onClick={onOpenSettings}
          className="arc-icon-button"
          title={t("appShell.header.settings", { defaultValue: "设置" })}
          aria-label={t("appShell.header.settings", { defaultValue: "设置" })}
        >
          <Settings className="h-4 w-4" />
        </button>

        {dockAvailable ? (
          <button
            type="button"
            onClick={onToggleDock}
            className={cn(
              "arc-icon-button",
              dockOpen && "bg-foreground/[0.06] text-foreground",
            )}
            title={
              dockOpen
                ? t("appShell.header.collapseProjectTools", {
                    defaultValue: "收起项目工具",
                  })
                : t("appShell.header.openProjectTools", {
                    defaultValue: "打开项目工具",
                  })
            }
            aria-label={
              dockOpen
                ? t("appShell.header.collapseProjectTools", {
                    defaultValue: "收起项目工具",
                  })
                : t("appShell.header.openProjectTools", {
                    defaultValue: "打开项目工具",
                  })
            }
            aria-expanded={dockOpen}
          >
            {dockOpen ? (
              <PanelRightClose className="h-4 w-4" />
            ) : (
              <PanelRightOpen className="h-4 w-4" />
            )}
          </button>
        ) : null}
      </div>
    </header>
  );
}
