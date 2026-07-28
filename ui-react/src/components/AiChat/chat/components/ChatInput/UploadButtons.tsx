import React, { forwardRef, useEffect, useRef, useState } from "react";
import {
  Check,
  ChevronDown,
  CirclePlay,
  Code2,
  FileArchive,
  Image,
  ListTodo,
  MessageSquare,
  Plus,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import useChatStore from "@/stores/chatSlice";
import useChatModeStore, {
  ChatMode,
  ExecutionMode,
} from "@/stores/chatModeSlice";
import { cn } from "@/utils/cn";
import type { IModelOption } from "../..";
import MCPToolsButton from "./MCPToolsButton";
import { aiProvierIcon } from "./config";
import type { UploadButtonsProps } from "./types";

const ToolbarButton = forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement>>(
  ({ className, children, ...props }, ref) => (
    <button
      ref={ref}
      type="button"
      className={cn("arc-composer-control", className)}
      {...props}
    >
      {children}
    </button>
  ),
);
ToolbarButton.displayName = "ToolbarButton";

export const UploadButtons: React.FC<UploadButtonsProps> = ({
  isLoading,
  isUploading,
  onImageClick,
  onSketchClick,
  baseModal,
  setBaseModal,
}) => {
  const [addMenuOpen, setAddMenuOpen] = useState(false);
  const [modelMenuOpen, setModelMenuOpen] = useState(false);
  const [executionMenuOpen, setExecutionMenuOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const { t } = useTranslation();
  const { modelOptions, clearImages } = useChatStore();
  const { mode, setMode, executionMode, setExecutionMode } = useChatModeStore();
  const canUseMCP = Boolean(baseModal.functionCall);

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (rootRef.current?.contains(event.target as Node)) return;
      setAddMenuOpen(false);
      setModelMenuOpen(false);
      setExecutionMenuOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setAddMenuOpen(false);
      setModelMenuOpen(false);
      setExecutionMenuOpen(false);
    };
    window.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  const handleModelSelect = (model: IModelOption) => {
    setBaseModal(model);
    clearImages();
    setModelMenuOpen(false);
  };

  const ProviderIcon = aiProvierIcon[(baseModal.provider || "").toLowerCase()];
  const controlsDisabled = isLoading || isUploading;

  return (
    <div ref={rootRef} className="flex min-w-0 items-center gap-1">
      <div className="relative">
        <ToolbarButton
          onClick={() => {
            setAddMenuOpen((current) => !current);
            setModelMenuOpen(false);
            setExecutionMenuOpen(false);
          }}
          disabled={controlsDisabled}
          aria-label={t("chat.buttons.addContext", { defaultValue: "添加上下文" })}
          title={t("chat.buttons.addContext", { defaultValue: "添加上下文" })}
          data-active={addMenuOpen}
        >
          <Plus className="h-4 w-4" />
        </ToolbarButton>

        {addMenuOpen ? (
          <div className="arc-popover absolute bottom-10 left-0 z-50 w-56 p-1.5">
            <div className="px-2 pb-1.5 pt-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground/65">
              {t("chat.buttons.addToConversation", { defaultValue: "添加到对话" })}
            </div>
            <button
              type="button"
              onClick={() => {
                setAddMenuOpen(false);
                onImageClick();
              }}
              disabled={controlsDisabled || !baseModal.useImage}
              className="arc-popover-item"
            >
              <Image className="h-4 w-4" />
              <span className="min-w-0 flex-1">
                <span className="block text-xs">
                  {t("chat.buttons.upload_image", { defaultValue: "上传图片" })}
                </span>
                <span className="mt-0.5 block text-[10px] text-muted-foreground">
                  PNG、JPG、WebP
                </span>
              </span>
            </button>
            <button
              type="button"
              onClick={() => {
                setAddMenuOpen(false);
                onSketchClick();
              }}
              disabled={controlsDisabled}
              className="arc-popover-item"
            >
              <FileArchive className="h-4 w-4" />
              <span className="min-w-0 flex-1">
                <span className="block text-xs">
                  {t("chat.buttons.importSketch", { defaultValue: "导入 Sketch" })}
                </span>
                <span className="mt-0.5 block text-[10px] text-muted-foreground">
                  {t("chat.buttons.designContext", {
                    defaultValue: "生成设计上下文",
                  })}
                </span>
              </span>
            </button>
            <div className="mx-1 my-1 h-px bg-border/70" />
            <div className="px-2 py-1 text-[10px] leading-4 text-muted-foreground">
              {t("chat.buttons.workspaceMention", {
                defaultValue: "输入 @ 可引用工作区文件",
              })}
            </div>
          </div>
        ) : null}
      </div>

      <MCPToolsButton ToolbarButton={ToolbarButton} disabled={!canUseMCP || controlsDisabled} />

      <div className="relative min-w-0">
        <button
          type="button"
          onClick={() => {
            setModelMenuOpen((current) => !current);
            setAddMenuOpen(false);
            setExecutionMenuOpen(false);
          }}
          className={cn(
            "arc-composer-model max-w-[210px]",
            modelMenuOpen && "bg-foreground/[0.065] text-foreground",
          )}
          aria-expanded={modelMenuOpen}
          title={baseModal.name}
        >
          {ProviderIcon ? <ProviderIcon className="h-3.5 w-3.5 shrink-0" /> : null}
          <span className="truncate">
            {baseModal.name ||
              t("chat.buttons.selectModel", { defaultValue: "选择模型" })}
          </span>
          <ChevronDown
            className={cn(
              "h-3 w-3 shrink-0 text-muted-foreground transition-transform",
              modelMenuOpen && "rotate-180",
            )}
          />
        </button>

        {modelMenuOpen ? (
          <div className="arc-popover absolute bottom-10 left-0 z-50 w-[280px] max-w-[calc(100vw-32px)] p-1.5">
            <div className="mb-1.5 grid grid-cols-2 gap-1 rounded-lg bg-muted/65 p-1">
              <button
                type="button"
                onClick={() => setMode("chat" as Parameters<typeof setMode>[0])}
                className={cn(
                  "flex h-8 items-center justify-center gap-1.5 rounded-md text-[11px] font-medium transition-colors",
                  mode === "chat"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <MessageSquare className="h-3.5 w-3.5" />
                Chat
              </button>
              <button
                type="button"
                onClick={() => setMode("builder" as Parameters<typeof setMode>[0])}
                className={cn(
                  "flex h-8 items-center justify-center gap-1.5 rounded-md text-[11px] font-medium transition-colors",
                  mode === "builder"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <Code2 className="h-3.5 w-3.5" />
                Builder
              </button>
            </div>
            <div className="px-2 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground/65">
              {t("chat.buttons.availableModels", { defaultValue: "可用模型" })}
            </div>
            <div className="max-h-64 overflow-y-auto">
              {modelOptions.length ? (
                modelOptions.map((model, index) => {
                  const Icon = aiProvierIcon[(model.provider || "").toLowerCase()];
                  const selected = baseModal.key === model.key;
                  return (
                    <button
                      key={model.key || `model-${index}`}
                      type="button"
                      onClick={() => handleModelSelect(model as IModelOption)}
                      className="arc-popover-item"
                    >
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-muted/70">
                        {Icon ? <Icon className="h-3.5 w-3.5" /> : <Code2 className="h-3.5 w-3.5" />}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-xs">{model.name}</span>
                      {selected ? <Check className="h-3.5 w-3.5" /> : null}
                    </button>
                  );
                })
              ) : (
                <div className="px-3 py-5 text-center text-xs text-muted-foreground">
                  {t("chat.models.empty", { defaultValue: "暂无可用模型，请先在设置中配置。" })}
                </div>
              )}
            </div>
          </div>
        ) : null}
      </div>

      {mode === ChatMode.Builder ? (
        <div className="relative shrink-0">
          <button
            type="button"
            onClick={() => {
              setExecutionMenuOpen((current) => !current);
              setAddMenuOpen(false);
              setModelMenuOpen(false);
            }}
            disabled={controlsDisabled}
            className={cn(
              "arc-composer-model",
              executionMode === ExecutionMode.Plan &&
                "bg-amber-500/10 text-amber-700 hover:bg-amber-500/15 dark:text-amber-300",
              executionMenuOpen && "bg-foreground/[0.065]",
            )}
            aria-expanded={executionMenuOpen}
            aria-label={t("chat.planMode.selector", {
              defaultValue: "选择执行模式",
            })}
            title={
              executionMode === ExecutionMode.Plan
                ? t("chat.planMode.planDescription", {
                    defaultValue: "先规划，审批后执行",
                  })
                : t("chat.planMode.executeDescription", {
                    defaultValue: "直接分析并执行任务",
                  })
            }
          >
            {executionMode === ExecutionMode.Plan ? (
              <ListTodo className="h-3.5 w-3.5 shrink-0" />
            ) : (
              <CirclePlay className="h-3.5 w-3.5 shrink-0" />
            )}
            <span>
              {executionMode === ExecutionMode.Plan
                ? t("chat.planMode.plan", { defaultValue: "计划" })
                : t("chat.planMode.execute", { defaultValue: "执行" })}
            </span>
            <ChevronDown
              className={cn(
                "h-3 w-3 shrink-0 text-muted-foreground transition-transform",
                executionMenuOpen && "rotate-180",
              )}
            />
          </button>

          {executionMenuOpen ? (
            <div className="arc-popover absolute bottom-10 left-0 z-50 w-[280px] max-w-[calc(100vw-32px)] p-1.5">
              <div className="px-2 pb-1.5 pt-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground/65">
                {t("chat.planMode.title", { defaultValue: "任务执行方式" })}
              </div>
              <button
                type="button"
                onClick={() => {
                  setExecutionMode(ExecutionMode.Execute);
                  setExecutionMenuOpen(false);
                }}
                className="arc-popover-item"
                data-active={executionMode === ExecutionMode.Execute}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-muted/70">
                  <CirclePlay className="h-3.5 w-3.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-xs font-medium">
                    {t("chat.planMode.execute", { defaultValue: "执行模式" })}
                  </span>
                  <span className="mt-0.5 block text-[10px] leading-4 text-muted-foreground">
                    {t("chat.planMode.executeDescription", {
                      defaultValue: "直接分析并修改项目",
                    })}
                  </span>
                </span>
                {executionMode === ExecutionMode.Execute ? (
                  <Check className="h-3.5 w-3.5" />
                ) : null}
              </button>
              <button
                type="button"
                onClick={() => {
                  setExecutionMode(ExecutionMode.Plan);
                  setExecutionMenuOpen(false);
                }}
                className="arc-popover-item"
                data-active={executionMode === ExecutionMode.Plan}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-700 dark:text-amber-300">
                  <ListTodo className="h-3.5 w-3.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-xs font-medium">
                    {t("chat.planMode.plan", { defaultValue: "计划模式" })}
                  </span>
                  <span className="mt-0.5 block text-[10px] leading-4 text-muted-foreground">
                    {t("chat.planMode.planDescription", {
                      defaultValue: "只读探索并生成计划，批准后执行",
                    })}
                  </span>
                </span>
                {executionMode === ExecutionMode.Plan ? (
                  <Check className="h-3.5 w-3.5" />
                ) : null}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
};
