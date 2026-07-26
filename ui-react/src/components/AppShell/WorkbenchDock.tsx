import { lazy, Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Braces,
  Code2,
  Eye,
  FileCode2,
  GitCompareArrows,
  PanelRightClose,
  Plus,
} from "lucide-react";
import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { cn } from "@/utils/cn";

const PreviewIframe = lazy(() => import("@/components/PreviewIframe"));
const WeAPI = lazy(() => import("@/components/WeAPI"));
const WeIde = lazy(() => import("@/components/WeIde"));

type WorkbenchTab = "workspace" | "preview" | "api" | "changes";

type WorkbenchDockProps = {
  open: boolean;
  width: number;
  onWidthChange: (width: number) => void;
  onClose: () => void;
};

const tabs: Array<{
  id: WorkbenchTab;
  labelKey: string;
  defaultLabel: string;
  icon: typeof Code2;
}> = [
  {
    id: "workspace",
    labelKey: "appShell.dock.tabs.workspace",
    defaultLabel: "工作区",
    icon: Code2,
  },
  {
    id: "preview",
    labelKey: "appShell.dock.tabs.preview",
    defaultLabel: "预览",
    icon: Eye,
  },
  {
    id: "api",
    labelKey: "appShell.dock.tabs.api",
    defaultLabel: "API",
    icon: Braces,
  },
  {
    id: "changes",
    labelKey: "appShell.dock.tabs.changes",
    defaultLabel: "变更",
    icon: GitCompareArrows,
  },
];

function ChangesPanel({ onOpenFile }: { onOpenFile: (path: string) => void }) {
  const { t } = useTranslation();
  const files = useFileStore((state) => state.files);
  const oldFiles = useFileStore((state) => state.oldFiles);
  const changes = useMemo(() => {
    const allPaths = new Set([...Object.keys(oldFiles), ...Object.keys(files)]);
    return Array.from(allPaths)
      .map((path) => {
        const before = oldFiles[path];
        const after = files[path];
        if (before === after) return null;
        const status =
          before === undefined
            ? "added"
            : after === undefined
              ? "deleted"
              : "modified";
        const beforeLines = before?.split("\n").length ?? 0;
        const afterLines = after?.split("\n").length ?? 0;
        return {
          path,
          status,
          delta: afterLines - beforeLines,
        };
      })
      .filter(Boolean) as Array<{
      path: string;
      status: "added" | "deleted" | "modified";
      delta: number;
    }>;
  }, [files, oldFiles]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0 border-b border-border/60 px-4 py-3">
        <div className="text-xs font-semibold text-foreground">
          {t("appShell.dock.changes.title", { defaultValue: "生成文件变更" })}
        </div>
        <div className="mt-1 text-[11px] text-muted-foreground">
          {t("appShell.dock.changes.description", {
            defaultValue: "基于本次生成前后的工作区快照；尚未连接 Git 后端。",
          })}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {changes.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center px-6 text-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-muted/75 text-muted-foreground">
              <GitCompareArrows className="h-5 w-5" />
            </div>
            <div className="mt-3 text-sm font-medium text-foreground">
              {t("appShell.dock.changes.emptyTitle", {
                defaultValue: "没有待查看的变更",
              })}
            </div>
            <div className="mt-1 max-w-xs text-xs leading-5 text-muted-foreground">
              {t("appShell.dock.changes.emptyDescription", {
                defaultValue: "Agent 写入或编辑文件后，变更会出现在这里。",
              })}
            </div>
          </div>
        ) : (
          <div className="space-y-1">
            {changes.map((change) => {
              const fileName = change.path.split("/").pop() || change.path;
              const directory = change.path.slice(
                0,
                Math.max(0, change.path.length - fileName.length),
              );
              return (
                <button
                  key={change.path}
                  type="button"
                  onClick={() => onOpenFile(change.path)}
                  className="group flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-foreground/[0.055]"
                >
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-muted/75 text-muted-foreground group-hover:text-foreground">
                    <FileCode2 className="h-3.5 w-3.5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-xs font-medium text-foreground/90">
                      {fileName}
                    </div>
                    <div className="mt-0.5 truncate text-[10px] text-muted-foreground">
                      {directory || "/"}
                    </div>
                  </div>
                  <span
                    className={cn(
                      "rounded-md px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide",
                      change.status === "added" &&
                        "bg-emerald-500/10 text-emerald-600 dark:text-emerald-300",
                      change.status === "modified" &&
                        "bg-amber-500/10 text-amber-600 dark:text-amber-300",
                      change.status === "deleted" &&
                        "bg-destructive/10 text-destructive",
                    )}
                  >
                    {change.status === "added"
                      ? "A"
                      : change.status === "modified"
                        ? "M"
                        : "D"}
                  </span>
                  <span
                    className={cn(
                      "min-w-8 text-right text-[10px] tabular-nums",
                      change.delta > 0
                        ? "text-emerald-600 dark:text-emerald-300"
                        : change.delta < 0
                          ? "text-destructive"
                          : "text-muted-foreground",
                    )}
                  >
                    {change.delta > 0 ? `+${change.delta}` : change.delta}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export function WorkbenchDock({
  open,
  width,
  onWidthChange,
  onClose,
}: WorkbenchDockProps) {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<WorkbenchTab>(() => {
    const stored = localStorage.getItem(
      "arc-workbench-tab",
    ) as WorkbenchTab | null;
    return tabs.some((tab) => tab.id === stored) ? stored! : "workspace";
  });
  const [mountedTabs, setMountedTabs] = useState<Set<WorkbenchTab>>(
    () => new Set([activeTab]),
  );
  const files = useFileStore((state) => state.files);
  const oldFiles = useFileStore((state) => state.oldFiles);
  const changeCount = useMemo(() => {
    const allPaths = new Set([...Object.keys(oldFiles), ...Object.keys(files)]);
    let count = 0;
    allPaths.forEach((path) => {
      if (oldFiles[path] !== files[path]) count += 1;
    });
    return count;
  }, [files, oldFiles]);
  const isMiniProgram = Object.keys(files).some(
    (path) => path === "app.json" || path.endsWith("/app.json"),
  );

  const selectTab = (tab: WorkbenchTab) => {
    setActiveTab(tab);
    setMountedTabs((current) => {
      if (current.has(tab)) return current;
      const next = new Set(current);
      next.add(tab);
      return next;
    });
    localStorage.setItem("arc-workbench-tab", tab);
  };

  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    const onMove = (moveEvent: PointerEvent) => {
      const next = Math.min(
        720,
        Math.max(360, window.innerWidth - moveEvent.clientX),
      );
      onWidthChange(next);
    };
    const onUp = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
  };

  const openFile = (path: string) => {
    selectTab("workspace");
    window.requestAnimationFrame(() => {
      window.dispatchEvent(new CustomEvent("openFile", { detail: { path } }));
    });
  };

  return (
    <>
      {open ? (
        <button
          type="button"
          aria-label={t("appShell.dock.close", {
            defaultValue: "关闭项目工具",
          })}
          onClick={onClose}
          className="fixed inset-0 z-40 bg-black/25 backdrop-blur-[1px] min-[1100px]:hidden"
        />
      ) : null}
      <section
        className={cn(
          "arc-workbench fixed inset-y-0 right-0 z-50 flex h-full shrink-0 overflow-hidden border-l border-border/60 transition-[width,opacity,transform] duration-200 ease-out min-[1100px]:relative min-[1100px]:z-20",
          open
            ? "translate-x-0 opacity-100 max-[1099px]:!w-[92vw] max-[1099px]:max-w-[680px]"
            : "pointer-events-none translate-x-8 opacity-0 max-[1099px]:!w-0",
        )}
        style={{ width: open ? width : 0 }}
        aria-hidden={!open}
      >
        <div
          role="separator"
          aria-orientation="vertical"
          aria-label={t("appShell.dock.resize", {
            defaultValue: "调整项目工具宽度",
          })}
          onPointerDown={startResize}
          className="absolute inset-y-0 left-0 z-30 hidden w-1 -translate-x-1/2 cursor-col-resize transition-colors hover:bg-foreground/15 min-[1100px]:block"
        />
        <div className="flex h-full min-w-0 flex-1 flex-col" style={{ width }}>
          <div className="flex h-11 shrink-0 items-center border-b border-border/60 px-1.5">
            <div className="project-tools-panel-tabs flex min-w-0 flex-1 items-center gap-0.5 overflow-x-auto">
              {tabs.map((tab) => {
                const Icon = tab.icon;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => selectTab(tab.id)}
                    className={cn(
                      "inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg px-2.5 text-[11px] font-medium transition-colors",
                      activeTab === tab.id
                        ? "bg-workbench-active text-foreground"
                        : "text-muted-foreground hover:bg-foreground/[0.05] hover:text-foreground",
                    )}
                  >
                    <Icon className="h-3.5 w-3.5" />
                    {t(tab.labelKey, { defaultValue: tab.defaultLabel })}
                    {tab.id === "changes" ? (
                      <span className="ml-0.5 rounded-full bg-foreground/[0.07] px-1.5 text-[9px] tabular-nums">
                        {changeCount}
                      </span>
                    ) : null}
                  </button>
                );
              })}
            </div>
            <button
              type="button"
              className="arc-icon-button h-7 w-7"
              title={t("appShell.dock.addPending", {
                defaultValue: "添加项目工具（接口待接入）",
              })}
              aria-label={t("appShell.dock.add", {
                defaultValue: "添加项目工具",
              })}
              disabled
            >
              <Plus className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              className="arc-icon-button h-7 w-7"
              onClick={onClose}
              title={t("appShell.dock.collapse", {
                defaultValue: "收起项目工具",
              })}
              aria-label={t("appShell.dock.collapse", {
                defaultValue: "收起项目工具",
              })}
            >
              <PanelRightClose className="h-3.5 w-3.5" />
            </button>
          </div>

          <div className="relative min-h-0 flex-1 overflow-hidden bg-workbench-panel">
            {mountedTabs.has("workspace") ? (
              <div
                className={cn(
                  "absolute inset-0",
                  activeTab === "workspace" ? "block" : "hidden",
                )}
              >
                <Suspense fallback={null}>
                  <WeIde />
                </Suspense>
              </div>
            ) : null}
            {mountedTabs.has("preview") ? (
              <div
                className={cn(
                  "absolute inset-0",
                  activeTab === "preview" ? "block" : "hidden",
                )}
              >
                <Suspense fallback={null}>
                  <PreviewIframe
                    isMinPrograme={isMiniProgram}
                    setShowIframe={() => undefined}
                  />
                </Suspense>
              </div>
            ) : null}
            {mountedTabs.has("api") ? (
              <div
                className={cn(
                  "absolute inset-0",
                  activeTab === "api" ? "block" : "hidden",
                )}
              >
                <Suspense fallback={null}>
                  <WeAPI />
                </Suspense>
              </div>
            ) : null}
            <div
              className={cn(
                "absolute inset-0",
                activeTab === "changes" ? "block" : "hidden",
              )}
            >
              <ChangesPanel onOpenFile={openFile} />
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
