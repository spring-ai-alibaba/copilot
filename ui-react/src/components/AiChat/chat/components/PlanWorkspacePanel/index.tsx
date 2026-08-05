import { useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import classNames from "classnames";
import {
  AlertCircle,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Circle,
  CircleDot,
  FileText,
  GitBranch,
  ListTodo,
  LoaderCircle,
  LockKeyhole,
  ShieldAlert,
  X,
} from "lucide-react";
import type {
  PlanWorkspaceReview,
  PlanWorkspaceState,
  PlanWorkspaceTask,
} from "@/api/conversation";
import { safeJsonParse } from "@/utils/safeJsonParse";

export type PlanDecision = {
  action: "APPROVE" | "REJECT";
  feedback?: string;
};

export type PlanDecisionState = {
  conversationId: string;
  action: "APPROVE" | "REJECT";
  status: "submitting" | "running" | "completed" | "failed";
  message?: string;
};

export type TimelineToolInvocation = {
  args: unknown;
  result?: unknown;
  state: string;
  toolCallId: string;
  toolName: string;
};

export const isTodoWriteTool = (toolName: string) =>
  toolName.replace(/[^a-z]/gi, "").toLowerCase().endsWith("todowrite");

export const stripPlanWorkspaceTimeline = (content: string) =>
  content
    .replace(/```arc-plan\n[\s\S]*?\n```/g, "")
    .replace(/```arc-tool\n([\s\S]*?)\n```/g, (block, encoded: string) => {
      try {
        const invocation = safeJsonParse(decodeURIComponent(encoded.trim())) as { toolName?: string };
        return invocation.toolName && isTodoWriteTool(invocation.toolName) ? "" : block;
      } catch {
        return block;
      }
    })
    .replace(/\n{3,}/g, "\n\n");

const parseJsonValue = (value: unknown): unknown => {
  if (typeof value !== "string") return value;
  try {
    return safeJsonParse(value);
  } catch {
    return value;
  }
};

const normalizeTaskStatus = (value: unknown): PlanWorkspaceTask["status"] => {
  const status = String(value || "pending").trim().toLowerCase().replace(/[\s-]+/g, "_");
  if (["completed", "complete", "done", "success", "succeeded"].includes(status)) return "completed";
  if (["in_progress", "progress", "doing", "running", "active"].includes(status)) return "in_progress";
  return "pending";
};

export const parseTodoTasks = (source: unknown, result?: unknown): PlanWorkspaceTask[] => {
  let value = parseJsonValue(source);
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    value = parseJsonValue(record.todos ?? record.tasks ?? record.items ?? record.raw ?? record.input ?? record.arguments ?? value);
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const nested = value as Record<string, unknown>;
      value = nested.todos ?? nested.tasks ?? nested.items ?? value;
    }
  }
  if (!Array.isArray(value)) {
    if (typeof result !== "string") return [];
    return result.split("\n").flatMap((line) => {
      const match = line.match(/^\s*-\s*\[([x~ ])\]\s+(.+?)(?:\s+\(priority:\s*(high|medium|low)\))?\s*$/i);
      if (!match) return [];
      return [{
        content: match[2].trim(),
        status: match[1].toLowerCase() === "x" ? "completed" : match[1] === "~" ? "in_progress" : "pending",
        priority: match[3]?.toLowerCase() as PlanWorkspaceTask["priority"],
      }];
    });
  }
  return value.flatMap((item, index) => {
    const parsed = parseJsonValue(item);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return [];
    const record = parsed as Record<string, unknown>;
    const content = String(record.content ?? record.subject ?? record.description ?? record.title ?? "").trim();
    if (!content) return [];
    const priority = String(record.priority || "").toLowerCase();
    return [{
      id: record.id ? String(record.id) : `task-${index}`,
      content,
      status: normalizeTaskStatus(record.status ?? record.state),
      priority: ["high", "medium", "low"].includes(priority)
        ? priority as PlanWorkspaceTask["priority"]
        : undefined,
      owner: record.owner ? String(record.owner) : undefined,
    }];
  });
};

const statusMeta: Record<PlanWorkspaceState["status"], { title: string; description: string; tone: string }> = {
  IDLE: { title: "计划与执行", description: "暂无计划", tone: "text-muted-foreground" },
  PLANNING: { title: "正在生成计划", description: "Agent 正在分析上下文并整理实施步骤", tone: "text-blue-600 dark:text-blue-300" },
  PENDING_APPROVAL: { title: "计划等待审批", description: "确认方案后 Agent 才会开始修改", tone: "text-amber-700 dark:text-amber-300" },
  REVISING: { title: "正在修改计划", description: "Agent 正在根据反馈生成新版本", tone: "text-blue-600 dark:text-blue-300" },
  EXECUTING: { title: "计划正在执行", description: "可展开查看任务进度与已批准计划", tone: "text-blue-600 dark:text-blue-300" },
  COMPLETED: { title: "计划执行完成", description: "所有执行步骤均已处理", tone: "text-emerald-700 dark:text-emerald-300" },
  FAILED: { title: "计划运行失败", description: "展开查看错误并决定下一步", tone: "text-destructive" },
};

const TaskList = ({ tasks }: { tasks: PlanWorkspaceTask[] }) => (
  <div className="space-y-2">
    {tasks.map((task, index) => (
      <div key={task.id || `${task.content}-${index}`} className="flex items-start gap-2.5 rounded-lg border border-border/60 bg-muted/25 px-3 py-2">
        {task.status === "completed" ? (
          <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
        ) : task.status === "in_progress" ? (
          <CircleDot className="mt-0.5 h-4 w-4 shrink-0 text-blue-600" />
        ) : (
          <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        )}
        <div className="min-w-0 flex-1">
          <div className="text-xs leading-5 text-foreground/90">{task.content}</div>
          {(task.priority || task.owner) && (
            <div className="mt-0.5 flex gap-2 text-[9px] uppercase tracking-wide text-muted-foreground">
              {task.priority && <span>{task.priority}</span>}
              {task.owner && <span>{task.owner}</span>}
            </div>
          )}
        </div>
      </div>
    ))}
  </div>
);

const ReviewDetails = ({ review }: { review: PlanWorkspaceReview }) => {
  const risk = review.riskLevel || "LOW";
  return (
    <div className="space-y-3">
      {review.summary && (
        <section className="rounded-xl border border-primary/15 bg-primary/[0.045] px-3 py-2.5">
          <div className="mb-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-primary/80">你将得到什么</div>
          <p className="text-xs leading-5 text-foreground/90">{review.summary}</p>
        </section>
      )}

      {review.changes?.length ? (
        <section>
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">计划怎么做</div>
          <div className="space-y-2">
            {review.changes.map((change, index) => (
              <div key={`${change.title}-${index}`} className="rounded-xl border border-border/60 bg-muted/20 px-3 py-2.5">
                <div className="flex items-start gap-2">
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-foreground text-[9px] font-semibold text-background">{index + 1}</span>
                  <div className="min-w-0 flex-1">
                    <div className="text-xs font-medium text-foreground">{change.title}</div>
                    {(change.action || change.impact || change.reason) && <p className="mt-1 text-[10px] leading-4 text-muted-foreground">{[change.action, change.impact, change.reason].filter(Boolean).join(" · ")}</p>}
                    {(change.files?.length || change.symbols?.length) ? <div className="mt-1.5 flex flex-wrap gap-1">{change.files?.map((file) => <span key={file} className="rounded bg-background px-1.5 py-0.5 font-mono text-[9px] text-foreground/75">{file}</span>)}{change.symbols?.map((symbol) => <span key={symbol} className="rounded bg-sky-500/10 px-1.5 py-0.5 font-mono text-[9px] text-sky-700 dark:text-sky-300">{symbol}</span>)}</div> : null}
                    {change.acceptanceCriteria?.length ? <p className="mt-1.5 text-[10px] leading-4 text-emerald-700 dark:text-emerald-300">完成标准：{change.acceptanceCriteria.join("；")}</p> : null}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <span className={classNames(
          "inline-flex items-center gap-1 rounded-full px-2 py-1 text-[9px] font-semibold",
          risk === "HIGH" ? "bg-destructive/10 text-destructive" : risk === "MEDIUM" ? "bg-amber-500/10 text-amber-700 dark:text-amber-300" : "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
        )}>
          <ShieldAlert className="h-3 w-3" />{risk === "HIGH" ? "高风险" : risk === "MEDIUM" ? "中风险" : "低风险"}
        </span>
        {review.permissionMode && <span className="rounded bg-muted px-2 py-1 font-mono text-[9px] text-muted-foreground">{review.permissionMode}</span>}
      </div>

      {review.affectedFiles?.length ? (
        <section>
          <div className="mb-1.5 flex items-center gap-1.5 text-[10px] font-semibold text-muted-foreground"><FileText className="h-3 w-3" />预计影响文件</div>
          <div className="flex flex-wrap gap-1.5">{review.affectedFiles.map((file) => <span key={file} className="max-w-full truncate rounded-md border border-border/70 bg-muted/55 px-2 py-1 font-mono text-[10px]">{file}</span>)}</div>
        </section>
      ) : null}

      {review.executionPolicy && <div className="flex items-start gap-2 rounded-lg border border-border/60 bg-muted/30 px-3 py-2 text-[10px] leading-4 text-muted-foreground"><LockKeyhole className="mt-0.5 h-3.5 w-3.5 shrink-0" /><span>{review.executionPolicy}</span></div>}

      {review.verifications?.length ? (
        <section>
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">如何验证</div>
          <div className="space-y-1.5">{review.verifications.map((item, index) => <div key={`${item.description}-${index}`} className="rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-[10px] leading-4 text-foreground/85"><span className="mr-1.5 font-semibold text-muted-foreground">{item.type || "验证"}</span>{item.description}{item.command && <code className="mt-1 block w-fit rounded bg-background px-1.5 py-0.5 text-[9px] text-muted-foreground">{item.command}</code>}</div>)}</div>
        </section>
      ) : null}

      {review.evidence?.length ? (
        <section className="rounded-xl border border-sky-500/20 bg-sky-500/[0.045] px-3 py-2.5 text-[10px] leading-4">
          <div className="mb-1.5 flex items-center gap-1.5 font-semibold text-sky-700 dark:text-sky-300"><GitBranch className="h-3.5 w-3.5" />代码证据 <span className="rounded bg-background/70 px-1.5 py-0.5 text-[9px] font-medium text-muted-foreground">CodeGraph</span></div>
          <div className="space-y-2">{review.evidence.map((item, index) => <div key={`${item.subject}-${index}`}><div className="text-foreground/85">{item.summary}</div>{item.relatedFiles?.length ? <div className="mt-1 flex flex-wrap gap-1">{item.relatedFiles.map((file) => <span key={file} className="rounded bg-background/80 px-1.5 py-0.5 font-mono text-[9px] text-foreground/70">{file}</span>)}</div> : null}</div>)}</div>
        </section>
      ) : null}

      {(review.evidenceStatus === "INDEXING" || review.evidenceStatus === "SYNCING") && (
        <section className="flex items-start gap-2 rounded-xl border border-sky-500/20 bg-sky-500/[0.045] px-3 py-2.5 text-[10px] leading-4 text-sky-800 dark:text-sky-200">
          <LoaderCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 animate-spin" />
          <span>正在{review.evidenceStatus === "SYNCING" ? "同步" : "建立"} CodeGraph 代码索引。本次仍可审批；下一次生成或修订计划会自动补充调用链与关联测试证据。</span>
        </section>
      )}

      {(review.risks?.length || review.scopeOut?.length) ? (
        <section className="rounded-xl border border-border/60 bg-muted/15 px-3 py-2.5 text-[10px] leading-4">
          {review.risks?.length ? <div><div className="mb-1 font-semibold text-amber-700 dark:text-amber-300">风险与边界</div>{review.risks.map((riskItem) => <div key={riskItem} className="text-foreground/80">· {riskItem}</div>)}</div> : null}
          {review.scopeOut?.length ? <div className={classNames(review.risks?.length && "mt-2")}><div className="mb-1 font-semibold text-muted-foreground">不修改的范围</div>{review.scopeOut.map((scope) => <div key={scope} className="text-muted-foreground">· {scope}</div>)}</div> : null}
        </section>
      ) : null}

      {review.questions?.length ? (
        <section className="rounded-xl border border-amber-500/25 bg-amber-500/[0.06] px-3 py-2.5 text-[10px] leading-4">
          <div className="mb-1 font-semibold text-amber-700 dark:text-amber-300">需要你决定</div>
          {review.questions.map((item, index) => <div key={`${item.question}-${index}`} className="text-foreground/85">· {item.question}{item.suggestedAnswer ? `（建议：${item.suggestedAnswer}）` : ""}</div>)}
        </section>
      ) : null}

      <details className="rounded-xl border border-border/60 bg-muted/[0.12]">
        <summary className="cursor-pointer px-3 py-2 text-[10px] font-medium text-muted-foreground">技术详情与原始计划</summary>
        <div className="space-y-3 border-t border-border/60 px-3 py-3">
          <div className="arc-message-markdown prose prose-sm max-w-none rounded-xl border border-border/60 bg-background px-4 py-3 text-xs dark:prose-invert">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{review.planContent}</ReactMarkdown>
          </div>

          {review.filePreviews?.map((preview) => (
            <details key={preview.path} className="rounded-lg border border-border/60 bg-muted/20">
              <summary className="cursor-pointer px-3 py-2 font-mono text-[10px] text-foreground/80">{preview.path}</summary>
              <pre className="overflow-x-auto border-t border-border/60 px-3 py-2 text-[10px] leading-5 text-muted-foreground">{preview.content}</pre>
            </details>
          ))}
          {review.gitStatus && <details className="rounded-lg border border-border/60 bg-muted/20"><summary className="flex cursor-pointer items-center gap-1.5 px-3 py-2 text-[10px] font-medium"><GitBranch className="h-3 w-3" />Git 状态</summary><pre className="overflow-x-auto border-t border-border/60 px-3 py-2 text-[10px] text-muted-foreground">{review.gitStatus}</pre></details>}
        </div>
      </details>
    </div>
  );
};

export const PlanWorkspacePanel = ({
  workspace,
  isLoading,
  decisionState,
  onDecision,
  integrated = false,
}: {
  workspace: PlanWorkspaceState | null;
  isLoading: boolean;
  decisionState: PlanDecisionState | null;
  onDecision: (decision: PlanDecision) => Promise<void> | void;
  /**
   * Render the panel as the top section of the composer instead of as a
   * standalone card. The composer owns the outer border, background and
   * shadow in this mode, so the panel can behave like an upward drawer.
   */
  integrated?: boolean;
}) => {
  const [expanded, setExpanded] = useState(false);
  const [showFeedback, setShowFeedback] = useState(false);
  const [feedback, setFeedback] = useState("");
  const previousReviewId = useRef<string | undefined>();
  const previousTaskCount = useRef(0);
  const previousStatus = useRef<PlanWorkspaceState["status"] | undefined>();

  useEffect(() => {
    const reviewId = workspace?.review?.reviewId;
    if (reviewId && reviewId !== previousReviewId.current) {
      setExpanded(true);
      setShowFeedback(false);
      setFeedback("");
    }
    previousReviewId.current = reviewId;
  }, [workspace?.review?.reviewId]);

  useEffect(() => {
    const taskCount = workspace?.tasks.length || 0;
    if (previousTaskCount.current === 0 && taskCount > 0) setExpanded(true);
    previousTaskCount.current = taskCount;
  }, [workspace?.tasks.length]);

  useEffect(() => {
    const status = workspace?.status;
    if (!status || status === previousStatus.current) return;
    if (["REVISING", "COMPLETED"].includes(status)) setExpanded(false);
    if (status === "EXECUTING" && previousStatus.current !== undefined) setExpanded(false);
    if (status === "FAILED") setExpanded(true);
    previousStatus.current = status;
  }, [workspace?.status]);

  const progress = useMemo(() => {
    const tasks = workspace?.tasks || [];
    const completed = tasks.filter((task) => task.status === "completed").length;
    return { completed, total: tasks.length, percent: tasks.length ? Math.round(completed / tasks.length * 100) : 0 };
  }, [workspace?.tasks]);

  if (!workspace || (workspace.status === "IDLE" && !workspace.review && !workspace.tasks.length)) return null;
  const meta = statusMeta[workspace.status];
  const busy = ["PLANNING", "REVISING", "EXECUTING"].includes(workspace.status) && isLoading;
  const activeDecision = decisionState?.conversationId === workspace.conversationId ? decisionState : null;
  const decisionBusy = activeDecision?.status === "submitting" || activeDecision?.status === "running";
  const canDecide = workspace.decisionAllowed && !!workspace.review && !isLoading && !decisionBusy;

  const submit = (action: PlanDecision["action"]) => {
    if (!canDecide) return;
    if (action === "REJECT" && !feedback.trim()) {
      setShowFeedback(true);
      return;
    }
    onDecision({ action, feedback: action === "REJECT" ? feedback.trim() : undefined });
  };

  const PanelExpandIcon = expanded ? ChevronDown : ChevronUp;

  return (
    <div className={classNames("w-full", !integrated && "mx-auto max-w-[760px] px-3 sm:px-5")}>
      <div className={classNames(
        "overflow-hidden",
        integrated
          ? "border-b border-border/65"
          : "rounded-2xl border border-border/75 bg-card/95 shadow-lg backdrop-blur-xl",
      )}>
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          className={classNames(
            "flex w-full items-center gap-3 text-left transition-colors hover:bg-muted/35",
            integrated ? "px-4 py-2.5" : "px-4 py-3",
            expanded && integrated && "bg-muted/15",
          )}
          aria-expanded={expanded}
          aria-label={expanded ? "收起计划与执行" : "展开计划与执行"}
        >
          <span className={classNames("flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted", meta.tone)}>
            {busy ? <LoaderCircle className="h-4 w-4 animate-spin" /> : workspace.status === "COMPLETED" ? <CheckCircle2 className="h-4 w-4" /> : workspace.status === "FAILED" ? <AlertCircle className="h-4 w-4" /> : <ListTodo className="h-4 w-4" />}
          </span>
          <span className="min-w-0 flex-1">
            <span className={classNames("block text-sm font-semibold", meta.tone)}>{meta.title}</span>
            <span className="mt-0.5 block truncate text-[10px] text-muted-foreground">{workspace.message || meta.description}{progress.total ? ` · ${progress.completed}/${progress.total} 已完成` : ""}</span>
          </span>
          {progress.total > 0 && <span className="text-[10px] font-medium text-muted-foreground">{progress.percent}%</span>}
          <PanelExpandIcon className="h-4 w-4 text-muted-foreground" />
        </button>

        <div
          className={classNames(
            "overflow-hidden transition-[max-height,opacity] duration-300 ease-out",
            expanded ? "max-h-[min(50vh,520px)] opacity-100" : "max-h-0 opacity-0",
          )}
          aria-hidden={!expanded}
        >
          <div className={classNames(
            "max-h-[min(50vh,520px)] overflow-y-auto px-4 py-3 [scrollbar-width:thin]",
            !integrated && "border-t border-border/65",
          )}>
            {workspace.status === "FAILED" && <div className="mb-3 rounded-lg border border-destructive/25 bg-destructive/[0.06] px-3 py-2 text-xs text-destructive">{workspace.message || "计划执行失败，请检查模型或工具配置后重试。"}</div>}
            {workspace.tasks.length > 0 && <section className="mb-4"><div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">Todo 进度</div><TaskList tasks={workspace.tasks} /></section>}
            {workspace.review && <section><div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">{workspace.decisionAllowed ? "待审批计划" : "已批准计划"}</div><ReviewDetails review={workspace.review} /></section>}

            {workspace.decisionAllowed && workspace.review && (
              <div className="sticky bottom-0 -mx-4 -mb-3 mt-4 border-t border-border/65 bg-card/95 px-4 py-3 backdrop-blur">
                {showFeedback && <textarea value={feedback} onChange={(event) => setFeedback(event.target.value)} rows={2} placeholder="说明需要修改的地方" className="mb-2 w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-xs outline-none focus:border-amber-500/60" />}
                <div className="flex justify-end gap-2">
                  <button type="button" disabled={!canDecide} onClick={() => submit("REJECT")} className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs font-medium text-foreground disabled:opacity-45"><X className="h-3.5 w-3.5" />修改计划</button>
                  <button type="button" disabled={!canDecide} onClick={() => submit("APPROVE")} className="inline-flex items-center gap-1.5 rounded-lg bg-foreground px-3 py-2 text-xs font-medium text-background disabled:opacity-45"><Check className="h-3.5 w-3.5" />{decisionBusy ? "处理中…" : "批准并执行"}</button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
