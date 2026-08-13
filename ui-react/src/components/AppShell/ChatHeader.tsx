import {
  AlertCircle,
  Code2,
  Gauge,
  LoaderCircle,
  MessageSquare,
  Moon,
  PanelLeft,
  PanelRightClose,
  PanelRightOpen,
  RefreshCw,
  RotateCcw,
  Settings,
  Sun,
  X,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import useChatModeStore from "@/stores/chatModeSlice";
import useThemeStore from "@/stores/themeSlice";
import useUserStore from "@/stores/userSlice";
import { useConversationStore } from "@/stores/conversationSlice";
import { HeaderActions } from "@/components/Header/HeaderActions";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/utils/cn";
import type {
  ContextState,
  ConversationContextStatus,
} from "@/api/context";
import {
  useContextStore,
} from "@/stores/contextSlice";
import type { ContextErrorState } from "@/stores/contextSlice";

export type ChatHeaderProps = {
  sidebarOpen: boolean;
  dockOpen: boolean;
  dockAvailable: boolean;
  onOpenSidebar: () => void;
  onToggleDock: () => void;
  onOpenSettings: () => void;
};

const CONTEXT_PANEL_ID = "conversation-context-panel";

const stateLabelKeys: Record<ContextState, string> = {
  EMPTY: "appShell.contextPanel.states.empty",
  ACTIVE: "appShell.contextPanel.states.active",
  COMPACTED: "appShell.contextPanel.states.compacted",
};

const stateTone: Record<ContextState, string> = {
  EMPTY: "border-border/70 bg-muted/70 text-muted-foreground",
  ACTIVE: "border-emerald-500/25 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  COMPACTED: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300",
};

function ContextRow({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)] items-start gap-4">
      <dt className="min-w-0 text-muted-foreground">{label}</dt>
      <dd
        className={cn(
          "min-w-0 break-words text-right font-medium text-foreground",
          valueClassName,
        )}
      >
        {value}
      </dd>
    </div>
  );
}

function formatTimestamp(
  value: string | null | undefined,
  locale: string,
  unavailable: string,
) {
  if (!value) return unavailable;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return unavailable;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function errorMessage(
  t: (key: string, options?: Record<string, unknown>) => string,
  error: ContextErrorState,
) {
  if (error.reason === "unauthorized") {
    return t("appShell.contextPanel.errors.unauthorized");
  }
  if (error.reason === "forbidden") {
    return t("appShell.contextPanel.errors.forbidden");
  }
  if (error.reason === "notFound") {
    return t("appShell.contextPanel.errors.notFound");
  }
  if (error.reason === "conflict") {
    return t("appShell.contextPanel.errors.conflict");
  }
  if (error.reason === "unavailable") {
    return t(
      error.operation === "reset"
        ? "appShell.contextPanel.errors.resetUnavailable"
        : "appShell.contextPanel.errors.loadUnavailable",
    );
  }
  return t(
    error.operation === "reset"
      ? "appShell.contextPanel.errors.reset"
      : "appShell.contextPanel.errors.load",
  );
}

export function ChatHeader({
  sidebarOpen,
  dockOpen,
  dockAvailable,
  onOpenSidebar,
  onToggleDock,
  onOpenSettings,
}: ChatHeaderProps) {
  const { t, i18n } = useTranslation();
  const { isDarkMode, setTheme } = useThemeStore();
  const { mode, setMode } = useChatModeStore();
  const isAuthenticated = useUserStore((state) => state.isAuthenticated);
  const currentConversationId = useConversationStore(
    (state) => state.currentConversationId,
  );
  const conversations = useConversationStore((state) => state.conversations);
  const contextStatus = useContextStore(
    (state) =>
      (currentConversationId
        ? state.statuses[currentConversationId]
        : undefined) as ConversationContextStatus | undefined,
  );
  const contextError = useContextStore((state) =>
    currentConversationId ? state.errors[currentConversationId] : undefined,
  );
  const contextLoading = useContextStore((state) =>
    Boolean(
      currentConversationId &&
        state.loadingByConversation[currentConversationId],
    ),
  );
  const contextResetting = useContextStore((state) =>
    Boolean(
      currentConversationId &&
        state.resettingByConversation[currentConversationId],
    ),
  );
  const contextRunning = useContextStore(
    (state) => Boolean(
      currentConversationId &&
        state.runningByConversation[currentConversationId],
    ),
  );
  const loadContext = useContextStore((state) => state.load);
  const resetContext = useContextStore((state) => state.reset);
  const [contextOpen, setContextOpen] = useState(false);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const contextRootRef = useRef<HTMLDivElement | null>(null);
  const contextPanelRef = useRef<HTMLDivElement | null>(null);
  const contextTriggerRef = useRef<HTMLButtonElement | null>(null);
  const resetDialogOpenRef = useRef(false);

  const title = useMemo(
    () =>
      conversations.find(
        (conversation) => conversation.conversationId === currentConversationId,
      )?.title,
    [conversations, currentConversationId],
  );

  const locale = i18n.resolvedLanguage?.startsWith("zh")
    ? "zh-CN"
    : i18n.resolvedLanguage || "en-US";
  const numberFormatter = useMemo(() => new Intl.NumberFormat(locale), [locale]);
  const formatNumber = useCallback(
    (value: number | null | undefined) =>
      value == null || !Number.isFinite(value)
        ? t("appShell.contextPanel.notAvailable")
        : numberFormatter.format(Math.max(0, value)),
    [numberFormatter, t],
  );
  const unavailableLabel = t("appShell.contextPanel.notAvailable");
  const formatTime = useCallback(
    (value: string | null | undefined) =>
      formatTimestamp(value, locale, unavailableLabel),
    [locale, unavailableLabel],
  );

  const contextAvailable = Boolean(isAuthenticated && currentConversationId);
  const isRunning = contextRunning;
  const statusLabel = contextStatus
    ? t(stateLabelKeys[contextStatus.state])
    : unavailableLabel;
  const contextLabel = t("appShell.header.context");

  const closeContext = useCallback((restoreFocus = true) => {
    setContextOpen(false);
    setResetDialogOpen(false);
    if (restoreFocus) {
      requestAnimationFrame(() => {
        contextTriggerRef.current?.focus({ preventScroll: true });
      });
    }
  }, []);

  useEffect(() => {
    setContextOpen(false);
    setResetDialogOpen(false);
    if (isAuthenticated && currentConversationId) {
      void loadContext(currentConversationId);
    }
  }, [currentConversationId, isAuthenticated, loadContext]);

  useEffect(() => {
    resetDialogOpenRef.current = resetDialogOpen;
  }, [resetDialogOpen]);

  useEffect(() => {
    if (!contextOpen) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (resetDialogOpenRef.current) return;
      if (!contextRootRef.current?.contains(event.target as Node)) {
        closeContext(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !resetDialogOpenRef.current) {
        event.preventDefault();
        closeContext();
      }
    };
    window.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    requestAnimationFrame(() => contextPanelRef.current?.focus());
    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [closeContext, contextOpen]);

  useEffect(() => {
    if (!contextAvailable) closeContext(false);
  }, [contextAvailable, closeContext]);

  const changeTheme = () => {
    const nextDark = !isDarkMode;
    localStorage.setItem("theme", nextDark ? "dark" : "light");
    setTheme(nextDark);
  };

  const changeMode = (nextMode: "chat" | "builder") => {
    setMode(nextMode as Parameters<typeof setMode>[0]);
  };

  const handleRetry = () => {
    if (!currentConversationId) return;
    if (contextError?.operation === "reset") {
      setResetDialogOpen(true);
      return;
    }
    void loadContext(currentConversationId);
  };

  const handleConfirmReset = async () => {
    if (!currentConversationId || isRunning) return;
    try {
      await resetContext(currentConversationId);
      setResetDialogOpen(false);
      toast.success(t("appShell.contextPanel.resetSuccess"));
    } catch {
      setResetDialogOpen(false);
      // The translated error remains visible in the context panel.
    }
  };

  return (
    <>
      <header className="relative z-30 flex h-12 shrink-0 items-center justify-between gap-2 px-3.5 sm:gap-3">
        <div className="flex min-w-0 items-center gap-2">
          {!sidebarOpen ? (
            <button
              type="button"
              onClick={onOpenSidebar}
              className="arc-icon-button"
              title={t("appShell.header.openSidebar")}
              aria-label={t("appShell.header.openSidebar")}
            >
              <PanelLeft className="h-[18px] w-[18px]" />
            </button>
          ) : null}
          <div className="min-w-0">
            <div className="max-w-[36vw] truncate text-xs font-medium text-foreground/80">
              {title ||
                (mode === "builder"
                  ? t("appShell.header.builderWorkspace")
                  : t("appShell.header.newConversation"))}
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
              {t("appShell.header.modes.chat")}
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
              {t("appShell.header.modes.builder")}
            </button>
          </div>

          <HeaderActions />

          <div ref={contextRootRef} className="relative">
            <button
              ref={contextTriggerRef}
              type="button"
              onClick={() => setContextOpen((open) => !open)}
              disabled={!contextAvailable}
              className={cn(
                "arc-icon-button",
                contextOpen && "bg-foreground/[0.06] text-foreground",
                !contextAvailable && "cursor-not-allowed opacity-40",
              )}
              title={
                contextAvailable
                  ? contextLabel
                  : t("appShell.contextPanel.unavailable")
              }
              aria-label={
                contextAvailable
                  ? contextLabel
                  : t("appShell.contextPanel.unavailable")
              }
              aria-expanded={contextOpen}
              aria-controls={CONTEXT_PANEL_ID}
            >
              <Gauge className="h-4 w-4" aria-hidden="true" />
              {contextStatus?.state === "COMPACTED" ? (
                <span
                  className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-amber-500"
                  aria-hidden="true"
                />
              ) : null}
            </button>

            {contextOpen && contextAvailable ? (
              <div
                ref={contextPanelRef}
                id={CONTEXT_PANEL_ID}
                role="dialog"
                aria-modal="false"
                aria-labelledby={`${CONTEXT_PANEL_ID}-title`}
                aria-describedby={`${CONTEXT_PANEL_ID}-description`}
                aria-live="polite"
                aria-busy={contextLoading}
                tabIndex={-1}
                className="arc-popover fixed inset-x-3 top-[3.75rem] z-50 max-h-[min(32rem,calc(100dvh-5rem))] overflow-y-auto overscroll-contain p-4 outline-none sm:absolute sm:inset-x-auto sm:right-0 sm:top-10 sm:w-[22rem]"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2
                      id={`${CONTEXT_PANEL_ID}-title`}
                      className="truncate text-sm font-semibold text-foreground"
                    >
                      {t("appShell.contextPanel.title")}
                    </h2>
                    <p
                      id={`${CONTEXT_PANEL_ID}-description`}
                      className="mt-1 text-[11px] leading-4 text-muted-foreground"
                    >
                      {t("appShell.contextPanel.description")}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => closeContext()}
                    className="arc-icon-button -mt-1 h-7 w-7"
                    aria-label={t("appShell.contextPanel.close")}
                  >
                    <X className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                </div>

                {contextLoading && !contextStatus ? (
                  <div className="mt-4 flex items-center gap-2 rounded-lg border border-border/60 bg-muted/35 px-3 py-2.5 text-xs text-muted-foreground">
                    <LoaderCircle className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                    <span>{t("appShell.contextPanel.loading")}</span>
                  </div>
                ) : null}

                {contextError ? (
                  <div className="mt-4 rounded-lg border border-destructive/25 bg-destructive/5 p-3">
                    <div className="flex items-start gap-2 text-xs text-destructive">
                      <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <p className="min-w-0 flex-1 leading-5">
                        {errorMessage(t, contextError)}
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-2 h-7 w-full text-[11px]"
                      onClick={handleRetry}
                      disabled={contextLoading || contextResetting}
                    >
                      <RefreshCw className="h-3 w-3" aria-hidden="true" />
                      {t("appShell.contextPanel.retry")}
                    </Button>
                  </div>
                ) : null}

                {contextStatus ? (
                  <>
                    <div className="mt-4 flex items-center justify-between gap-3">
                      <span className="text-xs text-muted-foreground">
                        {t("appShell.contextPanel.status")}
                      </span>
                      <span
                        className={cn(
                          "inline-flex max-w-[65%] items-center gap-1.5 rounded-full border px-2 py-1 text-[11px] font-medium",
                          stateTone[contextStatus.state],
                        )}
                      >
                        <span
                          className="h-1.5 w-1.5 rounded-full bg-current"
                          aria-hidden="true"
                        />
                        <span className="truncate">{statusLabel}</span>
                      </span>
                    </div>

                    <dl className="mt-3 space-y-2.5 text-xs">
                      <ContextRow
                        label={t("appShell.contextPanel.retainedMessages")}
                        value={formatNumber(contextStatus.messageCount)}
                      />
                      <ContextRow
                        label={t("appShell.contextPanel.compactionSummary")}
                        value={
                          contextStatus.summaryPresent
                            ? t("appShell.contextPanel.summaryPresent")
                            : t("appShell.contextPanel.summaryAbsent")
                        }
                        valueClassName="inline-flex items-center justify-end gap-1"
                      />
                      <ContextRow
                        label={t("appShell.contextPanel.compactionThreshold")}
                        value={formatNumber(contextStatus.triggerTokens)}
                      />
                      <ContextRow
                        label={t("appShell.contextPanel.revision")}
                        value={formatNumber(contextStatus.revision)}
                      />
                      <ContextRow
                        label={t("appShell.contextPanel.updatedAt")}
                        value={formatTime(contextStatus.updatedAt)}
                      />
                      <ContextRow
                        label={t("appShell.contextPanel.resetAt")}
                        value={formatTime(contextStatus.resetAt)}
                      />
                    </dl>

                    <section className="mt-4 border-t border-border/60 pt-3">
                      <h3 className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                        {t("appShell.contextPanel.tokenUsage.title")}
                      </h3>
                      {contextStatus.lastRunTokenUsage ? (
                        <dl className="mt-2.5 space-y-2.5 text-xs">
                          <ContextRow
                            label={t("appShell.contextPanel.tokenUsage.input")}
                            value={formatNumber(
                              contextStatus.lastRunTokenUsage.inputTokens,
                            )}
                          />
                          <ContextRow
                            label={t("appShell.contextPanel.tokenUsage.output")}
                            value={formatNumber(
                              contextStatus.lastRunTokenUsage.outputTokens,
                            )}
                          />
                          <ContextRow
                            label={t("appShell.contextPanel.tokenUsage.cached")}
                            value={formatNumber(
                              contextStatus.lastRunTokenUsage.cachedTokens ?? 0,
                            )}
                          />
                          <ContextRow
                            label={t("appShell.contextPanel.tokenUsage.total")}
                            value={formatNumber(
                              contextStatus.lastRunTokenUsage.totalTokens,
                            )}
                            valueClassName="font-semibold"
                          />
                        </dl>
                      ) : (
                        <p className="mt-2 text-xs text-muted-foreground">
                          {t("appShell.contextPanel.tokenUsage.empty")}
                        </p>
                      )}
                    </section>

                    {contextLoading ? (
                      <div className="mt-3 flex items-center gap-1.5 text-[11px] text-muted-foreground">
                        <LoaderCircle className="h-3 w-3 animate-spin" aria-hidden="true" />
                        {t("appShell.contextPanel.refreshing")}
                      </div>
                    ) : null}

                    <div className="mt-4 border-t border-border/60 pt-3">
                      <Button
                        type="button"
                        variant={isRunning ? "secondary" : "outline"}
                        size="sm"
                        className="h-8 w-full text-xs"
                        onClick={() => setResetDialogOpen(true)}
                        disabled={contextResetting || isRunning}
                        title={
                          isRunning
                            ? t("appShell.contextPanel.resetWhileRunning")
                            : undefined
                        }
                      >
                        {contextResetting ? (
                          <LoaderCircle className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                        ) : (
                          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                        )}
                        {contextResetting
                          ? t("appShell.contextPanel.resetting")
                          : t("appShell.contextPanel.reset")}
                      </Button>
                      {isRunning ? (
                        <p className="mt-2 text-[11px] leading-4 text-muted-foreground">
                          {t("appShell.contextPanel.resetHint")}
                        </p>
                      ) : null}
                    </div>
                  </>
                ) : null}

                {!contextStatus && !contextLoading && !contextError ? (
                  <div className="mt-4 rounded-lg border border-border/60 bg-muted/30 p-3 text-xs leading-5 text-muted-foreground">
                    {t("appShell.contextPanel.unavailable")}
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>

          <button
            type="button"
            onClick={changeTheme}
            className="arc-icon-button"
            title={
              isDarkMode
                ? t("appShell.header.switchToLight")
                : t("appShell.header.switchToDark")
            }
            aria-label={
              isDarkMode
                ? t("appShell.header.switchToLight")
                : t("appShell.header.switchToDark")
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
            title={t("appShell.header.settings")}
            aria-label={t("appShell.header.settings")}
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
                  ? t("appShell.header.collapseProjectTools")
                  : t("appShell.header.openProjectTools")
              }
              aria-label={
                dockOpen
                  ? t("appShell.header.collapseProjectTools")
                  : t("appShell.header.openProjectTools")
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

      <ConfirmDialog
        open={resetDialogOpen}
        title={t("appShell.contextPanel.resetConfirm.title")}
        description={t("appShell.contextPanel.resetConfirm.description")}
        confirmLabel={t("appShell.contextPanel.resetConfirm.confirm")}
        cancelLabel={t("appShell.contextPanel.resetConfirm.cancel")}
        loading={contextResetting}
        onClose={() => setResetDialogOpen(false)}
        onConfirm={() => void handleConfirmReset()}
      />
    </>
  );
}
