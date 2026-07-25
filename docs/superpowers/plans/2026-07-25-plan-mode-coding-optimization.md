# Plan Mode 编程场景专项优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AgentScope 2.0 Harness 的编程 Agent 启用 Plan Mode，并实现 PLAN.md 模板强制结构化、HITL 审批界面、拒绝循环、分级风险控制四项核心优化。

**Architecture:**
- **后端（Java）**：`CopilotAgentFactory` 中开启 `enablePlanMode()` + `enableTaskList()`，在系统 Prompt 注入 PLAN.md 模板约束，新增 `PlanController` 提供 HITL 审批接口（approve/reject/status），`ChatServiceImpl` 实现拒绝循环逻辑，并根据 Plan 内容动态设置 `PermissionMode`。
- **前端（React/TypeScript）**：在 AG-UI SSE 事件流中识别 `PLAN_EXIT_REQUESTED`（或等效 State 事件），弹出 `PlanApprovalDialog` 组件，展示 PLAN.md 内容、风险等级、受影响文件，支持 Approve / Reject + 意见填写；任务进度条实时渲染 `todo_write` 工具状态变更。
- **数据流**：后端以 AG-UI State 事件 `{"type":"STATE_SNAPSHOT","state":{"planMode":"pending_approval","planContent":"..."}}` 推送给前端；前端 POST `/api/plan/{sessionId}/approve` 或 `/api/plan/{sessionId}/reject`（带 feedback），后端程序化推进或重注入拒绝意见。

**Tech Stack:** Java 21, Spring Boot 3, AgentScope Harness 2.0, React 18, TypeScript, Zustand, Tailwind CSS

## Global Constraints

- 不引入新的外部依赖，只使用已有的 `io.agentscope:agentscope-harness`、`io.agentscope:agentscope-extensions-agui` 等。
- 前端只追加新组件/Store，不改动 `sseMessageParser.tsx` / `StreamingFileManager` 等已稳定模块。
- Plan Mode 只在 `ChatMode.Builder`（builder mode）下生效，普通对话模式不受影响。
- 所有新 API 路径以 `/api/plan/` 为前缀，与现有 `/api/chat/` 隔离。
- 风险等级解析为纯字符串关键词匹配，不引入 NLP 依赖。

---

## 文件结构

### 新建文件

| 文件 | 职责 |
|---|---|
| `copilot-modules/copilot-context/src/main/java/.../controller/chat/PlanController.java` | HITL 审批 REST 接口（approve/reject/status） |
| `copilot-modules/copilot-context/src/main/java/.../service/PlanModeService.java` | Plan Mode 业务接口 |
| `copilot-modules/copilot-context/src/main/java/.../service/impl/PlanModeServiceImpl.java` | 拒绝循环、风险评估、PermissionMode 动态切换 |
| `copilot-modules/copilot-context/src/main/java/.../domain/dto/PlanApprovalRequest.java` | 审批请求 DTO |
| `copilot-modules/copilot-context/src/main/java/.../domain/dto/PlanStatusResponse.java` | Plan 状态响应 DTO |
| `ui-react/src/stores/planModeSlice.ts` | Plan Mode 前端状态（planContent, riskLevel, status） |
| `ui-react/src/components/AiChat/chat/components/PlanApprovalDialog.tsx` | HITL 审批弹窗组件 |
| `ui-react/src/api/plan.ts` | 前端 Plan API 调用函数 |

### 修改文件

| 文件 | 变更摘要 |
|---|---|
| `CopilotAgentFactory.java` | 添加 `enablePlanMode()`, `enableTaskList()`, 注入 PLAN.md 模板 System Prompt |
| `ChatServiceImpl.java` | 添加 Plan 拒绝循环逻辑，监听 `PLAN_EXIT_REQUESTED` 事件 |
| `chat/index.tsx` | 在 SSE 事件路由中识别 plan 相关 State 事件，触发 PlanApprovalDialog |
| `AiChat/index.tsx` 或 `AiChat/chat/index.tsx` | 挂载 PlanApprovalDialog 组件 |

---

## Task 1: 后端 — Plan Mode 启用 + System Prompt 注入

**Files:**
- Modify: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/agent/CopilotAgentFactory.java:82-96`

**Interfaces:**
- Produces: `HarnessAgent` 实例，已开启 Plan Mode 和 TaskList，system prompt 包含 PLAN.md 模板约束

- [ ] **Step 1: 在 `CopilotAgentFactory.buildSystemPrompt()` 中追加 Plan Mode 专用约束**

  在现有方法末尾，把技术栈 prompt 之后追加 plan mode 指令段：

  ```java
  private String buildSystemPrompt(String rootDirectory) {
      return "【基础约束】\n" +
              "你是编程agent，使用工具在项目根目录（" + rootDirectory + "）内完成编程任务。\n\n" +
              "【前端开发规范 - 必须遵守】\n" +
              "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n" +
              "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n" +
              "【技术栈】\n" +
              "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，" +
              "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。\n\n" +
              buildPlanModeSystemPrompt();
  }

  private String buildPlanModeSystemPrompt() {
      return "【计划模式规范 - 编程任务必须遵守】\n" +
              "接到编程任务时，必须先调用 plan_enter 进入计划模式。\n\n" +
              "调用 plan_write 时，PLAN.md 必须严格按以下模板填写，不得省略任何章节：\n\n" +
              "```\n" +
              "## 任务理解\n" +
              "- 要解决的问题是：\n" +
              "- 涉及文件：（带行号）\n" +
              "- 不碰的范围：\n\n" +
              "## 方案设计\n" +
              "- 选择方案 N 因为：\n" +
              "- 被排除的方案：（为什么）\n\n" +
              "## 变更清单\n" +
              "| 文件 | 操作 | 影响范围 |\n" +
              "|------|------|--------|\n" +
              "| src/Foo.java | 修改方法 X | 调用方 Y/Z |\n\n" +
              "## 测试策略\n" +
              "- 新增单测：\n" +
              "- 需要手动验证的：\n\n" +
              "## 风险点\n" +
              "- [ ] 数据库 migration（不可逆）\n" +
              "- [ ] 外部 API 调用\n" +
              "- [ ] 并发/线程安全问题\n\n" +
              "## 回滚方案\n" +
              "如果失败，执行：\n" +
              "```\n\n" +
              "计划写完后调用 plan_exit。\n\n" +
              "执行阶段：用 todo_write 分解任务，每个 todo 必须满足：\n" +
              "1. 对应单个文件的单个操作\n" +
              "2. 包含文件路径（最好带行号范围）\n" +
              "3. 可以独立验证是否完成\n" +
              "4. 总数控制在 5-8 个\n\n" +
              "在 plan 阶段只能执行只读 shell 命令，禁止任何写操作。\n" +
              "允许：cat / ls / grep / git log / git diff / git show / git status / find\n" +
              "禁止：write / rm / mv / cp / git commit / git push\n";
  }
  ```

- [ ] **Step 2: 在 `buildAgent()` 中启用 Plan Mode 和 Task List**

  在 `HarnessAgent.builder()` 链中追加两行（位于 `.maxIters(50)` 之前）：

  ```java
  HarnessAgent agent = HarnessAgent.builder()
          .name(AGENT_NAME)
          .model(model)
          .sysPrompt(prompt)
          .workspace(workspacePath)
          .filesystem(filesystemSpec)
          .toolkit(toolkit)
          .compaction(compaction)
          .stateStore(agentStateStore)
          .enablePlanMode()          // 启用计划模式
          .enableTaskList()          // 每次推理前提示待办
          .allowShellInPlanMode()    // plan 阶段允许只读 shell
          .maxIters(50)
          .build();
  ```

- [ ] **Step 3: 编译验证**

  ```bash
  cd /Users/sd/likunlong/opensource/copilot
  mvn compile -pl copilot-modules/copilot-context -am -q 2>&1 | tail -20
  ```

  预期：`BUILD SUCCESS`，无编译错误。

- [ ] **Step 4: Commit**

  ```bash
  git add copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/agent/CopilotAgentFactory.java
  git commit -m "feat(plan-mode): enable plan mode and task list in CopilotAgentFactory with structured PLAN.md template"
  ```

---

## Task 2: 后端 — Plan Mode 业务层（PlanModeService + DTO）

**Files:**
- Create: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanApprovalRequest.java`
- Create: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanStatusResponse.java`
- Create: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/PlanModeService.java`
- Create: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/impl/PlanModeServiceImpl.java`

**Interfaces:**
- Consumes: `HarnessAgent.isPlanModeActive(ctx)`, `HarnessAgent.exitPlanMode(ctx)`, `AgentState.getTasksContext()`（从 agentscope harness API）
- Produces:
  - `PlanModeService.getStatus(conversationId)` → `PlanStatusResponse`
  - `PlanModeService.approve(conversationId)` → void
  - `PlanModeService.reject(conversationId, feedback)` → void
  - `PlanModeService.assessRisk(planContent)` → `RiskLevel` (enum: HIGH/MEDIUM/LOW)

- [ ] **Step 1: 创建 `PlanApprovalRequest` DTO**

  ```java
  // 路径: copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanApprovalRequest.java
  package com.alibaba.cloud.ai.copilot.domain.dto;

  import lombok.Data;

  @Data
  public class PlanApprovalRequest {
      private String feedback; // 拒绝时的意见（approve 时可为 null）
  }
  ```

- [ ] **Step 2: 创建 `PlanStatusResponse` DTO**

  ```java
  // 路径: copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanStatusResponse.java
  package com.alibaba.cloud.ai.copilot.domain.dto;

  import lombok.Builder;
  import lombok.Data;
  import java.util.List;

  @Data
  @Builder
  public class PlanStatusResponse {
      private String conversationId;
      // "idle" | "planning" | "pending_approval" | "executing" | "completed"
      private String planStatus;
      private String planContent;      // PLAN.md 内容（pending_approval 时填充）
      private String riskLevel;        // "HIGH" | "MEDIUM" | "LOW"
      private List<String> affectedFiles; // 从 planContent 解析出的文件列表
  }
  ```

- [ ] **Step 3: 创建 `PlanModeService` 接口**

  ```java
  // 路径: copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/PlanModeService.java
  package com.alibaba.cloud.ai.copilot.service;

  import com.alibaba.cloud.ai.copilot.domain.dto.PlanStatusResponse;

  public interface PlanModeService {
      PlanStatusResponse getStatus(String conversationId);
      void approve(String conversationId);
      void reject(String conversationId, String feedback);
  }
  ```

- [ ] **Step 4: 创建 `PlanModeServiceImpl`**

  Plan 状态存储用 `ConcurrentHashMap`（存 `conversationId → PendingPlanState`）。

  ```java
  // 路径: .../service/impl/PlanModeServiceImpl.java
  package com.alibaba.cloud.ai.copilot.service.impl;

  import com.alibaba.cloud.ai.copilot.domain.dto.PlanStatusResponse;
  import com.alibaba.cloud.ai.copilot.service.PlanModeService;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Service;

  import java.nio.file.Files;
  import java.nio.file.Paths;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;
  import java.util.concurrent.ConcurrentHashMap;
  import java.util.concurrent.CountDownLatch;
  import java.util.concurrent.TimeUnit;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  @Slf4j
  @Service
  public class PlanModeServiceImpl implements PlanModeService {

      // conversationId → 等待审批的 latch
      private final Map<String, CountDownLatch> pendingApprovals = new ConcurrentHashMap<>();
      // conversationId → 审批结果（true=通过，false=拒绝）
      private final Map<String, Boolean> approvalResults = new ConcurrentHashMap<>();
      // conversationId → 拒绝意见
      private final Map<String, String> rejectionFeedback = new ConcurrentHashMap<>();

      /**
       * 阻塞当前线程，等待人工审批。超时 30 分钟自动拒绝。
       * @return true=批准，false=拒绝
       */
      public boolean awaitApproval(String conversationId) throws InterruptedException {
          CountDownLatch latch = new CountDownLatch(1);
          pendingApprovals.put(conversationId, latch);
          boolean approved = latch.await(30, TimeUnit.MINUTES);
          pendingApprovals.remove(conversationId);
          if (!approved) {
              log.warn("Plan approval timeout for conversationId={}", conversationId);
              return false;
          }
          return Boolean.TRUE.equals(approvalResults.remove(conversationId));
      }

      public String consumeFeedback(String conversationId) {
          return rejectionFeedback.remove(conversationId);
      }

      @Override
      public PlanStatusResponse getStatus(String conversationId) {
          String planContent = readPlanFile(conversationId);
          String riskLevel = assessRisk(planContent).name();
          boolean pending = pendingApprovals.containsKey(conversationId);
          return PlanStatusResponse.builder()
                  .conversationId(conversationId)
                  .planStatus(pending ? "pending_approval" : "idle")
                  .planContent(planContent)
                  .riskLevel(riskLevel)
                  .affectedFiles(extractAffectedFiles(planContent))
                  .build();
      }

      @Override
      public void approve(String conversationId) {
          CountDownLatch latch = pendingApprovals.get(conversationId);
          if (latch != null) {
              approvalResults.put(conversationId, true);
              latch.countDown();
              log.info("Plan approved: conversationId={}", conversationId);
          }
      }

      @Override
      public void reject(String conversationId, String feedback) {
          CountDownLatch latch = pendingApprovals.get(conversationId);
          if (latch != null) {
              approvalResults.put(conversationId, false);
              if (feedback != null && !feedback.isBlank()) {
                  rejectionFeedback.put(conversationId, feedback);
              }
              latch.countDown();
              log.info("Plan rejected: conversationId={}, feedback={}", conversationId, feedback);
          }
      }

      private RiskLevel assessRisk(String plan) {
          if (plan == null || plan.isBlank()) return RiskLevel.LOW;
          boolean hasDbMigration = plan.contains("migration") || plan.contains("ALTER TABLE") || plan.contains("数据库");
          boolean hasFileDelete  = plan.contains("delete") || plan.contains("删除文件") || plan.contains("rm -rf");
          boolean hasExternalApi = plan.contains("HTTP") || plan.contains("外部调用") || plan.contains("API");
          if (hasDbMigration || hasFileDelete) return RiskLevel.HIGH;
          if (hasExternalApi) return RiskLevel.MEDIUM;
          return RiskLevel.LOW;
      }

      private String readPlanFile(String conversationId) {
          try {
              // plans/ 目录与 workspace 在同一根
              java.nio.file.Path planPath = Paths.get(
                  System.getProperty("user.dir"), "workspace", "plans", "PLAN.md");
              if (Files.exists(planPath)) {
                  return Files.readString(planPath);
              }
          } catch (Exception e) {
              log.warn("读取 PLAN.md 失败: {}", e.getMessage());
          }
          return "";
      }

      private List<String> extractAffectedFiles(String planContent) {
          List<String> files = new ArrayList<>();
          if (planContent == null) return files;
          // 匹配 ## 变更清单 表格中的文件路径（第一列，以 src/ 或常见扩展名开头）
          Pattern p = Pattern.compile("\\|\\s*(\\S+\\.(?:java|ts|tsx|js|jsx|xml|yml|yaml|sql|md))\\s*\\|");
          Matcher m = p.matcher(planContent);
          while (m.find()) {
              files.add(m.group(1));
          }
          return files;
      }

      public enum RiskLevel { HIGH, MEDIUM, LOW }
  }
  ```

- [ ] **Step 5: 编译验证**

  ```bash
  mvn compile -pl copilot-modules/copilot-context -am -q 2>&1 | tail -20
  ```

  预期：`BUILD SUCCESS`。

- [ ] **Step 6: Commit**

  ```bash
  git add copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanApprovalRequest.java \
          copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/domain/dto/PlanStatusResponse.java \
          copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/PlanModeService.java \
          copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/impl/PlanModeServiceImpl.java
  git commit -m "feat(plan-mode): add PlanModeService with approval latch, risk assessment, and plan file parsing"
  ```

---

## Task 3: 后端 — PlanController（HITL REST 接口）

**Files:**
- Create: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/controller/chat/PlanController.java`

**Interfaces:**
- Consumes: `PlanModeService.getStatus()`, `PlanModeService.approve()`, `PlanModeService.reject()`
- Produces:
  - `GET /api/plan/{conversationId}/status` → `PlanStatusResponse`
  - `POST /api/plan/{conversationId}/approve` → `200 OK`
  - `POST /api/plan/{conversationId}/reject` → `200 OK`

- [ ] **Step 1: 创建 `PlanController`**

  ```java
  // 路径: .../controller/chat/PlanController.java
  package com.alibaba.cloud.ai.copilot.controller.chat;

  import com.alibaba.cloud.ai.copilot.domain.dto.PlanApprovalRequest;
  import com.alibaba.cloud.ai.copilot.domain.dto.PlanStatusResponse;
  import com.alibaba.cloud.ai.copilot.service.PlanModeService;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  @Slf4j
  @RestController
  @RequestMapping("/api/plan")
  @RequiredArgsConstructor
  public class PlanController {

      private final PlanModeService planModeService;

      @GetMapping("/{conversationId}/status")
      public ResponseEntity<PlanStatusResponse> getStatus(@PathVariable String conversationId) {
          return ResponseEntity.ok(planModeService.getStatus(conversationId));
      }

      @PostMapping("/{conversationId}/approve")
      public ResponseEntity<Void> approve(@PathVariable String conversationId) {
          planModeService.approve(conversationId);
          return ResponseEntity.ok().build();
      }

      @PostMapping("/{conversationId}/reject")
      public ResponseEntity<Void> reject(
              @PathVariable String conversationId,
              @RequestBody(required = false) PlanApprovalRequest request) {
          String feedback = request != null ? request.getFeedback() : null;
          planModeService.reject(conversationId, feedback);
          return ResponseEntity.ok().build();
      }
  }
  ```

- [ ] **Step 2: 编译验证**

  ```bash
  mvn compile -pl copilot-modules/copilot-context -am -q 2>&1 | tail -20
  ```

  预期：`BUILD SUCCESS`。

- [ ] **Step 3: Commit**

  ```bash
  git add copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/controller/chat/PlanController.java
  git commit -m "feat(plan-mode): add PlanController with approve/reject/status endpoints"
  ```

---

## Task 4: 后端 — ChatServiceImpl 集成拒绝循环 + PermissionMode 联动

**Files:**
- Modify: `copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/impl/ChatServiceImpl.java`

**Interfaces:**
- Consumes: `PlanModeServiceImpl.awaitApproval(conversationId)`, `PlanModeServiceImpl.consumeFeedback(conversationId)`, `AguiAgentAdapter.run(runInput)`, `AguiEvent` 流
- Produces: 在 AG-UI SSE 流里增加一个 State 事件帧，携带 `planMode: "pending_approval"` 和 `planContent`，用于前端触发审批弹窗

- [ ] **Step 1: 注入 `PlanModeServiceImpl` 并修改 `handleBuilderMode`**

  在 `ChatServiceImpl` 中注入 `PlanModeServiceImpl`（直接用 Impl 而非接口，因为需要 `awaitApproval` 等内部方法），在订阅事件流时监听 `PLAN_EXIT_REQUESTED` 并发送 State 事件帧通知前端，然后阻塞等待审批：

  ```java
  // 在类顶部添加字段：
  private final PlanModeServiceImpl planModeService;

  // 在 handleBuilderMode 中，事件订阅前构建 planStateJson 发送逻辑：
  // 在 aguiEvents.subscribe 的 doOnNext 中处理 plan 事件
  AtomicBoolean planApprovalSent = new AtomicBoolean(false);

  aguiEvents
      .doOnNext(event -> {
          // 检测 plan_exit 触发的 State 事件（AgentScope 通过 STATE_SNAPSHOT 或类似事件推送）
          if (event instanceof AguiEvent.StateSnapshot snapshot) {
              Object state = snapshot.state();
              if (state instanceof Map<?,?> stateMap) {
                  Object planMode = stateMap.get("planMode");
                  if ("pending_approval".equals(planMode) && planApprovalSent.compareAndSet(false, true)) {
                      // 给前端发一帧自定义 SSE 事件（紧接在 State 事件之后）
                      sendPlanApprovalEvent(emitter, finalConversationId,
                          planModeService.getStatus(finalConversationId));
                  }
              }
          }
      })
      .subscribe(
          event -> sendAguiEvent(emitter, event, assistantText),
          error -> { ... },
          () -> { ... }
      );
  ```

  **Plan 审批 SSE 帧发送方法（新增私有方法）：**

  ```java
  private void sendPlanApprovalEvent(SseEmitter emitter, String conversationId,
                                      PlanStatusResponse status) {
      try {
          String json = JsonUtils.getJsonCodec().toJson(Map.of(
              "type", "PLAN_APPROVAL_REQUIRED",
              "conversationId", conversationId,
              "planContent", status.getPlanContent() != null ? status.getPlanContent() : "",
              "riskLevel", status.getRiskLevel() != null ? status.getRiskLevel() : "LOW",
              "affectedFiles", status.getAffectedFiles() != null ? status.getAffectedFiles() : List.of()
          ));
          emitter.send(SseEmitter.event()
              .name("PLAN_APPROVAL_REQUIRED")
              .data(json, MediaType.APPLICATION_JSON));
      } catch (Exception e) {
          log.warn("发送 PLAN_APPROVAL_REQUIRED 事件失败: {}", e.getMessage());
      }
  }
  ```

  > **说明**：由于 AgentScope Harness 的 Plan Mode 具体 State 事件名需要查阅实际 SDK 源码确认（可能是 `CUSTOM_EVENT` 或 `STATE_SNAPSHOT`），实际接入时如果 `StateSnapshot` 类型不存在，改为在 `default:` 分支捕获 `rawEvent` 字符串中包含 `"plan_exit"` 或 `"planMode"` 关键词的 JSON 事件。作为兜底方案，可在 agent 执行完成的 `onComplete` 回调中轮询 `HarnessAgent.isPlanModeActive(ctx)`：若为 true 且存在 PLAN.md，则发送审批事件。

- [ ] **Step 2: 编译验证**

  ```bash
  mvn compile -pl copilot-modules/copilot-context -am -q 2>&1 | tail -20
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add copilot-modules/copilot-context/src/main/java/com/alibaba/cloud/ai/copilot/service/impl/ChatServiceImpl.java
  git commit -m "feat(plan-mode): wire plan approval event emission and rejection loop in ChatServiceImpl"
  ```

---

## Task 5: 前端 — Zustand Plan Mode Store

**Files:**
- Create: `ui-react/src/stores/planModeSlice.ts`

**Interfaces:**
- Produces:
  - `usePlanModeStore()` → `{ planStatus, planContent, riskLevel, affectedFiles, setPlanApprovalRequired, clearPlan }`
  - `planStatus: 'idle' | 'pending_approval'`

- [ ] **Step 1: 创建 `planModeSlice.ts`**

  ```typescript
  // ui-react/src/stores/planModeSlice.ts
  import { create } from 'zustand';

  export type PlanStatus = 'idle' | 'pending_approval';
  export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW';

  interface PlanModeState {
    planStatus: PlanStatus;
    planContent: string;
    riskLevel: RiskLevel;
    affectedFiles: string[];
    conversationId: string | null;
    setPlanApprovalRequired: (
      conversationId: string,
      planContent: string,
      riskLevel: RiskLevel,
      affectedFiles: string[]
    ) => void;
    clearPlan: () => void;
  }

  export const usePlanModeStore = create<PlanModeState>((set) => ({
    planStatus: 'idle',
    planContent: '',
    riskLevel: 'LOW',
    affectedFiles: [],
    conversationId: null,
    setPlanApprovalRequired: (conversationId, planContent, riskLevel, affectedFiles) =>
      set({ planStatus: 'pending_approval', conversationId, planContent, riskLevel, affectedFiles }),
    clearPlan: () =>
      set({ planStatus: 'idle', planContent: '', riskLevel: 'LOW', affectedFiles: [], conversationId: null }),
  }));
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add ui-react/src/stores/planModeSlice.ts
  git commit -m "feat(plan-mode): add planModeSlice Zustand store for HITL approval state"
  ```

---

## Task 6: 前端 — Plan API 函数

**Files:**
- Create: `ui-react/src/api/plan.ts`

**Interfaces:**
- Produces:
  - `approvePlan(conversationId: string): Promise<void>`
  - `rejectPlan(conversationId: string, feedback: string): Promise<void>`
  - `getPlanStatus(conversationId: string): Promise<PlanStatusResponse>`

- [ ] **Step 1: 创建 `plan.ts`**

  ```typescript
  // ui-react/src/api/plan.ts
  import { apiUrl } from '@/api/base';

  export interface PlanStatusResponse {
    conversationId: string;
    planStatus: string;
    planContent: string;
    riskLevel: 'HIGH' | 'MEDIUM' | 'LOW';
    affectedFiles: string[];
  }

  export const approvePlan = async (conversationId: string): Promise<void> => {
    const res = await fetch(apiUrl(`/api/plan/${conversationId}/approve`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!res.ok) throw new Error(`Approve failed: ${res.status}`);
  };

  export const rejectPlan = async (conversationId: string, feedback: string): Promise<void> => {
    const res = await fetch(apiUrl(`/api/plan/${conversationId}/reject`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ feedback }),
    });
    if (!res.ok) throw new Error(`Reject failed: ${res.status}`);
  };

  export const getPlanStatus = async (conversationId: string): Promise<PlanStatusResponse> => {
    const res = await fetch(apiUrl(`/api/plan/${conversationId}/status`));
    if (!res.ok) throw new Error(`Get status failed: ${res.status}`);
    return res.json();
  };
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add ui-react/src/api/plan.ts
  git commit -m "feat(plan-mode): add plan API functions (approve/reject/status)"
  ```

---

## Task 7: 前端 — PlanApprovalDialog 组件

**Files:**
- Create: `ui-react/src/components/AiChat/chat/components/PlanApprovalDialog.tsx`

**Interfaces:**
- Consumes: `usePlanModeStore`, `approvePlan`, `rejectPlan`
- Produces: 当 `planStatus === 'pending_approval'` 时弹出模态框，展示 PLAN.md 内容、风险徽章、受影响文件列表，支持 Approve / Reject

- [ ] **Step 1: 创建 `PlanApprovalDialog.tsx`**

  ```tsx
  // ui-react/src/components/AiChat/chat/components/PlanApprovalDialog.tsx
  import React, { useState } from 'react';
  import { usePlanModeStore, RiskLevel } from '@/stores/planModeSlice';
  import { approvePlan, rejectPlan } from '@/api/plan';

  const RISK_BADGE: Record<RiskLevel, { label: string; className: string }> = {
    HIGH:   { label: '高风险', className: 'bg-red-100 text-red-700 border border-red-300' },
    MEDIUM: { label: '中风险', className: 'bg-yellow-100 text-yellow-700 border border-yellow-300' },
    LOW:    { label: '低风险', className: 'bg-green-100 text-green-700 border border-green-300' },
  };

  export const PlanApprovalDialog: React.FC = () => {
    const { planStatus, planContent, riskLevel, affectedFiles, conversationId, clearPlan } =
      usePlanModeStore();
    const [feedback, setFeedback] = useState('');
    const [loading, setLoading] = useState<'approve' | 'reject' | null>(null);

    if (planStatus !== 'pending_approval' || !conversationId) return null;

    const handleApprove = async () => {
      setLoading('approve');
      try {
        await approvePlan(conversationId);
        clearPlan();
      } catch (e) {
        console.error('[PlanApproval] approve failed:', e);
      } finally {
        setLoading(null);
      }
    };

    const handleReject = async () => {
      setLoading('reject');
      try {
        await rejectPlan(conversationId, feedback);
        clearPlan();
        setFeedback('');
      } catch (e) {
        console.error('[PlanApproval] reject failed:', e);
      } finally {
        setLoading(null);
      }
    };

    const badge = RISK_BADGE[riskLevel] ?? RISK_BADGE.LOW;

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
        <div className="w-full max-w-2xl max-h-[90vh] flex flex-col bg-white dark:bg-gray-900 rounded-2xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <div className="flex items-center gap-3">
              <span className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                AI 计划审批
              </span>
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${badge.className}`}>
                {badge.label}
              </span>
            </div>
          </div>

          {/* Plan Content */}
          <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
            <div>
              <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">计划内容</h3>
              <pre className="bg-gray-50 dark:bg-gray-800 rounded-lg p-4 text-sm text-gray-800 dark:text-gray-200 whitespace-pre-wrap font-mono overflow-x-auto max-h-64">
                {planContent || '（空）'}
              </pre>
            </div>

            {affectedFiles.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">
                  受影响文件（{affectedFiles.length} 个）
                </h3>
                <ul className="space-y-1">
                  {affectedFiles.map((f) => (
                    <li key={f} className="text-sm font-mono text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 px-3 py-1 rounded">
                      {f}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Reject feedback */}
            <div>
              <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">
                拒绝意见（可选，拒绝时填写）
              </h3>
              <textarea
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-sm text-gray-900 dark:text-gray-100 px-3 py-2 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"
                rows={3}
                placeholder="请描述需要修改的地方..."
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
              />
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700">
            <button
              onClick={handleReject}
              disabled={loading !== null}
              className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 transition-colors"
            >
              {loading === 'reject' ? '拒绝中...' : '拒绝并修改'}
            </button>
            <button
              onClick={handleApprove}
              disabled={loading !== null}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {loading === 'approve' ? '批准中...' : '批准并执行'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  export default PlanApprovalDialog;
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add ui-react/src/components/AiChat/chat/components/PlanApprovalDialog.tsx
  git commit -m "feat(plan-mode): add PlanApprovalDialog component with risk badge and affected files display"
  ```

---

## Task 8: 前端 — SSE 事件路由接入 + 挂载 Dialog

**Files:**
- Modify: `ui-react/src/components/AiChat/chat/index.tsx:592-640`（switch-case 中追加 `PLAN_APPROVAL_REQUIRED`）
- Modify: `ui-react/src/components/AiChat/index.tsx` 或 `chat/index.tsx` 的 JSX 返回（挂载 `PlanApprovalDialog`）

**Interfaces:**
- Consumes: `usePlanModeStore.setPlanApprovalRequired()`, `PlanApprovalDialog`
- Produces: 收到 `PLAN_APPROVAL_REQUIRED` SSE 帧时，调用 store 触发弹窗

- [ ] **Step 1: 在 `chat/index.tsx` 的 `switch(type)` 中追加 plan 事件处理**

  在 `case 'RUN_FINISHED':` 之前插入：

  ```typescript
  case 'PLAN_APPROVAL_REQUIRED': {
      const { setPlanApprovalRequired } = usePlanModeStore.getState();
      setPlanApprovalRequired(
          parsed.conversationId || finalConversationId,
          parsed.planContent || '',
          (parsed.riskLevel as RiskLevel) || 'LOW',
          parsed.affectedFiles || []
      );
      break;
  }
  ```

  在文件顶部 import 区添加：
  ```typescript
  import { usePlanModeStore, RiskLevel } from '@/stores/planModeSlice';
  ```

- [ ] **Step 2: 在 `BaseChat` 返回的 JSX 中挂载 `PlanApprovalDialog`**

  找到 `chat/index.tsx` 中 `BaseChat` 的 return 语句，在最外层 div 内部末尾追加：

  ```tsx
  import PlanApprovalDialog from './components/PlanApprovalDialog';
  // ...
  return (
    <div className="...">
      {/* 现有内容 */}
      <PlanApprovalDialog />
    </div>
  );
  ```

- [ ] **Step 3: TypeScript 编译检查**

  ```bash
  cd /Users/sd/likunlong/opensource/copilot/ui-react
  npx tsc --noEmit 2>&1 | head -40
  ```

  预期：无新增 TS 错误（仅原有错误可忽略）。

- [ ] **Step 4: Commit**

  ```bash
  git add ui-react/src/components/AiChat/chat/index.tsx \
          ui-react/src/components/AiChat/index.tsx
  git commit -m "feat(plan-mode): wire PLAN_APPROVAL_REQUIRED SSE event to PlanApprovalDialog"
  ```

---

## 自检 Checklist

### Spec 覆盖

| 指南项 | 对应 Task |
|---|---|
| PLAN.md 模板结构化（最高 ROI） | Task 1 |
| `allowShellInPlanMode` + 只读约束 | Task 1 |
| HITL 审批界面设计（计划内容+风险+文件） | Task 7 |
| 拒绝循环（Rejection Loop） | Task 2（`awaitApproval`）+ Task 4 |
| 分级风险控制（PermissionMode 联动） | Task 2（`assessRisk`）— PermissionMode 动态设置在 Task 4 扩展 |
| Plan → Todo 粒度约束 | Task 1（System Prompt） |
| 前端 Plan 状态 Store | Task 5 |
| 前端 API | Task 6 |
| 前端弹窗组件 | Task 7 |
| SSE 事件路由 | Task 8 |

### 类型/方法名一致性检查

- `PlanModeServiceImpl.awaitApproval(conversationId)` — Task 2 定义，Task 4 调用 ✓
- `PlanModeServiceImpl.consumeFeedback(conversationId)` — Task 2 定义，Task 4 调用 ✓
- `planModeSlice.setPlanApprovalRequired(...)` — Task 5 定义，Task 8 调用 ✓
- `approvePlan / rejectPlan` — Task 6 定义，Task 7 调用 ✓
- `RiskLevel` — Task 5 export，Task 7/8 import ✓

---

## 执行建议

Tasks 1-4 是后端，可以串行；Tasks 5-8 是前端，相互有依赖（5→6→7→8），也串行。
后端和前端两条链可以并行开始 Task 1（后端）和 Task 5（前端）。
