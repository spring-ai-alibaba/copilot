import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ChevronRight,
  CirclePlus,
  FolderKanban,
  LogIn,
  LogOut,
  MessageSquareText,
  MoreHorizontal,
  PanelLeftClose,
  Pencil,
  Search,
  Settings,
  Trash2,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { updateConversationTitle } from "@/api/conversation";
import { eventEmitter } from "@/components/AiChat/utils/EventEmitter";
import { AppLogo } from "@/components/AppLogo";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Button } from "@/components/ui/button";
import { useFileStore } from "@/components/WeIde/stores/fileStore";
import type { SettingsTab } from "@/components/Settings";
import { useConversationStore } from "@/stores/conversationSlice";
import { useContextStore } from "@/stores/contextSlice";
import useUserStore from "@/stores/userSlice";
import { db } from "@/utils/indexDB";
import { cn } from "@/utils/cn";

type GuestConversation = {
  id: string;
  title: string;
  lastMessage: string;
  updatedAt: number;
};

type ConversationRow = {
  id: string;
  title: string;
  preview: string;
  updatedAt: number;
  remote: boolean;
};

export type NavigationSidebarProps = {
  open: boolean;
  onClose: () => void;
  onOpenSettings: (tab?: SettingsTab) => void;
};

function formatConversationTime(value: number, t: TFunction, locale: string) {
  if (!value) return "";
  const date = new Date(value);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  if (diff < 60_000) {
    return t("appShell.sidebar.time.justNow", { defaultValue: "刚刚" });
  }
  if (diff < 3_600_000) {
    return t("appShell.sidebar.time.minutesAgo", {
      defaultValue: "{{count}} 分钟前",
      count: Math.floor(diff / 60_000),
    });
  }
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString(locale, {
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return t("appShell.sidebar.time.yesterday", { defaultValue: "昨天" });
  }
  return date.toLocaleDateString(locale, { month: "short", day: "numeric" });
}

function initials(name?: string) {
  const text = name?.trim();
  if (!text) return "?";
  return text.slice(0, 2).toUpperCase();
}

export function NavigationSidebar({
  open,
  onClose,
  onOpenSettings,
}: NavigationSidebarProps) {
  const { t, i18n } = useTranslation();
  const { user, isAuthenticated, openLoginModal, logout } = useUserStore();
  const {
    conversations,
    currentConversationId,
    loading,
    pagination,
    loadConversations,
    deleteConversation,
    refreshConversations,
    setCurrentConversation,
  } = useConversationStore();
  const files = useFileStore((state) => state.files);
  const projectRoot = useFileStore((state) => state.projectRoot);
  const clearContext = useContextStore((state) => state.clear);
  const [searchValue, setSearchValue] = useState("");
  const [guestConversations, setGuestConversations] = useState<
    GuestConversation[]
  >([]);
  const [guestConversationsLoaded, setGuestConversationsLoaded] =
    useState(false);
  const [selectedGuestId, setSelectedGuestId] = useState<string | null>(() =>
    localStorage.getItem("arc-selected-guest-conversation"),
  );
  const [menuId, setMenuId] = useState<string | null>(null);
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<ConversationRow | null>(
    null,
  );
  const [deleting, setDeleting] = useState(false);
  const menuRootRef = useRef<HTMLDivElement | null>(null);

  const loadGuestConversations = useCallback(async () => {
    try {
      const ids = await db.getAllUuids();
      const rows = await Promise.all(
        ids.map(async (id) => {
          const records = await db.getByUuid(id);
          const record = records[0];
          const messages = record?.data?.messages ?? [];
          const last = messages[messages.length - 1];
          return {
            id,
            title:
              record?.data?.title ||
              t("appShell.sidebar.newConversation", { defaultValue: "新对话" }),
            lastMessage: typeof last?.content === "string" ? last.content : "",
            updatedAt: record?.time ?? 0,
          };
        }),
      );
      setGuestConversations(rows.sort((a, b) => b.updatedAt - a.updatedAt));
    } catch (error) {
      console.error("Failed to load local conversations", error);
      setGuestConversations([]);
    } finally {
      setGuestConversationsLoaded(true);
    }
  }, [t]);

  useEffect(() => {
    if (isAuthenticated) {
      setSelectedGuestId(null);
      void loadConversations(1, 30);
      return;
    }
    void loadGuestConversations();
    const unsubscribe = db.subscribe(() => void loadGuestConversations());
    return () => {
      unsubscribe();
    };
  }, [
    isAuthenticated,
    loadConversations,
    loadGuestConversations,
    user?.id,
    user?.userId,
  ]);

  useEffect(() => {
    if (isAuthenticated || !guestConversationsLoaded || !selectedGuestId)
      return;
    if (
      guestConversations.some(
        (conversation) => conversation.id === selectedGuestId,
      )
    )
      return;
    setSelectedGuestId(null);
    localStorage.removeItem("arc-selected-guest-conversation");
  }, [
    guestConversations,
    guestConversationsLoaded,
    isAuthenticated,
    selectedGuestId,
  ]);

  useEffect(() => {
    const onPointerDown = (event: PointerEvent) => {
      if (menuRootRef.current?.contains(event.target as Node)) return;
      setMenuId(null);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, []);

  const rows = useMemo<ConversationRow[]>(() => {
    if (isAuthenticated) {
      return conversations.map((conversation) => ({
        id: conversation.conversationId,
        title:
          conversation.title ||
          t("appShell.sidebar.newConversation", { defaultValue: "新对话" }),
        preview: "",
        updatedAt: new Date(
          conversation.lastMessageTime ||
            conversation.updatedTime ||
            conversation.createdTime,
        ).getTime(),
        remote: true,
      }));
    }
    return guestConversations.map((conversation) => ({
      id: conversation.id,
      title: conversation.title,
      preview: conversation.lastMessage,
      updatedAt: conversation.updatedAt,
      remote: false,
    }));
  }, [conversations, guestConversations, isAuthenticated, t]);

  const filteredRows = useMemo(() => {
    const keyword = searchValue.trim().toLocaleLowerCase();
    if (!keyword) return rows;
    return rows.filter(
      (row) =>
        row.title.toLocaleLowerCase().includes(keyword) ||
        row.preview.toLocaleLowerCase().includes(keyword),
    );
  }, [rows, searchValue]);

  const newConversation = () => {
    setSelectedGuestId(null);
    localStorage.removeItem("arc-selected-guest-conversation");
    setCurrentConversation(null);
    eventEmitter.emit("chat:select", "");
    if (window.innerWidth < 1024) onClose();
  };

  const selectConversation = (row: ConversationRow) => {
    setMenuId(null);
    if (row.remote) {
      setSelectedGuestId(null);
      localStorage.removeItem("arc-selected-guest-conversation");
      setCurrentConversation(row.id);
    } else {
      setCurrentConversation(null);
      setSelectedGuestId(row.id);
      localStorage.setItem("arc-selected-guest-conversation", row.id);
      eventEmitter.emit("chat:select", row.id);
    }
    if (window.innerWidth < 1024) onClose();
  };

  const beginRename = (row: ConversationRow) => {
    setMenuId(null);
    setRenamingId(row.id);
    setRenameValue(row.title);
  };

  const commitRename = async () => {
    const id = renamingId;
    const title = renameValue.trim();
    setRenamingId(null);
    if (!id || !title) return;
    const current = rows.find((row) => row.id === id);
    if (current?.title === title) return;
    try {
      if (current?.remote) {
        await updateConversationTitle(id, title);
        await refreshConversations();
      } else {
        await db.renameByUuid(id, title);
        await loadGuestConversations();
      }
    } catch (error) {
      console.error("Failed to rename conversation", error);
    }
  };

  const confirmDelete = async () => {
    const target = deleteTarget;
    if (!target) return;
    setDeleting(true);
    try {
      if (target.remote) {
        await deleteConversation(target.id);
      } else {
        await db.deleteByUuid(target.id);
        await loadGuestConversations();
        if (selectedGuestId === target.id) {
          setSelectedGuestId(null);
          localStorage.removeItem("arc-selected-guest-conversation");
          eventEmitter.emit("chat:select", "");
        }
      }
      if (currentConversationId === target.id) {
        setCurrentConversation(null);
        eventEmitter.emit("chat:select", "");
      }
      clearContext(target.id);
      setDeleteTarget(null);
    } catch (error) {
      console.error("Failed to delete conversation", error);
    } finally {
      setDeleting(false);
    }
  };

  const projectName = projectRoot
    ? projectRoot.replace(/\\/g, "/").split("/").filter(Boolean).pop()
    : t("appShell.sidebar.currentWorkspace", { defaultValue: "当前工作区" });
  const dateLocale = i18n.resolvedLanguage?.startsWith("zh")
    ? "zh-CN"
    : i18n.resolvedLanguage || "zh-CN";

  return (
    <>
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 h-full shrink-0 overflow-hidden border-r border-border/50 bg-sidebar transition-[width,opacity,transform] duration-200 ease-out lg:relative lg:z-20",
          open
            ? "w-[272px] translate-x-0 opacity-100"
            : "w-0 -translate-x-4 opacity-0",
        )}
        aria-hidden={!open}
      >
        <div className="flex h-full w-[272px] min-w-[272px] flex-col">
          <div className="shrink-0 border-b border-border/50 px-2 pb-3 pt-3">
            <div className="flex items-center justify-between gap-2 px-1">
              <div className="flex min-w-0 items-center gap-2.5">
                <AppLogo />
                <div className="min-w-0">
                  <div className="truncate text-sm font-semibold tracking-tight text-foreground">
                    Alibaba Copilot
                  </div>
                  <div className="mt-0.5 text-[10px] font-medium uppercase tracking-[0.16em] text-muted-foreground/70">
                    {t("appShell.sidebar.workspaceAgent", {
                      defaultValue: "Workspace Agent",
                    })}
                  </div>
                </div>
              </div>
              <button
                type="button"
                className="arc-icon-button"
                onClick={onClose}
                title={t("appShell.sidebar.collapse", {
                  defaultValue: "收起侧栏",
                })}
                aria-label={t("appShell.sidebar.collapse", {
                  defaultValue: "收起侧栏",
                })}
              >
                <PanelLeftClose className="h-4 w-4" />
              </button>
            </div>

            <button
              type="button"
              onClick={newConversation}
              className="arc-sidebar-row mt-3"
            >
              <CirclePlus className="h-4 w-4 text-foreground/85" />
              <span>
                {t("sidebar.start_new_chat", { defaultValue: "新建对话" })}
              </span>
            </button>
          </div>

          <div className="shrink-0 px-2 pb-1 pt-2">
            <div className="px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.15em] text-muted-foreground/60">
              {t("appShell.sidebar.workspace", { defaultValue: "Workspace" })}
            </div>
            <button
              type="button"
              className="arc-sidebar-row group"
              title={projectRoot || projectName}
            >
              <FolderKanban className="h-4 w-4 shrink-0 text-foreground/70" />
              <span className="min-w-0 flex-1 truncate">{projectName}</span>
              <span className="text-[10px] tabular-nums text-muted-foreground/65">
                {Object.keys(files).length}
              </span>
              <ChevronRight className="h-3.5 w-3.5 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5" />
            </button>
          </div>

          <div className="flex min-h-0 flex-1 flex-col">
            <div className="flex items-center justify-between px-4 pb-1 pt-2">
              <div className="text-[10px] font-semibold uppercase tracking-[0.15em] text-muted-foreground/60">
                {t("appShell.sidebar.recentConversations", {
                  defaultValue: "最近对话",
                })}
              </div>
              <span className="text-[10px] tabular-nums text-muted-foreground/55">
                {rows.length}
              </span>
            </div>
            <div className="px-3 pb-2">
              <label className="relative block">
                <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground/65" />
                <input
                  value={searchValue}
                  onChange={(event) => setSearchValue(event.target.value)}
                  placeholder={t("sidebar.search", {
                    defaultValue: "搜索对话",
                  })}
                  className="h-8 w-full rounded-lg border border-border/70 bg-background/55 pl-8 pr-2.5 text-xs text-foreground outline-none transition-colors placeholder:text-muted-foreground/55 hover:bg-background/75 focus:border-foreground/15 focus:bg-background"
                />
              </label>
            </div>

            <div
              ref={menuRootRef}
              className="min-h-0 flex-1 overflow-y-auto px-2 pb-3"
            >
              {loading && isAuthenticated && rows.length === 0 ? (
                <div className="space-y-1 p-1">
                  {[0, 1, 2, 3].map((item) => (
                    <div
                      key={item}
                      className="h-9 animate-pulse rounded-lg bg-foreground/[0.04]"
                    />
                  ))}
                </div>
              ) : filteredRows.length === 0 ? (
                <div className="flex flex-col items-center px-4 py-10 text-center">
                  <MessageSquareText className="h-5 w-5 text-muted-foreground/40" />
                  <p className="mt-2 text-xs leading-5 text-muted-foreground/65">
                    {searchValue
                      ? t("appShell.sidebar.noSearchResults", {
                          defaultValue: "没有匹配的对话",
                        })
                      : t("appShell.sidebar.emptyConversations", {
                          defaultValue: "还没有对话，开始一个新任务吧",
                        })}
                  </p>
                </div>
              ) : (
                <div className="space-y-0.5">
                  {filteredRows.map((row) => {
                    const active = row.remote
                      ? row.id === currentConversationId
                      : row.id === selectedGuestId;
                    const renaming = row.id === renamingId;
                    return (
                      <div key={row.id} className="group relative">
                        <button
                          type="button"
                          data-active={active}
                          onClick={() => selectConversation(row)}
                          className="arc-sidebar-row min-h-[42px] pr-8"
                        >
                          <div className="min-w-0 flex-1">
                            {renaming ? (
                              <input
                                autoFocus
                                value={renameValue}
                                onChange={(event) =>
                                  setRenameValue(event.target.value)
                                }
                                onClick={(event) => event.stopPropagation()}
                                onBlur={() => void commitRename()}
                                onKeyDown={(event) => {
                                  event.stopPropagation();
                                  if (event.key === "Enter")
                                    void commitRename();
                                  if (event.key === "Escape")
                                    setRenamingId(null);
                                }}
                                className="h-6 w-full rounded-md border border-border bg-background px-1.5 text-xs outline-none"
                              />
                            ) : (
                              <>
                                <div className="truncate text-[13px] leading-4 text-foreground/90">
                                  {row.title}
                                </div>
                                <div className="mt-1 truncate text-[10px] leading-3 text-muted-foreground/65">
                                  {formatConversationTime(
                                    row.updatedAt,
                                    t,
                                    dateLocale,
                                  )}
                                </div>
                              </>
                            )}
                          </div>
                        </button>
                        {!renaming ? (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.stopPropagation();
                              setMenuId((current) =>
                                current === row.id ? null : row.id,
                              );
                            }}
                            aria-label={t(
                              "appShell.sidebar.conversationActions",
                              {
                                defaultValue: "会话操作",
                              },
                            )}
                            className={cn(
                              "absolute right-1.5 top-2 inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground transition-[opacity,background-color,color] hover:bg-foreground/[0.07] hover:text-foreground",
                              menuId === row.id
                                ? "opacity-100"
                                : "opacity-0 group-hover:opacity-100",
                            )}
                          >
                            <MoreHorizontal className="h-3.5 w-3.5" />
                          </button>
                        ) : null}
                        {menuId === row.id ? (
                          <div className="arc-popover absolute right-1 top-8 z-30 w-36 p-1.5">
                            <button
                              type="button"
                              onClick={() => beginRename(row)}
                              className="arc-popover-item min-h-8 gap-2 py-1.5 text-xs"
                            >
                              <Pencil className="h-3.5 w-3.5" />
                              {t("appShell.sidebar.rename", {
                                defaultValue: "重命名",
                              })}
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setMenuId(null);
                                setDeleteTarget(row);
                              }}
                              className="arc-popover-item min-h-8 gap-2 py-1.5 text-xs text-destructive hover:bg-destructive/10 hover:text-destructive"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              {t("appShell.sidebar.delete", {
                                defaultValue: "删除",
                              })}
                            </button>
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                  {isAuthenticated &&
                  !searchValue.trim() &&
                  conversations.length < pagination.total ? (
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() =>
                        void loadConversations(1, pagination.size + 30)
                      }
                      className="mt-1 inline-flex h-8 w-full items-center justify-center rounded-lg text-[11px] font-medium text-muted-foreground transition-colors hover:bg-foreground/[0.05] hover:text-foreground disabled:cursor-wait disabled:opacity-55"
                    >
                      {loading
                        ? t("appShell.sidebar.loadingMore", {
                            defaultValue: "正在加载…",
                          })
                        : t("appShell.sidebar.loadMore", {
                            defaultValue: "加载更多",
                          })}
                    </button>
                  ) : null}
                </div>
              )}
            </div>
          </div>

          <div className="shrink-0 border-t border-border/50 px-2 py-2">
            <button
              type="button"
              onClick={() => onOpenSettings("General")}
              className="arc-sidebar-row"
            >
              <Settings className="h-4 w-4 text-foreground/70" />
              <span>{t("sidebar.settings", { defaultValue: "设置" })}</span>
            </button>

            {isAuthenticated ? (
              <div className="mt-1 flex items-center gap-2 rounded-lg px-2 py-2">
                <div
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-foreground/[0.08] bg-foreground/[0.08] text-[11px] font-semibold text-foreground"
                  style={
                    user?.avatar
                      ? {
                          backgroundImage: `url(${user.avatar})`,
                          backgroundSize: "cover",
                          backgroundPosition: "center",
                        }
                      : undefined
                  }
                >
                  {user?.avatar ? null : initials(user?.username)}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-xs font-medium text-foreground">
                    {user?.username ||
                      t("appShell.sidebar.user", { defaultValue: "User" })}
                  </div>
                  <div className="mt-0.5 truncate text-[10px] text-muted-foreground">
                    {user?.email ||
                      t("appShell.sidebar.signedIn", {
                        defaultValue: "已登录",
                      })}
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => logout()}
                  title={t("appShell.sidebar.logout", {
                    defaultValue: "退出登录",
                  })}
                  aria-label={t("appShell.sidebar.logout", {
                    defaultValue: "退出登录",
                  })}
                  className="h-7 w-7"
                >
                  <LogOut className="h-3.5 w-3.5" />
                </Button>
              </div>
            ) : (
              <button
                type="button"
                onClick={openLoginModal}
                className="arc-sidebar-row mt-1"
              >
                <div className="flex h-7 w-7 items-center justify-center rounded-full bg-foreground/[0.08] text-[11px] font-medium">
                  ?
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-medium text-foreground">
                    {t("appShell.sidebar.guest", { defaultValue: "Guest" })}
                  </div>
                  <div className="text-[10px] text-muted-foreground">
                    {t("appShell.sidebar.signInToSync", {
                      defaultValue: "登录以同步会话",
                    })}
                  </div>
                </div>
                <LogIn className="h-3.5 w-3.5 text-muted-foreground" />
              </button>
            )}
          </div>
        </div>
      </aside>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title={t("appShell.sidebar.deleteDialog.title", {
          defaultValue: "删除这个对话？",
        })}
        description={
          deleteTarget
            ? t("appShell.sidebar.deleteDialog.description", {
                defaultValue: "“{{title}}”将被永久删除，此操作无法撤销。",
                title: deleteTarget.title,
              })
            : undefined
        }
        confirmLabel={t("appShell.sidebar.deleteDialog.confirm", {
          defaultValue: "删除",
        })}
        cancelLabel={t("appShell.sidebar.deleteDialog.cancel", {
          defaultValue: "取消",
        })}
        loading={deleting}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => void confirmDelete()}
      />
    </>
  );
}
