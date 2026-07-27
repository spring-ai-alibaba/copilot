import React, { type ReactNode, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Divider } from "antd";
import type { ThemeMode } from "antd-style";
import {
  ArrowLeft,
  Brain,
  Cable,
  Cpu,
  Database,
  FileText,
  Settings2,
  X,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import styled from "styled-components";
import KnowledgeSettings from "@/components/Settings/KnowledgeSettings";
import MCPSettings from "@/components/Settings/MCPSettings";
import MemorySettings from "@/components/Settings/MemorySettings";
import ModelSettings from "@/components/Settings/ModelSettings";
import PromptSettings from "@/components/Settings/PromptSettings";
import { AppLogo } from "@/components/AppLogo";
import { cn } from "@/utils/cn";
import { GeneralSettings } from "./GeneralSettings";

export type SettingsTab = "General" | "MCPServer" | "Knowledge" | "Models" | "Prompts" | "Memory";

export const TAB_KEYS = {
  GENERAL: "General" as const,
  MCPServer: "MCPServer" as const,
  Knowledge: "Knowledge" as const,
  Models: "Models" as const,
  Prompts: "Prompts" as const,
  Memory: "Memory" as const,
} as const;

interface SettingsProps {
  isOpen: boolean;
  onClose: () => void;
  initialTab?: SettingsTab;
}

type TabDefinition = {
  id: SettingsTab;
  label: string;
  description: string;
  icon: ReactNode;
  group: "basic" | "intelligence";
};

function SettingsNavItem({
  item,
  active,
  onClick,
}: {
  item: TabDefinition;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      data-active={active}
      className={cn(
        "group flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors max-md:justify-center max-md:gap-0 max-md:px-0",
        active
          ? "bg-foreground/[0.075] font-medium text-foreground"
          : "text-muted-foreground hover:bg-foreground/[0.045] hover:text-foreground",
      )}
    >
      <span
        className={cn(
          "flex h-6 w-6 shrink-0 items-center justify-center rounded-md transition-colors [&>svg]:h-3.5 [&>svg]:w-3.5",
          active
            ? "bg-background text-foreground shadow-sm"
            : "bg-foreground/[0.045] text-muted-foreground group-hover:text-foreground",
        )}
      >
        {item.icon}
      </span>
      <span className="truncate max-md:hidden">{item.label}</span>
    </button>
  );
}

export function Settings({ isOpen, onClose, initialTab = TAB_KEYS.GENERAL }: SettingsProps) {
  const [mounted, setMounted] = useState(isOpen);
  const [visible, setVisible] = useState(false);
  const [activeTab, setActiveTab] = useState<SettingsTab>(initialTab);
  const { t } = useTranslation();

  const tabs = useMemo<TabDefinition[]>(
    () => [
      {
        id: TAB_KEYS.GENERAL,
        label: t("settings.general", { defaultValue: "通用" }),
        description: t("settings.shell.generalDescription", {
          defaultValue: "外观、语言与应用行为",
        }),
        icon: <Settings2 />,
        group: "basic",
      },
      {
        id: TAB_KEYS.Models,
        label: t("settings.Models", { defaultValue: "模型" }),
        description: t("settings.shell.modelsDescription", {
          defaultValue: "模型服务与默认参数",
        }),
        icon: <Cpu />,
        group: "basic",
      },
      {
        id: TAB_KEYS.MCPServer,
        label: t("settings.MCPServer", { defaultValue: "MCP 服务" }),
        description: t("settings.shell.mcpDescription", {
          defaultValue: "外部工具与服务连接",
        }),
        icon: <Cable />,
        group: "intelligence",
      },
      {
        id: TAB_KEYS.Knowledge,
        label: t("settings.Knowledge", { defaultValue: "知识库" }),
        description: t("settings.shell.knowledgeDescription", {
          defaultValue: "知识来源与检索配置",
        }),
        icon: <Database />,
        group: "intelligence",
      },
      {
        id: TAB_KEYS.Prompts,
        label: t("settings.Prompts", { defaultValue: "提示词" }),
        description: t("settings.shell.promptsDescription", {
          defaultValue: "可复用的任务指令",
        }),
        icon: <FileText />,
        group: "intelligence",
      },
      {
        id: TAB_KEYS.Memory,
        label: t("settings.Memory", { defaultValue: "记忆" }),
        description: t("settings.shell.memoryDescription", {
          defaultValue: "偏好学习与长期记忆",
        }),
        icon: <Brain />,
        group: "intelligence",
      },
    ],
    [t],
  );

  useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab]);

  useEffect(() => {
    if (isOpen) {
      setMounted(true);
      const frame = requestAnimationFrame(() => setVisible(true));
      return () => cancelAnimationFrame(frame);
    }
    setVisible(false);
    const timer = window.setTimeout(() => setMounted(false), 220);
    return () => window.clearTimeout(timer);
  }, [isOpen]);

  useEffect(() => {
    if (!mounted) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [mounted, onClose]);

  if (!mounted) return null;

  const activeDefinition = tabs.find((tab) => tab.id === activeTab) ?? tabs[0];
  const groups = [
    {
      id: "basic" as const,
      label: t("settings.shell.basicGroup", { defaultValue: "基础" }),
    },
    {
      id: "intelligence" as const,
      label: t("settings.shell.intelligenceGroup", {
        defaultValue: "智能能力",
      }),
    },
  ];

  const content = (() => {
    switch (activeTab) {
      case TAB_KEYS.GENERAL:
        return <GeneralSettings />;
      case TAB_KEYS.MCPServer:
        return <MCPSettings />;
      case TAB_KEYS.Knowledge:
        return <KnowledgeSettings />;
      case TAB_KEYS.Models:
        return <ModelSettings />;
      case TAB_KEYS.Prompts:
        return <PromptSettings />;
      case TAB_KEYS.Memory:
        return <MemorySettings />;
      default: {
        const unreachable: never = activeTab;
        return unreachable;
      }
    }
  })();

  return createPortal(
    <div
      className={cn(
        "arc-settings-page fixed inset-0 z-[10000] flex bg-background text-foreground transition-[opacity,transform] duration-200 ease-out",
        visible ? "translate-y-0 opacity-100" : "translate-y-5 opacity-0",
      )}
      aria-hidden={!visible}
    >
      <aside className="flex w-[248px] shrink-0 flex-col border-r border-border/60 bg-sidebar max-md:w-[76px]">
        <div className="border-b border-border/60 p-3">
          <button
            type="button"
            onClick={onClose}
            className="arc-sidebar-row mb-3 max-md:justify-center max-md:px-0"
          >
            <ArrowLeft className="h-4 w-4 shrink-0" />
            <span className="max-md:hidden">
              {t("settings.shell.backToChat", { defaultValue: "返回对话" })}
            </span>
          </button>
          <div className="flex items-center gap-2.5 px-1.5 max-md:justify-center max-md:px-0">
            <AppLogo />
            <div className="min-w-0 max-md:hidden">
              <div className="truncate text-sm font-semibold">
                {t("settings.title", { defaultValue: "设置" })}
              </div>
              <div className="mt-0.5 text-[10px] uppercase tracking-[0.14em] text-muted-foreground">
                Alibaba Copilot
              </div>
            </div>
          </div>
        </div>

        <nav className="min-h-0 flex-1 overflow-y-auto px-3 py-3 max-md:px-2">
          {groups.map((group, groupIndex) => (
            <div key={group.id} className={groupIndex ? "mt-5" : ""}>
              <div className="mb-1 px-2.5 text-[10px] font-semibold uppercase tracking-[0.15em] text-muted-foreground/60 max-md:hidden">
                {group.label}
              </div>
              <div className="space-y-0.5">
                {tabs
                  .filter((tab) => tab.group === group.id)
                  .map((tab) => (
                    <div key={tab.id} title={tab.label}>
                      <SettingsNavItem
                        item={tab}
                        active={activeTab === tab.id}
                        onClick={() => setActiveTab(tab.id)}
                      />
                    </div>
                  ))}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-border/60 px-4 py-3 text-[10px] leading-4 text-muted-foreground max-md:hidden">
          {t("settings.shell.storageNote", {
            defaultValue:
              "设置会保存在当前浏览器中；需要后端同步的项目已在接口文档中标记。",
          })}
        </div>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-[72px] shrink-0 items-center justify-between gap-4 border-b border-border/60 px-7 max-sm:px-4">
          <div className="min-w-0">
            <h1 className="truncate text-base font-semibold tracking-tight">{activeDefinition.label}</h1>
            <p className="mt-1 truncate text-xs text-muted-foreground">
              {activeDefinition.description}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="arc-icon-button shrink-0"
            aria-label={t("common.close", { defaultValue: "关闭" })}
            title={t("common.close", { defaultValue: "关闭" })}
          >
            <X className="h-4 w-4" />
          </button>
        </header>

        <div key={activeTab} className="settings-section-enter min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-5xl px-7 py-6 max-sm:px-4">{content}</div>
        </div>
      </main>
    </div>,
    document.body,
  );
}

export const SettingRow = styled.div`
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
`;

export const SettingTitle = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 600;
  user-select: none;
`;

export const SettingSubtitle = styled.div`
  margin: 18px 0 8px;
  color: hsl(var(--foreground));
  font-size: 13px;
  font-weight: 600;
  user-select: none;
`;

export const SettingGroup = styled.div<{ theme?: ThemeMode }>`
  margin-bottom: 16px;
  border: 1px solid hsl(var(--border) / 0.72);
  border-radius: 12px;
  padding: 16px;
  background: hsl(var(--card) / 0.68);
`;

export const SettingContainer = styled.div<{ theme?: ThemeMode }>`
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 100%;
  color: hsl(var(--foreground));
  font-family: inherit;
`;

export const SettingDivider = styled(Divider)`
  margin: 12px 0 !important;
  border-block-start-color: hsl(var(--border) / 0.72) !important;
`;
