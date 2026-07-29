import React, {memo, useCallback, useMemo, useState} from "react";
import ReactMarkdown from "react-markdown";
import {ArtifactView} from "../ArtifactView";
import {ImageGrid} from "../ImageGrid";
import {Message} from "ai";

import classNames from "classnames";
import useThemeStore from "@/stores/themeSlice";
import hljs from "highlight.js/lib/common";
import remarkGfm from "remark-gfm";
import "highlight.js/styles/github.css"; // 亮色主题
import "highlight.js/styles/github-dark.css"; // 暗色主题
import {useTranslation} from 'react-i18next';
import { safeJsonParse } from '@/utils/safeJsonParse';
import { AppLogo } from "@/components/AppLogo";
import {
  CheckCircle2,
  Check,
  Circle,
  CircleDot,
  Code2,
  FileText,
  GitBranch,
  ListTodo,
  LockKeyhole,
  ShieldAlert,
  X,
} from "lucide-react";

const codeStyles = `
  .hljs-attr {
    color: #36ACE3;
  }
  .hljs-string {
    color: #FF6B6B;
  }
  .hljs-number {
    color: #FF9F43;
  }
  .hljs-boolean {
    color: #2ED573;
  }
  .hljs-null {
    color: #A367DC;
  }
  
  .dark .hljs-attr {
    color: #9CDCFE;
  }
  .dark .hljs-string {
    color: #CE9178;
  }
  .dark .hljs-number {
    color: #B5CEA8;
  }
  .dark .hljs-boolean {
    color: #4EC9B0;
  }
  .dark .hljs-null {
    color: #C586C0;
  }
`;

function filterContent(message: Message) {
  let cloneMessage: Message | undefined;
  if (message.role === 'user') {
    cloneMessage = JSON.parse(JSON.stringify(message))
    // 使用正则表达式移除<weD2c>标签及其内容，添加 s 标志以匹配多行内容
    const weD2cRegex = /<weD2c>[\s\S]*?<\/weD2c>/g;
    cloneMessage.content = cloneMessage.content.replace(weD2cRegex, '');
    cloneMessage.parts = cloneMessage.parts?.map(item => {
      if(item.type === 'text'){
        item.text = item.text.replace(weD2cRegex, '')
        return item
      }
      return item
    }) ?? [];
  }
  return cloneMessage ? cloneMessage : message;
}
// 添加处理流式parts的函数
export const processStreamParts = (parts: Message["parts"]): string => {
  let result = "";
  let thinkContent = "";

  // 首先处理所有reasoning类型的内容
  parts?.forEach((part) => {
    if (part.type === "reasoning") {
      thinkContent += part.reasoning;
    }
  });

  // 如果有reasoning内容，将其转换为markdown引用格式
  if (thinkContent) {
    result +=
      thinkContent
        .split("\n")
        .map((line) => `> ${line}`)
        .join("\n") + "\n\n";
  }

  // 添加其他类型的内容
  parts?.forEach((part) => {
    if (part.type === "text") {
      // 检查是否包含think标签，如果有则进行处理
      if (isThinkContent(part.text)) {
        result += processThinkContent(part.text);
      } else {
        result += part.text;
      }
    }
  });

  const artifactIndex = result.indexOf("<boltArtifact");
  const preContent =
    artifactIndex > 0 ? result.substring(0, artifactIndex) : result;
  return preContent.trim();
};

function getDisplayContent(message: Message) {
  const filteredMessage = filterContent(message);
  const streamContent = processStreamParts(filteredMessage.parts);
  const rawContent = typeof filteredMessage.content === "string" ? filteredMessage.content : "";
  const content = streamContent || rawContent;
  const normalizedContent = isThinkContent(content)
    ? processThinkContent(content)
    : content;
  return collapseLiveTimelineBlocks(normalizedContent);
}

const LIVE_TIMELINE_BLOCK_PATTERN =
  /```(arc-reasoning|arc-tool)\n([\s\S]*?)\n```/g;

/**
 * AG-UI 会持续追加 reasoning delta 和同一工具的状态快照。
 * Markdown 本身是追加流，无法回写旧代码块，因此渲染前把它们折叠成：
 * - 一张不断增长的 reasoning 卡片；
 * - 每个 toolCallId 只保留最新状态。
 */
function collapseLiveTimelineBlocks(content: string) {
  const matches = Array.from(content.matchAll(LIVE_TIMELINE_BLOCK_PATTERN));
  if (matches.length < 2) {
    return content;
  }

  const reasoningMatches = matches.filter((match) => match[1] === "arc-reasoning");
  const reasoningContent = reasoningMatches
    .map((match) => decodeTimelinePayload(match[2] || ""))
    .join("");
  const latestReasoningOffset =
    reasoningMatches[reasoningMatches.length - 1]?.index;

  const latestToolOffsets = new Map<string, number>();
  for (const match of matches) {
    if (match[1] !== "arc-tool" || match.index === undefined) continue;
    try {
      const payload = JSON.parse(
        decodeTimelinePayload(match[2] || ""),
      ) as { toolCallId?: string };
      if (payload.toolCallId) {
        latestToolOffsets.set(payload.toolCallId, match.index);
      }
    } catch {
      // 不完整的流式块保持原样，由 Markdown 在后续 chunk 到达后重新解析。
    }
  }

  const pattern = new RegExp(LIVE_TIMELINE_BLOCK_PATTERN.source, "g");
  return content
    .replace(pattern, (block, language, payload, offset: number) => {
      if (language === "arc-reasoning") {
        if (offset !== latestReasoningOffset) return "";
        return `\`\`\`arc-reasoning\n${encodeURIComponent(reasoningContent)}\n\`\`\``;
      }

      try {
        const tool = JSON.parse(
          decodeTimelinePayload(payload),
        ) as { toolCallId?: string };
        if (
          tool.toolCallId &&
          latestToolOffsets.get(tool.toolCallId) !== offset
        ) {
          return "";
        }
      } catch {
        return block;
      }
      return block;
    })
    .replace(/\n{3,}/g, "\n\n");
}

interface MessageItemProps {
  message: Message & {
    experimental_attachments?: Array<{
      id: string;
      name: string;
      type: string;
      localUrl: string;
      contentType: string;
      url: string;
    }>;
  };
  isLoading: boolean;
  isEndMessage: boolean;
  handleRetry: () => void;
  listProgressStates?: Record<string, { filePath: string; content?: string; isLoading: boolean }>;
  onUpdateMessage?: (messageId: string, content: {
    text: string;
    type: string;
  }[]) => void;
  onPlanDecision?: (decision: {
    action: "APPROVE" | "REJECT";
    feedback?: string;
  }) => Promise<void> | void;
  planDecisionState?: {
    conversationId: string;
    action: "APPROVE" | "REJECT";
    status: "submitting" | "running" | "completed" | "failed";
    message?: string;
  } | null;
}

const isArtifactContent = (content: string) => {
  return content.includes("<boltArtifact");
};

const getArtifactTitle = (content: string) => {
  const match = content.match(/title="([^"]+)"/);
  return match ? match[1] : "Task";
};

// 如果生成结束了，user在最后，就要展示重试
const isShowRetry = (isUser: boolean, isLoading: boolean, isEndMessage:boolean) => {
  return isUser && !isLoading && isEndMessage;
};

// 添加图片预览组件
const ImagePreview = ({
  src,
  onClose,
}: {
  src: string;
  onClose: () => void;
}) => {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
      onClick={onClose}
    >
      <div className="relative max-w-[90vw] max-h-[90vh]">
        <img
          src={src}
          alt="Preview"
          className="object-contain max-w-full max-h-[90vh]"
        />
        <button
          className="absolute text-white top-4 right-4 hover:text-gray-300"
          onClick={onClose}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="w-6 h-6"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      </div>
    </div>
  );
};

// 添加自定义样式处理
const customHighlight = (code: string, language: string) => {
  try {
    if (language.toLowerCase() === 'json') {
      // 自定义 JSON 语法高亮
      const jsonStr = code.trim();
      return jsonStr.replace(
        /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
        function (match) {
          let colorClass = 'hljs-string'; // 字符串颜色
          if (/^"/.test(match)) {
            if (/:$/.test(match)) {
              colorClass = 'hljs-attr'; // key 的颜色
            }
          } else if (/true|false/.test(match)) {
            colorClass = 'hljs-boolean'; // 布尔值颜色
          } else if (/null/.test(match)) {
            colorClass = 'hljs-null'; // null 的颜色
          } else {
            colorClass = 'hljs-number'; // 数字颜色
          }
          return `<span class="${colorClass}">${match}</span>`;
        }
      );
    }

    // 其他语言使用 highlight.js
    return hljs.highlight(code.trim(), {
      language: language || "plaintext",
      ignoreIllegals: true,
    }).value;
  } catch (e) {
    return code;
  }
};

function decodeTimelinePayload(content: string) {
  try {
    return decodeURIComponent(content.trim());
  } catch {
    return content.trim();
  }
}

const ReasoningCard = ({ content }: { content: string }) => {
  const [open, setOpen] = useState(false);
  const { t } = useTranslation();
  return (
    <div className="my-3 overflow-hidden rounded-xl border border-border/70 bg-muted/35">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-foreground/[0.035]"
        aria-expanded={open}
      >
        <span className="flex h-6 w-6 items-center justify-center rounded-md bg-background text-muted-foreground shadow-sm">
          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M9.5 4.5a3.5 3.5 0 0 1 5.7 2.72A3.5 3.5 0 0 1 17 13.75V16a2 2 0 0 1-2 2h-1l-2 2-2-2H9a2 2 0 0 1-2-2v-2.25A3.5 3.5 0 0 1 9.5 4.5Z" />
          </svg>
        </span>
        <span className="flex-1 text-xs font-medium text-foreground/85">
          {t("chat.timeline.reasoning", { defaultValue: "思考过程" })}
        </span>
        <span className="text-[10px] text-muted-foreground">
          {open
            ? t("chat.timeline.collapse", { defaultValue: "收起" })
            : t("chat.timeline.expand", { defaultValue: "展开" })}
        </span>
        <svg
          className={classNames("h-3.5 w-3.5 text-muted-foreground transition-transform", open && "rotate-180")}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
      {open ? (
        <div className="whitespace-pre-wrap border-t border-border/60 px-3 py-3 text-xs leading-5 text-muted-foreground">
          {content}
        </div>
      ) : null}
    </div>
  );
};

const RunErrorCard = ({ message: errorMessage }: { message: string }) => {
  const { t } = useTranslation();
  return (
    <div className="my-3 rounded-xl border border-destructive/25 bg-destructive/[0.06] px-3 py-2.5">
      <div className="text-xs font-medium text-destructive">
        {t("chat.timeline.runFailed", { defaultValue: "运行失败" })}
      </div>
      <div className="mt-1 whitespace-pre-wrap text-[11px] leading-5 text-destructive/85">
        {errorMessage}
      </div>
    </div>
  );
};

type PlanReviewPayload = {
  conversationId: string;
  planFile?: string;
  planContent: string;
  affectedFiles?: string[];
  filePreviews?: Array<{
    path: string;
    startLine: number;
    endLine: number;
    content: string;
    status: "AVAILABLE" | "UNAVAILABLE";
  }>;
  gitStatus?: string;
  riskLevel?: "LOW" | "MEDIUM" | "HIGH";
  permissionMode?: "DEFAULT" | "DONT_ASK" | "BYPASS";
  executionPolicy?: string;
  status?: string;
};

const PlanReviewCard = ({
  review,
  disabled,
  onDecision,
  decisionState,
}: {
  review: PlanReviewPayload;
  disabled: boolean;
  onDecision?: MessageItemProps["onPlanDecision"];
  decisionState?: MessageItemProps["planDecisionState"];
}) => {
  const { t } = useTranslation();
  const [showFeedback, setShowFeedback] = useState(false);
  const [feedback, setFeedback] = useState("");
  const activeDecision =
    decisionState?.conversationId === review.conversationId
      ? decisionState
      : null;
  const submittedAction =
    activeDecision?.status === "failed" ? null : activeDecision?.action || null;
  const riskLevel = review.riskLevel || "LOW";
  const riskClassName =
    riskLevel === "HIGH"
      ? "bg-destructive/10 text-destructive"
      : riskLevel === "MEDIUM"
        ? "bg-amber-500/10 text-amber-700 dark:text-amber-300"
        : "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";

  const submitDecision = (action: "APPROVE" | "REJECT") => {
    if (!onDecision || disabled || submittedAction) {
      return;
    }
    if (action === "REJECT" && !feedback.trim()) {
      setShowFeedback(true);
      return;
    }
    void onDecision({
      action,
      feedback: action === "REJECT" ? feedback.trim() : undefined,
    });
  };

  return (
    <div className="my-3 overflow-hidden rounded-2xl border border-amber-500/25 bg-card shadow-sm">
      <div className="flex items-start gap-3 border-b border-border/65 bg-amber-500/[0.055] px-4 py-3">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-700 dark:text-amber-300">
          <ListTodo className="h-4 w-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-foreground">
            {t("chat.planMode.reviewTitle", { defaultValue: "计划等待审批" })}
          </span>
          <span className="mt-0.5 block text-[11px] leading-4 text-muted-foreground">
            {t("chat.planMode.reviewDescription", {
              defaultValue: "Agent 仍处于只读状态，批准后才会修改文件或运行命令。",
            })}
          </span>
        </span>
        <span
          className={classNames(
            "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-[9px] font-semibold",
            riskClassName,
          )}
        >
          <ShieldAlert className="h-3 w-3" />
          {riskLevel === "HIGH"
            ? t("chat.planMode.highRisk", { defaultValue: "高风险" })
            : riskLevel === "MEDIUM"
              ? t("chat.planMode.mediumRisk", { defaultValue: "中风险" })
              : t("chat.planMode.lowRisk", { defaultValue: "低风险" })}
        </span>
      </div>

      <div className="space-y-3 px-4 py-3.5">
        {review.affectedFiles?.length ? (
          <div>
            <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
              <FileText className="h-3 w-3" />
              {t("chat.planMode.affectedFiles", { defaultValue: "预计影响文件" })}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {review.affectedFiles.slice(0, 8).map((file) => (
                <span
                  key={file}
                  className="max-w-full truncate rounded-md border border-border/70 bg-muted/55 px-2 py-1 font-mono text-[10px] text-foreground/80"
                  title={file}
                >
                  {file}
                </span>
              ))}
            </div>
          </div>
        ) : null}

        {review.executionPolicy ? (
          <div className="rounded-xl border border-border/65 bg-muted/35 px-3 py-2.5">
            <div className="flex items-start gap-2">
              <LockKeyhole className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="text-[11px] font-medium text-foreground">
                    {t("chat.planMode.executionPolicy", {
                      defaultValue: "批准后的执行策略",
                    })}
                  </span>
                  {review.permissionMode ? (
                    <span className="rounded bg-background px-1.5 py-0.5 font-mono text-[9px] text-muted-foreground">
                      {review.permissionMode}
                    </span>
                  ) : null}
                </div>
                <p className="mt-0.5 text-[10px] leading-4 text-muted-foreground">
                  {review.executionPolicy}
                </p>
              </div>
            </div>
          </div>
        ) : null}

        <div className="max-h-[360px] overflow-y-auto rounded-xl border border-border/65 bg-background/70 px-3.5 py-3 [scrollbar-width:thin]">
          <div className="arc-message-markdown prose prose-sm max-w-none text-foreground dark:prose-invert">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {review.planContent || "PLAN.md 暂无内容"}
            </ReactMarkdown>
          </div>
        </div>

        {review.gitStatus ? (
          <div>
            <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
              <GitBranch className="h-3 w-3" />
              {t("chat.planMode.gitStatus", { defaultValue: "当前 Git 状态" })}
            </div>
            <pre className="max-h-32 overflow-auto whitespace-pre-wrap rounded-xl border border-border/65 bg-muted/35 px-3 py-2 font-mono text-[10px] leading-4 text-foreground/75 [scrollbar-width:thin]">
              {review.gitStatus}
            </pre>
          </div>
        ) : null}

        {review.filePreviews?.length ? (
          <div>
            <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
              <Code2 className="h-3 w-3" />
              {t("chat.planMode.filePreviews", { defaultValue: "改前文件片段" })}
            </div>
            <div className="space-y-1.5">
              {review.filePreviews.map((preview, index) => (
                <details
                  key={`${preview.path}-${index}`}
                  className="group overflow-hidden rounded-xl border border-border/65 bg-background/70"
                >
                  <summary className="cursor-pointer select-none px-3 py-2 font-mono text-[10px] text-foreground/80 marker:text-muted-foreground">
                    {preview.path}
                    {preview.status === "AVAILABLE" ? (
                      <span className="ml-2 font-sans text-[9px] text-muted-foreground">
                        L{preview.startLine}–L{preview.endLine}
                      </span>
                    ) : null}
                  </summary>
                  <pre
                    className={classNames(
                      "max-h-56 overflow-auto border-t border-border/60 px-3 py-2 font-mono text-[10px] leading-4 [scrollbar-width:thin]",
                      preview.status === "AVAILABLE"
                        ? "bg-muted/30 text-foreground/75"
                        : "bg-amber-500/[0.045] text-muted-foreground",
                    )}
                  >
                    {preview.content}
                  </pre>
                </details>
              ))}
            </div>
          </div>
        ) : null}

        {showFeedback && !submittedAction ? (
          <div className="rounded-xl border border-border/70 bg-muted/35 p-2.5">
            <label className="mb-1.5 block text-[11px] font-medium text-foreground">
              {t("chat.planMode.feedbackLabel", {
                defaultValue: "告诉 Agent 需要修改什么",
              })}
            </label>
            <textarea
              value={feedback}
              onChange={(event) => setFeedback(event.target.value)}
              className="min-h-20 w-full resize-y rounded-lg border border-border bg-background px-3 py-2 text-xs leading-5 text-foreground outline-none placeholder:text-muted-foreground focus:border-foreground/25 focus:ring-2 focus:ring-ring/20"
              placeholder={t("chat.planMode.feedbackPlaceholder", {
                defaultValue: "例如：不要修改数据库，补充回滚与单元测试方案",
              })}
              autoFocus
            />
          </div>
        ) : null}

        <div className="flex flex-wrap items-center justify-end gap-2">
          {submittedAction ? (
            <span className="mr-auto text-[11px] text-muted-foreground">
              {activeDecision?.status === "completed"
                ? t("chat.planMode.completed", {
                    defaultValue: "计划执行完成",
                  })
                : submittedAction === "APPROVE"
                ? t("chat.planMode.approved", {
                    defaultValue: "已批准，Agent 正在开始执行",
                  })
                : t("chat.planMode.rejected", {
                    defaultValue: "已驳回，Agent 正在修改计划",
                  })}
            </span>
          ) : null}
          {activeDecision?.status === "failed" ? (
            <span className="mr-auto text-[11px] text-destructive">
              {activeDecision.message || "审批执行失败，可以重试"}
            </span>
          ) : null}
          <button
            type="button"
            onClick={() => {
              if (showFeedback) {
                submitDecision("REJECT");
              } else {
                setShowFeedback(true);
              }
            }}
            disabled={disabled || Boolean(submittedAction)}
            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border bg-background px-3 text-xs font-medium text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-50"
          >
            <X className="h-3.5 w-3.5" />
            {showFeedback
              ? t("chat.planMode.submitFeedback", { defaultValue: "提交修改意见" })
              : t("chat.planMode.reject", { defaultValue: "驳回修改" })}
          </button>
          <button
            type="button"
            onClick={() => submitDecision("APPROVE")}
            disabled={disabled || Boolean(submittedAction)}
            className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-foreground px-3 text-xs font-medium text-background transition-colors hover:bg-foreground/85 disabled:pointer-events-none disabled:opacity-50"
          >
            <Check className="h-3.5 w-3.5" />
            {t("chat.planMode.approve", { defaultValue: "批准并执行" })}
          </button>
        </div>
      </div>
    </div>
  );
};

// 使用 memo 包裹 CodeBlock 组件以避免不必要的重渲染
export const CodeBlock = memo(
  ({
    language,
    filePath,
    children,
  }: {
    language: string;
    filePath?: string;
    children: string;
  }) => {
    const [copied, setCopied] = useState(false);
    const [isExpanded, setIsExpanded] = useState(true); // 添加展开/折叠状态
    const { isDarkMode } = useThemeStore();

    const highlightedCode = useMemo(() => {
      return customHighlight(children, language);
    }, [children, language]);

    const handleCopy = useCallback(async () => {
      try {
        await navigator.clipboard.writeText(children);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      } catch (err) {
        // Failed to copy
      }
    }, [children]);

    // 判断是否为 JSON 内容
    const isJson = language.toLowerCase() === 'json';

    return (
      <>
        <style>{codeStyles}</style>
        <div className="my-2">
          <div className="group overflow-hidden rounded-xl border border-border/75 bg-card shadow-sm">
            <div className="flex min-h-9 items-center justify-between border-b border-border/70 bg-muted/65 px-3 py-1">
              <div className="flex items-center gap-2.5">
                {filePath ? (
                  <div className="flex items-center gap-2">
                    <svg
                      className="h-3.5 w-3.5 text-muted-foreground"
                      viewBox="0 0 16 16"
                      fill="currentColor"
                    >
                      <path d="M2 1.75C2 .784 2.784 0 3.75 0h6.586c.464 0 .909.184 1.237.513l2.914 2.914c.329.328.513.773.513 1.237v9.586A1.75 1.75 0 0 1 13.25 16h-9.5A1.75 1.75 0 0 1 2 14.25Zm1.75-.25a.25.25 0 0 0-.25.25v12.5c0 .138.112.25.25.25h9.5a.25.25 0 0 0 .25-.25V6h-2.75A1.75 1.75 0 0 1 9 4.25V1.5Zm6.75.062V4.25c0 .138.112.25.25.25h2.688l-.011-.013-2.914-2.914-.013-.011Z" />
                    </svg>
                    <span className="text-[11px] font-medium text-muted-foreground">
                      {filePath}
                    </span>
                  </div>
                ) : language ? (
                  <div className="text-[10px] font-semibold uppercase tracking-[0.1em] text-muted-foreground">
                    {language}
                  </div>
                ) : null}
              </div>
              <div className="flex items-center gap-1">
                {/* 为 JSON 添加展开/折叠按钮 */}
                {isJson && (
                  <button
                    onClick={() => setIsExpanded(!isExpanded)}
                    className="flex h-6 w-6 items-center justify-center rounded-md p-1 text-muted-foreground opacity-0 transition-opacity hover:bg-foreground/[0.055] hover:text-foreground group-hover:opacity-100"
                    title={isExpanded ? "折叠" : "展开"}
                  >
                    <svg
                      className={`w-4 h-4 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path
                        d="M19 9l-7 7-7-7"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </button>
                )}
                <button
                  onClick={handleCopy}
                  className="flex h-6 w-6 items-center justify-center rounded-md p-1 text-muted-foreground opacity-0 transition-opacity hover:bg-foreground/[0.055] hover:text-foreground group-hover:opacity-100"
                >
                  {copied ? (
                    <svg
                      className="w-3 h-3"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path
                        d="M20 6L9 17l-5-5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  ) : (
                    <svg
                      className="w-3 h-3"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <rect x="9" y="9" width="13" height="13" rx="2" />
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                    </svg>
                  )}
                </button>
              </div>
            </div>
            <div className="overflow-hidden bg-workbench-panel">
              <div
                className={`overflow-x-auto px-3 py-2.5 [scrollbar-width:thin] ${
                  isDarkMode ? "hljs-dark" : "hljs-light"
                }`}
              >
                <pre className={`!m-0 leading-[1.45] transition-all duration-200 ${
                  isJson && !isExpanded ? 'max-h-0' : 'max-h-none'
                }`}>
                  <code
                    dangerouslySetInnerHTML={{ __html: highlightedCode }}
                    className={`language-${language || "plaintext"} text-xs text-foreground/90`}
                  />
                </pre>
                {/* JSON 内容折叠时显示渐变遮罩 */}
                {isJson && !isExpanded && (
                  <div className="pointer-events-none -mt-8 h-8 bg-gradient-to-t from-workbench-panel to-transparent" />
                )}
              </div>
            </div>
          </div>
        </div>
      </>
    );
  },
  (prevProps, nextProps) => {
    return (
      prevProps.language === nextProps.language &&
      prevProps.filePath === nextProps.filePath &&
      prevProps.children === nextProps.children
    );
  }
);

CodeBlock.displayName = "CodeBlock";

// 添加检查是否是 think 内容的函数
export const isThinkContent = (content: string) => {
  // 使用正则表达式检查完整的 <think>...</think> 标签对
  return /<think>[\s\S]*?<\/think>/.test(content);
};

// 修改 processThinkContent 函数
export const processThinkContent = (content: string) => {
  // 使用正则表达式直接提取 think 标签内的内容和外面的内容
  const thinkRegex = /<think>([\s\S]*?)<\/think>/g;
  let lastIndex = 0;
  let result = "";

  let match;
  while ((match = thinkRegex.exec(content)) !== null) {
    // 处理标签前的文本
    if (match.index > lastIndex) {
      result += content.substring(lastIndex, match.index);
    }

    // 处理 think 标签内的内容，转换为引用格式
    const thinkText = match[1];
    result +=
      thinkText
        .split("\n")
        .map((line) => `> ${line}`)
        .join("\n") + "\n\n";

    lastIndex = match.index + match[0].length;
  }

  // 处理剩余的文本
  if (lastIndex < content.length) {
    result += content.substring(lastIndex);
  }

  return result.trim();
};

type TimelineToolInvocation = {
  args: unknown;
  result?: unknown;
  state: string;
  step?: number;
  toolCallId: string;
  toolName: string;
};

type TodoItemState = "pending" | "in_progress" | "completed";

type TodoProgressItem = {
  content: string;
  status: TodoItemState;
  priority?: "high" | "medium" | "low";
};

const isTodoWriteTool = (toolName: string) =>
  toolName.replace(/[^a-z]/gi, "").toLowerCase().endsWith("todowrite");

const parseJsonObject = (value: unknown): unknown => {
  if (typeof value !== "string") {
    return value;
  }
  try {
    return safeJsonParse(value);
  } catch {
    return value;
  }
};

const normalizeTodoState = (value: unknown): TodoItemState => {
  const state = String(value || "pending")
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, "_");
  if (["completed", "complete", "done", "success", "succeeded"].includes(state)) {
    return "completed";
  }
  if (["in_progress", "progress", "doing", "running", "active"].includes(state)) {
    return "in_progress";
  }
  return "pending";
};

const normalizeTodoPriority = (
  value: unknown,
): TodoProgressItem["priority"] => {
  const priority = String(value || "").trim().toLowerCase();
  return ["high", "medium", "low"].includes(priority)
    ? (priority as TodoProgressItem["priority"])
    : undefined;
};

const parseTodoItemsFromResult = (result: unknown): TodoProgressItem[] => {
  if (typeof result !== "string") {
    return [];
  }
  return result
    .split("\n")
    .map((line): TodoProgressItem | null => {
      const match = line.match(
        /^\s*-\s*\[([x~ ])\]\s+(.+?)(?:\s+\(priority:\s*(high|medium|low)\))?\s*$/i,
      );
      if (!match) return null;
      return {
        content: match[2].trim(),
        status:
          match[1].toLowerCase() === "x"
            ? "completed"
            : match[1] === "~"
              ? "in_progress"
              : "pending",
        priority: normalizeTodoPriority(match[3]),
      } satisfies TodoProgressItem;
    })
    .filter((item): item is TodoProgressItem => item !== null);
};

const parseTodoItems = (toolInvocation: TimelineToolInvocation) => {
  let args = parseJsonObject(toolInvocation.args);
  if (args && typeof args === "object" && !Array.isArray(args)) {
    const record = args as Record<string, unknown>;
    args = parseJsonObject(
      record.todos ??
        record.tasks ??
        record.items ??
        record.raw ??
        record.input ??
        record.arguments ??
        args,
    );
    if (args && typeof args === "object" && !Array.isArray(args)) {
      const nested = args as Record<string, unknown>;
      args = nested.todos ?? nested.tasks ?? nested.items ?? args;
    }
  }

  if (!Array.isArray(args)) {
    return parseTodoItemsFromResult(toolInvocation.result);
  }

  const items = args
    .map((item): TodoProgressItem | null => {
      const parsed = parseJsonObject(item);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return null;
      }
      const record = parsed as Record<string, unknown>;
      const content = String(
        record.content ?? record.subject ?? record.description ?? record.title ?? "",
      ).trim();
      if (!content) return null;
      return {
        content,
        status: normalizeTodoState(record.status ?? record.state),
        priority: normalizeTodoPriority(record.priority),
      } satisfies TodoProgressItem;
    })
    .filter((item): item is TodoProgressItem => item !== null);

  return items.length ? items : parseTodoItemsFromResult(toolInvocation.result);
};

const TodoProgressCard = ({
  toolInvocation,
}: {
  toolInvocation: TimelineToolInvocation;
}) => {
  const [expanded, setExpanded] = useState(true);
  const { t } = useTranslation();
  const items = parseTodoItems(toolInvocation);
  const completed = items.filter((item) => item.status === "completed").length;
  const inProgress = items.filter((item) => item.status === "in_progress").length;
  const progress = items.length ? Math.round((completed / items.length) * 100) : 0;
  const toolFailed = toolInvocation.state === "error";

  return (
    <div className="my-3 overflow-hidden rounded-2xl border border-blue-500/20 bg-card shadow-sm">
      <button
        type="button"
        onClick={() => setExpanded((current) => !current)}
        className="flex w-full items-center gap-3 bg-blue-500/[0.045] px-4 py-3 text-left transition-colors hover:bg-blue-500/[0.075]"
        aria-expanded={expanded}
      >
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-300">
          <ListTodo className="h-4 w-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-foreground">
            {t("chat.todo.title", { defaultValue: "任务进度" })}
          </span>
          <span className="mt-0.5 block text-[10px] text-muted-foreground">
            {items.length
              ? t("chat.todo.summary", {
                  defaultValue: `${completed} / ${items.length} 已完成`,
                  completed,
                  total: items.length,
                })
              : t("chat.todo.preparing", { defaultValue: "正在整理任务列表" })}
          </span>
        </span>
        {inProgress > 0 ? (
          <span className="rounded-full bg-blue-500/10 px-2 py-1 text-[9px] font-medium text-blue-700 dark:text-blue-300">
            {t("chat.todo.inProgressCount", {
              defaultValue: `${inProgress} 项进行中`,
              count: inProgress,
            })}
          </span>
        ) : null}
        <svg
          className={classNames(
            "h-3.5 w-3.5 text-muted-foreground transition-transform",
            expanded && "rotate-180",
          )}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      <div className="h-1 bg-muted/65">
        <div
          className={classNames(
            "h-full transition-[width] duration-500",
            toolFailed ? "bg-destructive" : "bg-blue-500",
          )}
          style={{ width: `${progress}%` }}
        />
      </div>

      {expanded ? (
        <div className="space-y-1 px-3 py-3">
          {items.length ? (
            items.map((item, index) => (
              <div
                key={`${item.content}-${index}`}
                className={classNames(
                  "flex items-start gap-2.5 rounded-xl px-2.5 py-2",
                  item.status === "in_progress" && "bg-blue-500/[0.055]",
                )}
              >
                {item.status === "completed" ? (
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                ) : item.status === "in_progress" ? (
                  <CircleDot className="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
                ) : (
                  <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground/65" />
                )}
                <span
                  className={classNames(
                    "min-w-0 flex-1 text-xs leading-5",
                    item.status === "completed"
                      ? "text-muted-foreground line-through decoration-border"
                      : "text-foreground/90",
                  )}
                >
                  {item.content}
                </span>
                {item.priority ? (
                  <span
                    className={classNames(
                      "mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[8px] font-semibold uppercase",
                      item.priority === "high"
                        ? "bg-destructive/10 text-destructive"
                        : item.priority === "medium"
                          ? "bg-amber-500/10 text-amber-700 dark:text-amber-300"
                          : "bg-muted text-muted-foreground",
                    )}
                  >
                    {item.priority}
                  </span>
                ) : null}
              </div>
            ))
          ) : (
            <div className="flex items-center gap-2 px-2.5 py-2 text-xs text-muted-foreground">
              <div className="h-1.5 w-1.5 animate-pulse rounded-full bg-blue-500" />
              {toolFailed
                ? t("chat.todo.failed", { defaultValue: "任务列表更新失败" })
                : t("chat.todo.waiting", { defaultValue: "等待任务数据…" })}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
};

const ToolInvocationCard = ({
  toolInvocation,
}: {
  toolInvocation: TimelineToolInvocation;
  messageId: string;
  onUpdateMessage?: (messageId: string, content: {
    text: string;
    type: string;
  }[]) => void;
}) => {
  const [expanded, setExpanded] = useState(false);
  const segments = toolInvocation.toolName.split(".").filter(Boolean);
  const displayName = segments.pop() || toolInvocation.toolName || "Tool";
  const namespace = segments.join(" · ");
  const completed =
    toolInvocation.result !== undefined ||
    ["result", "output-available", "completed"].includes(toolInvocation.state);
  const failed = toolInvocation.state === "error";

  return (
    <div className="my-3 overflow-hidden rounded-xl border border-border/70 bg-card/65">
      <button
        type="button"
        onClick={() => setExpanded((current) => !current)}
        className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left transition-colors hover:bg-foreground/[0.035]"
        aria-expanded={expanded}
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-xs font-medium text-foreground">{displayName}</span>
          <span className="mt-0.5 block truncate text-[10px] text-muted-foreground">
            {namespace || "Agent tool"}
          </span>
        </span>
        <span
          className={classNames(
            "rounded-full px-2 py-0.5 text-[9px] font-medium",
            failed
              ? "bg-red-500/10 text-red-600 dark:text-red-300"
              : completed
              ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-300"
              : "bg-amber-500/10 text-amber-600 dark:text-amber-300",
          )}
        >
          {failed ? "失败" : completed ? "完成" : "运行中"}
        </span>
        <svg
          className={classNames("h-3.5 w-3.5 text-muted-foreground transition-transform", expanded && "rotate-180")}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
      {expanded ? (
        <div className="space-y-3 border-t border-border/65 px-3 py-3">
          <div>
            <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
              参数
            </div>
            <pre className="max-h-52 overflow-auto whitespace-pre-wrap rounded-lg bg-muted/65 p-2.5 font-mono text-[11px] leading-5 text-foreground/85">
              {JSON.stringify(toolInvocation.args ?? {}, null, 2)}
            </pre>
          </div>
          {toolInvocation.result !== undefined ? (
            <div>
              <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                结果
              </div>
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded-lg bg-muted/65 p-2.5 font-mono text-[11px] leading-5 text-foreground/85">
                {typeof toolInvocation.result === "string"
                  ? toolInvocation.result
                  : JSON.stringify(toolInvocation.result, null, 2)}
              </pre>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
};

// 添加列表进度卡片组件
const ListProgressCard = ({
  filePath,
  content,
  isLoading
}: {
  filePath: string;
  content?: string;
  isLoading: boolean;
}) => {
  const { t } = useTranslation();

  return (
    <div className="mb-3 ml-10 overflow-hidden rounded-xl border border-border/70 bg-card/65">
      <div className="flex items-center gap-2.5 px-3 py-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-xs font-medium text-foreground">
              {t('chat.list_progress', 'List Progress')}
            </span>
            <span className="mt-0.5 block truncate font-mono text-[10px] text-muted-foreground">
              {filePath}
            </span>
          </span>
          {isLoading && (
              <svg className="h-3.5 w-3.5 animate-spin text-muted-foreground" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                />
              </svg>
          )}
      </div>
        <div className="border-t border-border/65 p-3">
          {content ? (
            <pre className="max-h-52 overflow-auto whitespace-pre-wrap rounded-lg bg-muted/65 p-2.5 font-mono text-[11px] leading-5 text-foreground/85">
              {content}
            </pre>
          ) : (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <div className="h-1.5 w-1.5 animate-pulse rounded-full bg-foreground/45" />
              <span>{t('chat.loading_list', 'Loading list content...')}</span>
            </div>
          )}
        </div>
    </div>
  );
};

export const MessageItem: React.FC<MessageItemProps> = ({
  message,
  isLoading,
  isEndMessage,
  handleRetry,
  listProgressStates = {},
  onUpdateMessage,
  onPlanDecision,
  planDecisionState,
}) => {
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [isCollapsed, setIsCollapsed] = useState<boolean>(false);
  const [copied, setCopied] = useState(false);
  const isUser = message.role === "user";
  const handleCopyMessage = useCallback(async () => {
    try {
      const textContent = getDisplayContent(message);
      await navigator.clipboard.writeText(textContent);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("复制失败:", err);
    }
  }, [message]);

  return (
    <div className={classNames("group relative py-2", isUser && "flex justify-end")}>
      <div className={classNames("flex min-w-0 items-start gap-3", isUser ? "max-w-[86%] flex-row-reverse" : "w-full")}>
        {!isUser ? <AppLogo className="h-7 w-7 rounded-lg" /> : null}
          <div
            className={classNames(
              "min-w-0",
              isUser
                ? "rounded-2xl rounded-br-md border border-border/55 bg-muted/70 px-3.5 py-2.5 shadow-sm"
                : "flex-1 pt-0.5",
            )}
          >
            {isArtifactContent(message.content) ? (
              <ArtifactView
                isUser={isUser}
                title={getArtifactTitle(message.content)}
                message={message}
                isComplete={!isLoading}
                conversationId={message.id?.split('-')[0]} // 从消息ID提取会话ID
                userId={localStorage.getItem('user') ? safeJsonParse(localStorage.getItem('user') || '{}').id || 'default-user' : 'default-user'} // 从本地存储获取用户ID
              />
            ) : (
              <div className="flex flex-col gap-1">
                <div className="arc-message-markdown prose prose-sm max-w-none leading-relaxed text-foreground dark:prose-invert">
                  {/* 修改工具调用卡片的渲染 */}
                  {message.parts?.map((part, index) => {
                    if (part.type === "tool-invocation") {
                      if (isTodoWriteTool(part.toolInvocation.toolName)) {
                        return (
                          <TodoProgressCard
                            key={index}
                            toolInvocation={part.toolInvocation}
                          />
                        );
                      }
                      return (
                        <ToolInvocationCard
                          key={index}
                          toolInvocation={part.toolInvocation}
                          messageId={message.id}
                          onUpdateMessage={onUpdateMessage}
                        />
                      );
                    }
                    return null;
                  })}

                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      code({ node, className, children, ...props }) {
                        const match = /language-([\w-]+)(?::(.+))?/.exec(
                          className || ""
                        );
                        const isInline = !match;

                        if (isInline) {
                          return (
                            <code
                              className="rounded bg-muted px-1.5 py-0.5 font-mono text-[0.9em] text-foreground"
                              {...props}
                            >
                              {children}
                            </code>
                          );
                        }

                        const language = match?.[1] || "";
                        const filePath = match?.[2];
                        // 确保 children 是字符串类型
                        const content = Array.isArray(children)
                          ? children.join("")
                          : String(children).replace(/\n$/, "");

                        if (language === "arc-reasoning") {
                          return <ReasoningCard content={decodeTimelinePayload(content)} />;
                        }

                        if (language === "arc-error") {
                          return <RunErrorCard message={decodeTimelinePayload(content)} />;
                        }

                        if (language === "arc-plan") {
                          try {
                            const review = safeJsonParse(
                              decodeTimelinePayload(content),
                            ) as PlanReviewPayload;
                            return (
                              <PlanReviewCard
                                review={review}
                                disabled={isLoading}
                                onDecision={onPlanDecision}
                                decisionState={planDecisionState}
                              />
                            );
                          } catch (error) {
                            console.error("Failed to decode plan review", error);
                          }
                        }

                        if (language === "arc-tool") {
                          try {
                            const invocation = safeJsonParse(
                              decodeTimelinePayload(content),
                            ) as TimelineToolInvocation;
                            if (isTodoWriteTool(invocation.toolName)) {
                              return (
                                <TodoProgressCard
                                  toolInvocation={invocation}
                                />
                              );
                            }
                            return (
                              <ToolInvocationCard
                                toolInvocation={invocation}
                                messageId={message.id}
                                onUpdateMessage={onUpdateMessage}
                              />
                            );
                          } catch (error) {
                            console.error("Failed to decode AG-UI tool timeline", error);
                          }
                        }

                        return (
                          <CodeBlock language={language} filePath={filePath}>
                            {content}
                          </CodeBlock>
                        );
                      },
                      pre({ children }) {
                        // 直接返回子元素，不需要额外的包装
                        return children;
                      },
                      p({ children }) {
                        return <p className="mb-2 last:mb-0">{children}</p>;
                      },
                      ul({ children }) {
                        return (
                          <ul className="pl-4 mb-2 space-y-1 list-disc">
                            {children}
                          </ul>
                        );
                      },
                      ol({ children }) {
                        return (
                          <ol className="pl-4 mb-2 space-y-1 list-decimal">
                            {children}
                          </ol>
                        );
                      },
                      li({ children }) {
                        return (
                          <li className="text-foreground/90">
                            {children}
                          </li>
                        );
                      },
                      a({ children, href }) {
                        return (
                          <a
                            href={href}
                            className="text-foreground underline decoration-border underline-offset-4 hover:decoration-foreground"
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {children}
                          </a>
                        );
                      },
                      blockquote({ children }) {
                        return (
                          <blockquote className="relative my-2 rounded-xl border border-border/65 bg-muted/45 py-2 pl-4 pr-9 text-sm text-muted-foreground">
                            <div
                              className={`overflow-hidden transition-all duration-200 ${
                                isCollapsed ? "h-4" : "max-h-none"
                              }`}
                            >
                              {children}
                            </div>
                            {/* 渐变遮罩 */}
                            {isCollapsed && (
                              <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-muted to-transparent" />
                            )}
                            {/* 折叠/展开按钮 */}
                            <button
                              onClick={() => setIsCollapsed(!isCollapsed)}
                              className="absolute bottom-1 right-2 rounded-full p-1 text-muted-foreground hover:bg-foreground/[0.06] hover:text-foreground"
                            >
                              <svg
                                className="w-4 h-4"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                              >
                                {isCollapsed ? (
                                  <path
                                    d="M12 5v14M5 12h14"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                ) : (
                                  <path
                                    d="M5 12h14"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                )}
                              </svg>
                            </button>
                          </blockquote>
                        );
                      },
                      strong({ children }) {
                        return <strong>{children}</strong>;
                      },
                      em({ children }) {
                        return <em>{children}</em>;
                      },
                      table({ children }) {
                        return (
                          <div className="my-4 overflow-x-auto">
                            <table className="min-w-full border-collapse overflow-hidden rounded-lg border border-border">
                              {children}
                            </table>
                          </div>
                        );
                      },
                      thead({ children }) {
                        return (
                          <thead className="bg-muted/65">
                            {children}
                          </thead>
                        );
                      },
                      tbody({ children }) {
                        return (
                          <tbody className="divide-y divide-border">
                            {children}
                          </tbody>
                        );
                      },
                      tr({ children }) {
                        return (
                          <tr className="hover:bg-muted/45">
                            {children}
                          </tr>
                        );
                      },
                      th({ children }) {
                        return (
                          <th className="border border-border px-4 py-2 text-left text-sm font-medium text-foreground">
                            {children}
                          </th>
                        );
                      },
                      td({ children }) {
                        return (
                          <td className="border border-border px-4 py-2 text-sm text-foreground/85">
                            {children}
                          </td>
                        );
                      },
                    }}
                  >
                    {(() => {
                      return getDisplayContent(message);
                    })()}
                  </ReactMarkdown>
                </div>
              </div>
            )}

            {message.experimental_attachments &&
              message.experimental_attachments.length > 0 && (
                <div className="mt-2">
                  <ImageGrid
                    images={message.experimental_attachments}
                    onImageClick={(url) => setPreviewImage(url)}
                  />
                </div>
              )}
          </div>
      </div>
      {previewImage && (
        <ImagePreview
          src={previewImage}
          onClose={() => setPreviewImage(null)}
        />
      )}

      {/* 渲染list-progress卡片 */}
      {!isUser && isEndMessage ? Object.entries(listProgressStates).map(([operationId, state]) => (
        <ListProgressCard
          key={operationId}
          filePath={state.filePath}
          content={state.content}
          isLoading={state.isLoading}
        />
      )) : null}

      <>
        {!isArtifactContent(message.content) ? (
          <div className={classNames("flex items-center gap-0.5", isUser ? "justify-end" : "ml-10 justify-start")}>
            <button
              onClick={handleCopyMessage}
              className="rounded-lg p-1.5 text-muted-foreground opacity-0 transition-opacity hover:bg-foreground/[0.055] hover:text-foreground group-hover:opacity-100"
            >
              {copied ? (
                <svg
                  className="w-4 h-4 text-green-500"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    d="M20 6L9 17l-5-5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              ) : (
                <svg
                  className="h-3.5 w-3.5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              )}
            </button>
            {isShowRetry(isUser, isLoading, isEndMessage) ? (
              <button
                onClick={() => {
                  handleRetry?.()
                }}
                className="rounded-lg p-1.5 text-muted-foreground opacity-0 transition-opacity hover:bg-foreground/[0.055] hover:text-foreground group-hover:opacity-100"
                title="重试"
              >
                <svg
                  className="h-3.5 w-3.5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            ) : null}
          </div>
        ) : null}
      </>
    </div>
  );
};
