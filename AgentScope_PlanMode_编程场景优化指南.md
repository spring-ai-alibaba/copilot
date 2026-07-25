# AgentScope Java — Plan Mode 编程场景深度优化指南

> 基于 AgentScope Java v2 Harness 官方文档，结合编程场景的实践优化思路

---

## 一、Plan Mode 核心原理

### 设计思想

Plan Mode 把 AI Agent 的行为强制分成两个严格隔离的阶段：

```
[Read-Only Phase]  →  HITL Confirm  →  [Execution Phase]
    设计/探索              人工确认           工具全开放
```

解决的根本问题：**LLM 倾向于边想边做，Plan Mode 强制它先想清楚再动手**。

### 工具白名单机制

Plan 阶段只开放 4 类工具：

| 工具 | 作用 | 设计意图 |
|---|---|---|
| `plan_enter` | 进入计划模式 | 显式声明"我在规划" |
| `plan_write` | 写入 `plans/PLAN.md` | 外化思考，不用 write_file 是为了**安全** |
| `plan_exit` | 退出，触发 HITL | 人工把关的切入点 |
| `todo_write` | 任务列表 | 结构化分解，执行阶段用 |

其他工具调用被直接拒绝：
```
[Tool denied — plan mode is active]
Only read-only tools and plan_enter / plan_write / plan_exit / todo_write are allowed.
```

### 标准工作流

```
用户发任务
    ↓
LLM 调 plan_enter
    ↓
LLM 用 read_file / grep_files 探索代码库
    ↓
LLM 调 plan_write（写 PLAN.md）
    ↓
LLM 调 plan_exit
    ↓
系统触发 HITL → 等人类确认
    ↓
人类 APPROVE
    ↓
执行阶段（全工具解锁）
    ↓
LLM 调 todo_write 拆 5-8 个任务 → 按序执行
```

### 基础启用代码

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("planner")
    .model(model)
    .workspace(workspace)
    .enablePlanMode()
    .enableTaskList()           // 每次推理前提示待办
    .planFileDirectory("plans") // 默认就是 "plans"
    .build();
```

---

## 二、优化方向（编程场景）

### 优先级排序

| 优先级 | 优化项 | 成本 | ROI |
|---|---|---|---|
| ★★★ | PLAN.md 模板结构化 | 5 分钟 | 极高 |
| ★★★ | HITL 审批界面设计 | 中等 | 极高 |
| ★★★ | 拒绝循环（Rejection Loop） | 中等 | 高 |
| ★★ | 探索阶段深度（Shell 只读命令） | 低 | 高 |
| ★★ | 分级风险控制 | 中等 | 中 |
| ★ | Plan → Todo 粒度控制 | 低 | 中 |
| ★ | 多 Agent 计划者/执行者分离 | 高 | 高（大型任务） |

---

## 三、PLAN.md 模板结构化（最高 ROI）

Plan Mode 默认只说"写计划"，不规定格式。在系统提示里强制模型按模板写，是最廉价的优化。

### 推荐模板

```markdown
## 任务理解
- 要解决的问题是：
- 涉及文件：（带行号）
- 不碰的范围：

## 方案设计
- 选择方案 N 因为：
- 被排除的方案：（为什么）

## 变更清单
| 文件 | 操作 | 影响范围 |
|------|------|--------|
| src/Foo.java | 修改方法 X | 调用方 Y/Z |

## 测试策略
- 新增单测：
- 需要手动验证的：

## 风险点
- [ ] 数据库 migration（不可逆）
- [ ] 外部 API 调用
- [ ] 并发/线程安全问题

## 回滚方案
如果失败，执行：
```

### 注入方式

在 builder 里配置 plan phase 的系统指令，或通过 system prompt 注入 `plan_write` 的格式要求。核心目标：让 PLAN.md 包含**实际文件路径和行号**，而不是泛泛的"修改 service 层"。

---

## 四、探索阶段深度（allowShellInPlanMode 的正确用法）

开启 shell 但给模型明确的只读命令约束：

```java
HarnessAgent agent = HarnessAgent.builder()
    .enablePlanMode()
    .allowShellInPlanMode()  // 允许只读 shell
    .build();
```

### 允许的只读命令清单

```bash
git log --oneline -20                       # 理解最近改动背景
git diff HEAD~3 -- src/Foo.java             # 看文件演化
grep -rn "ClassName" src/                  # 找调用链
find . -name "*.java" -path "*/service/*"  # 定位文件结构
mvn test -pl module -q 2>&1 | tail -20     # 了解当前测试状态
```

### System Prompt 约束

```
在 plan 阶段你只能执行只读 shell 命令，禁止任何写操作，执行前先说明目的。
允许：cat / ls / grep / git log / git diff / git show / git status / find / mvn test（只读）
禁止：write / rm / mv / cp / git commit / git push / 任何修改文件的操作
```

> 注意：这个保证比默认模式弱，生产环境建议配合沙箱文件系统使用。

---

## 五、HITL 审批界面设计

不要只展示 PLAN.md 原文，要在人类看到计划时**同时展示相关上下文**：

```java
agent.streamEvents(message)
    .filter(e -> e.getType() == AgentEventType.PLAN_EXIT_REQUESTED)
    .doOnNext(e -> {
        String plan = readPlanFile(workspace);
        List<String> affectedFiles = extractFilesFromPlan(plan); // 解析 plan 中的文件列表
        String gitStatus = shellExec("git status --short");
        RiskLevel risk = assessRisk(plan); // 从 plan 中解析风险关键词

        showApprovalDialog(plan, affectedFiles, gitStatus, risk);
    });
```

### 审批界面必须包含的信息

| 信息项 | 目的 |
|---|---|
| PLAN.md 内容 | 核心计划 |
| 当前 git status | 了解有无未提交改动 |
| 受影响文件的当前内容片段 | 让审批者看到"改前"状态 |
| 预估风险等级 | 快速判断是否需要深度审查 |

### 风险等级解析示例

```java
RiskLevel assessRisk(String plan) {
    boolean hasDbMigration = plan.contains("migration") || plan.contains("ALTER TABLE");
    boolean hasFileDelete  = plan.contains("delete") || plan.contains("删除文件");
    boolean hasExternalApi = plan.contains("HTTP") || plan.contains("外部调用");

    if (hasDbMigration || hasFileDelete) return RiskLevel.HIGH;
    if (hasExternalApi) return RiskLevel.MEDIUM;
    return RiskLevel.LOW;
}
```

---

## 六、拒绝循环（Plan Rejection Loop）

官方文档没有提供这个机制，但实际编程场景中人类经常需要修改计划。

```java
while (true) {
    AgentResult result = agent.runAsync(message, ctx).block();

    if (!agent.isPlanModeActive(ctx)) break; // 正常完成，退出循环

    // 展示审批 UI
    ApprovalResult approval = showApprovalUI(readPlanFile(ctx));

    if (approval.isApproved()) {
        agent.exitPlanMode(ctx); // 程序化放行
        break;
    } else {
        // 把拒绝原因反注入，让模型修改计划
        String feedback = approval.getFeedback();
        message = Message.of(
            "PLAN REJECTED: " + feedback + "\n请重新调用 plan_write 修改计划，不要调用 plan_exit"
        );
        // 不调用 exitPlanMode，继续在 plan 阶段循环
    }
}
```

> 没有拒绝循环，人类只能"接受一切"或"放弃"，Plan Mode 的价值大打折扣。

---

## 七、分级风险控制（Permission Mode 联动）

根据 PLAN.md 内容动态调整执行阶段的权限模式：

```java
// 审批通过后，根据风险等级配置权限
RiskLevel risk = assessRisk(readPlanFile(ctx));

switch (risk) {
    case HIGH:
        // 高风险：每一步都要人工确认
        agent.setPermissionMode(ctx, PermissionMode.DEFAULT);
        break;
    case MEDIUM:
        // 中风险：拒绝外部调用而非询问
        agent.setPermissionMode(ctx, PermissionMode.DONT_ASK);
        break;
    case LOW:
        // 低风险：完全放行
        agent.setPermissionMode(ctx, PermissionMode.BYPASS);
        break;
}
```

### Permission Mode 含义

| 模式 | 行为 |
|---|---|
| `DEFAULT` | 遇到 ASK 规则时询问人类 |
| `DONT_ASK` | 把 ASK 决策转为 DENY，不询问 |
| `BYPASS` | 关闭所有规则评估，全部放行 |

> `setPermissionMode` 仅改变模式，不影响已有的 allow/deny/ask 规则；对**下一次**调用生效。

---

## 八、Plan → Todo 粒度控制

从 PLAN.md 过渡到 `todo_write` 时，任务粒度决定了执行过程的可观测性。

### 粒度对比

```
太粗（差）：
  [TODO] 重构 UserService

太细（也差）：
  [TODO] 在第 42 行加分号

合适（好）：
  [TODO] 提取 UserService.validateUser() 方法（预计改动 src/UserService.java:80-120）
  [TODO] 更新调用方 AuthController.login() 使用新方法（src/AuthController.java:35-50）
  [TODO] 新增单测 UserServiceTest.testValidateUser_nullInput
```

### System Prompt 约束

```
每个 todo 必须满足：
1. 对应单个文件的单个操作
2. 包含文件路径（最好带行号范围）
3. 可以独立验证是否完成
4. 总数控制在 5-8 个
```

### 实时监听任务状态变化

```java
agent.streamEvents(message)
    .filter(e -> e.getType() == AgentEventType.TOOL_RESULT_END)
    .filter(e -> "todo_write".equals(((ToolResultEndEvent) e).getToolCallName()))
    .doOnNext(e -> {
        List<Task> tasks = agent.getAgentState(userId, sessionId)
                .getTasksContext().getTasks();
        updateProgressUI(tasks); // 实时更新进度 UI
    })
    .subscribe();
```

---

## 九、多 Agent 计划者/执行者分离（大型任务）

对于复杂编程任务，Plan Mode 最强的用法是**计划者与执行者解耦**：

```
[Planner Agent]           [Executor Agent(s)]
  enablePlanMode()          无 plan mode
  只读工具                   执行工具全开放
  写 PLAN.md                 读 PLAN.md 作为输入
  HITL 确认后                一个 todo 一个 agent
  → 触发 Executor            互不干扰，可并行
```

```java
// 计划阶段完成后，拆分子任务并行执行
List<Task> tasks = agent.getAgentState(userId, sessionId)
    .getTasksContext().getTasks();

String planContent = readPlanFile(workspace);

// 每个 executor agent 处理一个独立文件改动
List<CompletableFuture<Void>> futures = tasks.stream()
    .filter(t -> t.getState() == TaskState.PENDING)
    .map(task -> spawnExecutorAgent(task, planContent))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

### 子 Agent 的已知 Gap

子 Agent 通过 `agent_spawn` 生成时**不继承 Plan Mode 限制**，需手动处理：

```java
// 方案一：给子 agent 也开启 plan mode（更严格）
childAgentBuilder.enablePlanMode()

// 方案二：手动过滤子 agent 的工具列表到只读集合（更灵活）
childAgentBuilder.tools(readOnlyTools)
```

---

## 十、四种终态监控

运行时需要区分 Agent 的状态：

| 状态 | 含义 | 处理建议 |
|---|---|---|
| 从未进入 plan mode | 模型决定直接执行 | 正常，无需干预 |
| 进入 → plan_exit 已调用 | 成功，等待人工确认 | 展示 PLAN.md 给审批者 |
| 在 plan mode + 有 PLAN.md | 写了计划但未退出 | 可程序化调 `exitPlanMode` 推进 |
| 在 plan mode + 无 PLAN.md | 模型只口头描述未落笔 | 提示模型调用 `plan_write` |

```java
boolean inPlan    = agent.isPlanModeActive(ctx);
boolean planExists = Files.exists(workspace.resolve("plans/PLAN.md"));
// 还需检查事件流中是否出现过 plan_enter / plan_write
```

---

## 十一、状态持久化

Plan Mode 状态存储在 `AgentState`，自动持久化：

- 进程重启后恢复
- 跨节点恢复（分布式场景）
- `plans/` 目录跟随配置的文件系统模式（本地 / 沙箱 / 远程 KV）

即使服务崩溃，用户重连后 Agent 仍知道自己处于 Plan 阶段，不会意外跳到执行阶段。

---

## 十二、Admin HTTP 端点（运维/调试用）

```
POST /v1/admin/sessions/{id}:enter-plan-mode   # 强制进入计划阶段
POST /v1/admin/sessions/{id}:exit-plan-mode    # 强制退出（不触发 HITL）
GET  /v1/admin/sessions/{id}/plan              # 读取当前 PLAN.md 内容
GET  /v1/admin/sessions/{sessionId}/tasks      # 读取任务列表
```

适合配置管控面板，或在出现异常状态时手动干预。

---

## 附：Plan Mode 适用场景判断

| 场景 | 是否适合 Plan Mode |
|---|---|
| 修改生产代码 / 数据 | ✅ 强烈推荐 |
| 任务描述模糊需先确认理解 | ✅ 推荐 |
| 多步骤复杂任务（>5 个文件） | ✅ 推荐 |
| 涉及数据库 migration | ✅ 必须 |
| 简单单文件小修改 | ⚠️ 可选，看团队习惯 |
| 纯查询 / 只读任务 | ❌ 不需要 |
| 紧急修复（时间敏感） | ❌ 酌情跳过 |
