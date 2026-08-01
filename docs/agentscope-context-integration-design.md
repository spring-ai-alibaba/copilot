# AgentScope Java 2.0 上下文集成设计与实现

> 状态：上下文阶段已完整落地
>
> 范围：短期上下文、持久化、压缩、状态查询、重置、AG-UI 可观测性与完整前端交互
>
> 暂不包含：AgentScope Sandbox、长期记忆、RAG、Skill、子 Agent

## 1. 结论

AgentScope 能满足当前系统的会话上下文需求，不需要额外引入 Python Runtime 或独立上下文服务。
仓库已经使用 AgentScope Java `2.0.0`，本次直接复用以下能力：

- `RuntimeContext(userId, sessionId)`：隔离用户和会话。
- `AgentState` / `AgentStateStore`：保存模型下一轮实际读取的消息、工具状态和压缩摘要。
- `MysqlAgentStateStore`：跨请求、跨进程重启持久化上下文。
- `CompactionMiddleware`：长会话 token 阈值压缩。
- `AguiAgentAdapter`：继续输出现有 AG-UI SSE 协议。

AgentScope 是运行时能力层，不替代系统已有的登录鉴权、会话归属、模型权限、历史展示和业务数据。
在单节点 Spring Boot + MySQL 的当前部署边界内，上下文阶段的后端链路和前端体验均已完整接通。
多副本部署前仍需要分布式 session guard，并验证或替换为适合多节点并发写入的 State Store。

## 2. 责任边界

| 领域 | 事实源 | 说明 |
| --- | --- | --- |
| 登录身份 | Sa-Token | `userId` 只从服务端认证上下文读取 |
| 会话归属、标题、模型绑定 | `chat_conversation` | 所有上下文操作前先校验归属 |
| 模型下一轮看到的上下文 | AgentScope `AgentStateStore` | 不从 `chat_message` 反向重建 |
| 前端可见时间线 | `chat_message` | 只保存适合展示的 user/assistant 文本 |
| 单次运行状态 | AG-UI event stream | 以 `threadId/runId` 标识 |
| 长期记忆 | 延期 | 不在本期注入偏好或跨会话事实 |

`chat_message` 与 `AgentState` 允许不同。前者面向人类阅读，后者保留 Agent 恢复下一轮所需的
工具调用、工具结果、摘要等结构，不能用展示消息覆盖 AgentScope 状态。

## 3. 当前架构

```mermaid
flowchart LR
    UI["React Chat UI"] -->|"POST /api/chat"| API["ChatController"]
    API --> CHAT["ChatServiceImpl"]
    CHAT --> AUTH["会话与模型权限校验"]
    CHAT --> GUARD["SessionRunGuard"]
    CHAT --> FACTORY["CopilotAgentFactory"]
    FACTORY --> AGENT["请求级 HarnessAgent"]
    CHAT --> DELEGATE["AuthenticatedAgentDelegate"]
    DELEGATE --> ADAPTER["AgentScope AguiAgentAdapter"]
    ADAPTER --> AGENT
    AGENT <--> STORE[("MysqlAgentStateStore")]
    ADAPTER -->|"AG-UI SSE"| UI
    CHAT --> META["ConversationContextService"]
    META <--> STORE
    META -->|"token_usage / context_status"| UI
    CHAT --> TIMELINE[("chat_message")]
```

### 3.1 为什么不需要自研 AG-UI Adapter

AgentScope Java `2.0.0` 的 `AguiAgentAdapter` 对外只有 `run(RunAgentInput)`，但内部最终调用：

```java
agent.stream(messages, options, runtimeContext)
```

因此本次新增请求级 `AuthenticatedAgentDelegate`。它在 `stream(...)` 边界覆盖上下文中的身份：

```text
userId    = String.valueOf(LoginHelper.getUserId())
sessionId = conversationId
```

标准 `AguiAgentAdapter` 仍负责协议转换，不复制 AgentScope 的 AG-UI 实现。请求体、header 和
`forwardedProps` 都不能覆盖服务端身份；`forwardedProps` 只保留兼容性的 UI 开关和模型信息。

`AuthenticatedAgentDelegate` 使用了 AgentScope `2.0.0` 中已标记 deprecated 的 stream 重载。
当前版本可以编译运行，但升级 AgentScope 时应优先切换到新的官方调用入口并删除兼容代码。

## 4. 后端实现

### 4.1 请求准备与权限

`ChatServiceImpl` 在返回 `SseEmitter` 前同步完成：

1. 校验消息和 `modelConfigId`。
2. 从 Sa-Token 读取 `userId`。
3. 校验模型可用性和可见性。
4. 对新会话只预分配 UUID；对已有会话执行第一次归属校验。
5. 申请 `(userId, conversationId)` 的 `SessionRunGuard`；冲突返回 `409`。
6. 预读 `agent_state` 与 `context_meta`，存储不可用时在推理前返回 `503`。
7. 获得 guard 后重新校验已有会话的归属、存在性和模型绑定，关闭校验与加锁之间的删除竞态。
8. 创建请求级 `HarnessAgent`；只有 State Store preflight 和 Agent 创建都成功后，才使用预分配 ID
   持久化新业务会话。
9. 创建 `AuthenticatedAgentDelegate`、保存用户展示消息并建立 SSE 流。

新会话不会在 State Store 故障或 Agent 创建失败时留下没有回传给前端的空会话。

`ChatController` 使用局部 `@ExceptionHandler(ServiceException.class)` 处理 SSE 建立前的同步失败，
返回 `application/json` 和真实 HTTP 状态，而不是把错误码只放进 HTTP 200 的响应体。聊天入口因此会
按实际状态返回 `403/404/409/422/503`；未登录返回 `401`。SSE 建立后的 Agent 或 Store 故障继续通过
AG-UI `RUN_ERROR` 表达。

### 4.2 上下文持久化

同一个 AgentScope session 使用两个状态槽：

| slot | 内容 |
| --- | --- |
| `agent_state` | AgentScope 管理的消息、工具状态、压缩摘要等运行状态 |
| `context_meta` | revision、reset 时间、最近更新时间和本轮 token usage |

两个槽位都使用同一个物理身份：

```text
(userId, sessionId) = (authenticatedUserId, conversationId)
```

Context API 只返回计数和布尔状态，不返回原始消息、summary、系统 Prompt、工具结果或凭据。

AgentScope `2.0.0` 在 State Store 读取异常时可能以 fresh state 继续。`FailClosedAgentStateStore`
将读取故障提升到该捕获边界之外，服务层再转换为同步 `503` 或流内 `RUN_ERROR`，避免一次数据库
故障被表现为“失忆”并覆盖旧状态。这是版本兼容层，升级到原生 fail-closed 版本后应移除。

`agent_state` 是模型上下文的权威提交；`context_meta`、token usage 和 `context_status` 是其后的
可观测性投影。AgentScope 在产生成功的 `RUN_FINISHED` 前已经提交 `agent_state`，服务端随后以
best effort 方式保存 `context_meta` 并生成两个 `CUSTOM` 事件。投影失败时只记录服务端错误日志，
不把已经完成的 run 伪装成 `RUN_ERROR`，也不阻止 assistant 展示消息保存、标题更新和正常结束事件发送。
此时前端可能暂时缺少最新 token/status，但不会被诱导重试一个已经生效的运行。

### 4.3 并发与生命周期

`SessionRunGuard` 保证单 JVM 内同一用户、同一会话只有一个推理、重置或删除操作。chat、reset、
delete 都会先做快速权限检查，再在获得 guard 后重新检查会话归属和存在性；因此等待 guard 期间
发生的会话删除不会被后续推理或重置重新写活。

`Disposable` 与以下 `SseEmitter` 生命周期绑定：

- `onCompletion`
- `onTimeout`
- `onError`

浏览器断开、SSE 写入失败、Agent 异常或正常完成都会关闭请求级 Agent 并释放 guard。只有实际收到
`RUN_ERROR` 的运行才跳过 assistant 展示消息和标题更新；best-effort 元数据投影失败不设置
`runFailed`。

当前 guard 只在单 JVM 内有效。多实例部署必须换成 Redis、数据库锁或其他带租约的分布式实现。

### 4.4 Compaction

本期继续使用 AgentScope `CompactionMiddleware`，不自建摘要算法。实际配置为：

```java
CompactionConfig.builder()
        .triggerMessages(0)
        .triggerTokens(maxTokensBeforeSummary)
        .keepMessages(messagesToKeep)
        .keepTokens(0)
        .flushBeforeCompact(false)
        .offloadBeforeCompact(false)
        .build();
```

并调用 `.disableToolResultEviction()`。语义是：

- 只按 token 阈值触发，避免把 token 数误当作消息数。
- 压缩后按消息条数保留 recent tail。
- 不把短期上下文 flush 为长期记忆。
- 不把原始上下文 offload 到尚未完成租户隔离的 workspace。

`summaryPresent` 通过 `AgentState.context` 中名为 `__compaction_summary__` 的消息判断。
AgentScope `2.0.0` 的流式路径不提供可靠的 context-overflow 自动重试，因此生产配置需要为模型
上下文窗口预留余量，并用真实长会话做阈值测试。

### 4.5 查询与重置 API

```http
GET    /api/chat/conversations/{conversationId}/context
DELETE /api/chat/conversations/{conversationId}/context
```

返回结构：

```json
{
  "conversationId": "...",
  "revision": 2,
  "state": "ACTIVE",
  "messageCount": 12,
  "summaryPresent": false,
  "triggerTokens": 4000,
  "lastRunTokenUsage": {
    "inputTokens": 1200,
    "outputTokens": 350,
    "cachedTokens": 0,
    "totalTokens": 1550
  },
  "resetAt": null,
  "updatedAt": "2026-07-31T02:00:00Z"
}
```

重置在获得 guard 后重新校验会话权限，然后删除当前 session 的 AgentScope 状态，再尝试写入新的
`context_meta` tombstone 并递增 revision。`chat_message` 不删除，所以用户仍能看到历史，但下一轮
模型不会再读取重置前的上下文。正常路径下重复重置保持幂等；与活跃 run 或 delete 冲突时返回
`409`。

删除 AgentScope session 是重置的权威结果。若删除失败，接口返回 `503`；若删除已经成功、但随后
tombstone 保存失败，服务端记录错误并仍返回成功，避免客户端重试已经完成的破坏性操作。成功响应
使用本次内存中的 reset metadata，但后续查询可能因为 tombstone 未持久化而缺少 revision、resetAt
和最近 token 等元数据。

删除业务会话同样在获得 guard 后重新校验权限，再同步删除整个 AgentScope session。若状态删除失败，
业务软删除事务回滚。

### 4.6 AG-UI 事件

成功运行且可观测性投影成功时，结束顺序固定为：

```text
CUSTOM token_usage
CUSTOM context_status
RUN_FINISHED
```

前端收到 `RUN_FINISHED` 后才转换为 AI SDK 的 `[DONE]`，因此两个状态事件不会被流结束截断。
若 `context_meta`、token usage 或 status 投影失败，服务端省略对应 `CUSTOM` 事件并仍发送
`RUN_FINISHED`。AgentScope 把真正的运行异常转换为 `RUN_ERROR + RUN_FINISHED + onComplete`，
服务端显式记录 `runFailed`，这类运行不会被误判为成功。

## 5. 前端实现

### 5.1 API 与运行时数据校验

- `src/api/context.ts` 实现 Context GET/DELETE、认证 header、网络异常和 HTTP/业务错误解析。
- 运行时校验 `ConversationContextStatus`、`TokenUsage`、枚举值、非负数、可选时间字段，并要求响应
  `conversationId` 与请求一致；不可信或结构错误的 payload 不进入 store。
- 错误统一归类为 `401/403/404/409/5xx/network/unknown`，同时保留 load/reset 操作类型，供 UI
  输出针对性的本地化状态。
- AG-UI `CUSTOM/token_usage` 与 `CUSTOM/context_status` 使用同一套 payload 校验；先到达的 token
  usage 会临时缓存，并在 status 到达时合并。

### 5.2 按会话状态、并发与陈旧响应保护

`src/stores/contextSlice.ts` 按 `conversationId` 隔离以下数据：

- status、error、loading、resetting。
- `runningByConversation` 引用计数，而不是单一布尔值；不同请求或流的开始、结束、取消和异常各自
  增减一次，避免一个流提前结束后错误解除另一个流的运行态。
- load/reset request id，用于丢弃旧请求结果；reset 会立即使此前发起的 GET 失效。
- pending token usage，用于兼容两个 `CUSTOM` 事件的到达顺序。

GET 和 SSE 更新都会比较 `revision` 与 `updatedAt`，较早的 GET 或旧流事件不能覆盖较新的 reset/SSE
状态。新会话在收到服务端 `conversation-id` 后会把运行计数从原会话归属迁移到真实会话 ID。

认证身份切换同样是状态边界：退出登录、token 清除、登录态失效或切换到不同用户时，会清空会话与
上下文 store；正在进行的聊天请求会按启动时 token 记录并在 token 变化时中止，旧身份流中的
`conversation-id` 和 `CUSTOM` 事件也会被忽略。删除会话成功后会同步清理该会话的前端上下文缓存。

### 5.3 完整上下文面板

`ChatHeader` 提供可直接使用的上下文面板：

- 本地化展示 `EMPTY/ACTIVE/COMPACTED`、保留消息数、压缩摘要、压缩 token 阈值、revision、
  updatedAt/resetAt，以及 input/output/cached/total token。
- 数字和日期按当前中英文语言使用 `Intl` 格式化；中英文 locale key 已对齐。
- 覆盖首次 loading、后台 refreshing、无数据、服务不可用、load/reset 错误和 retry 状态。
- `401/403/404/409/503/network` 使用针对性文案；reset 错误的重试会重新进入确认流程。
- reset 提供二次确认、进行中状态、成功 toast；推理运行期间按钮禁用并显示原因，后端仍以 guard
  和权限校验作为最终约束。
- 未登录或没有当前会话时入口禁用；压缩状态在入口显示非文本状态标记。

### 5.4 交互、无障碍与移动端

- 面板支持点击外部和 Escape 关闭，打开后获得焦点，关闭后将焦点返回触发按钮。
- 面板使用 `role="dialog"`、标题/描述关联、`aria-live`、`aria-busy`、expanded/controls 等状态。
- reset 确认框使用 `role="alertdialog"` 和 `aria-modal`，具备初始焦点、Tab/Shift+Tab 焦点循环、
  Escape、loading 锁定、描述关联和关闭后的焦点恢复。
- 桌面端面板锚定 Header；移动端使用 fixed/inset 定位、稳定宽度、动态视口最大高度和内部滚动，
  短屏幕不会与 Header 控件重叠或产生横向溢出。
- 亮色、暗色、中英文和窄屏布局使用同一套状态与交互逻辑。

本期没有把原始上下文展示给前端，也没有把前端状态作为授权或后端并发控制依据。历史消息映射
保留 `createdAt`，但历史展示仍以 `chat_message` 为准。

## 6. 请求时序

```mermaid
sequenceDiagram
    participant UI as React UI
    participant CTRL as ChatController
    participant API as ChatServiceImpl
    participant GUARD as SessionRunGuard
    participant BIZ as Conversation/Message DB
    participant AD as AuthenticatedAgentDelegate
    participant AS as HarnessAgent
    participant STORE as AgentStateStore

    UI->>CTRL: POST /api/chat
    CTRL->>API: handleBuilderMode(request)
    Note over CTRL,UI: Any pre-SSE ServiceException returns matching HTTP status + JSON
    API->>API: validate request + auth + model access
    alt new conversation
        API->>API: preallocate conversation UUID
    else existing conversation
        API->>BIZ: initial ownership check
    end
    API->>GUARD: acquire(userId, conversationId, runId)
    GUARD-->>API: lease
    API->>STORE: preflight agent_state + context_meta
    alt existing conversation
        API->>BIZ: revalidate ownership/existence while holding lease
        API->>API: validate bound model
        API->>AS: build Agent
    else new conversation
        API->>AS: build Agent
        API->>BIZ: persist conversation with preallocated UUID
    end
    API->>AD: bind trusted userId + conversationId
    API->>BIZ: save user message
    API-->>UI: conversation-id + AG-UI SSE
    AD->>AS: run(threadId, runId)
    AS->>STORE: load agent_state
    AS->>AS: compact at token threshold
    AS->>STORE: commit agent_state
    AS-->>API: RunFinished
    alt context projection succeeds
        API->>STORE: save context_meta + token usage
        API-->>UI: CUSTOM token_usage + context_status
    else projection fails after agent_state commit
        API->>API: log projection failure
        Note over API,UI: CUSTOM events may be omitted; run remains successful
    end
    API-->>UI: RUN_FINISHED
    API->>BIZ: save assistant message + update timeline/title
    API->>AD: close Agent
    API->>GUARD: release lease
```

`agent_state` 是恢复模型上下文的权威数据。`context_meta`、token usage 和前端状态事件是可观测性投影；
投影失败发生在 AgentScope 已提交状态之后，因此不能把已生效的 run 改写为失败，也不能阻止 assistant
消息和时间线保存。

## 7. 错误语义

| 场景 | 行为 |
| --- | --- |
| Chat 在 SSE 建立前校验失败 | `ChatController` 返回真实 HTTP 状态和 JSON 响应体，不以 `200 text/event-stream` 包装错误 |
| 未登录 | `401` |
| 会话不存在或不属于当前用户 | `403`，不泄露 AgentState 是否存在 |
| 模型不存在、禁用或不可见 | `404` |
| 消息/模型参数无效，或模型与会话绑定不一致 | `422` |
| 同会话已有 run、reset 或 delete | `409` |
| 请求等待 guard 后会话被删除或权限发生变化 | 持有 lease 后重新校验，失败则在 SSE 前返回 `403` |
| State Store 预读失败 | 同步 `503` |
| State Store 在流中失败 | `RUN_ERROR` 后结束，不以 fresh state 继续 |
| Agent 失败 | `RUN_ERROR + RUN_FINISHED`，不写成功投影，也不保存 assistant 成功时间线 |
| `agent_state` 已提交，但 `context_meta`/token 投影失败 | 记录错误日志，可省略两个 `CUSTOM` 事件，仍发送 `RUN_FINISHED` 并保存 assistant 时间线 |
| 浏览器断流或 SSE 写失败 | 取消订阅、关闭 Agent、释放 guard |
| reset 删除 AgentScope session 失败 | 返回 `503`，不报告重置成功 |
| reset 已删除 session，但 reset tombstone 保存失败 | 记录错误并返回成功，避免客户端重试已完成的破坏性操作；后续读取可能丢失 revision、resetAt 和 token 投影 |
| 重复 reset | 已有 tombstone 时幂等成功 |
| 登录 token 被清除或切换用户 | 前端清空会话/上下文状态并中止旧流，忽略旧身份的 `conversation-id` 和 `CUSTOM` 事件 |
| GET 或旧 SSE 事件晚于 reset/新流返回 | 前端用 requestId、revision 和 updatedAt 丢弃过期响应 |

AgentScope `2.0.0` 不保证取消时保存进行中的状态。本期承诺“失败或断流不会被记为成功”，不承诺
恢复未完成 run 的中间状态。

## 8. 部署要求与剩余边界

上线前需要完成：

1. 为 `agentscope_sessions` 提供显式 SQL migration；生产设置
   `app.conversation.agent-state.create-if-not-exist=false`。
2. 处理旧的匿名 AgentScope 状态。新实现不再读取 `userId=null` 的旧键；迁移或清理时必须先用
   `chat_conversation.user_id` 验证归属。
3. 使用真实 MySQL、真实模型做多轮恢复、长会话压缩、重启恢复和故障注入测试；故障注入应覆盖
   Store 预读、`agent_state` 保存、成功投影、reset 删除和 tombstone 保存。
4. 多副本部署前实现分布式 session guard，并验证 AgentStateStore 在跨节点并发写入、reset 和 delete
   竞争下的覆盖语义；当前本地 guard 只保证单 JVM 串行化。
5. AgentScope 升级时替换 deprecated stream 兼容入口，并重新评估、最终移除针对 `2.0.0` 的
   fail-closed 包装补丁。

本次没有扩展沙箱能力。`CopilotAgentFactory` 原有的本地 ROOTED filesystem 配置保持不变；后续接入
Docker/Kubernetes/AgentRun Sandbox 时，应继续复用本期的 `(userId, conversationId, runId)` 身份、
生命周期和审计边界。

## 9. 验证状态

已完成的最近一次构建和受控 UI 验证包括：

- Context 模块及依赖 Maven compile 通过。
- Context 模块及依赖 Maven test 生命周期通过；当前仓库没有对应自动化测试源码。
- `copilot-admin` 及全部依赖 Maven package 通过。
- React TypeScript `--noEmit` 通过。
- Vite production build 通过。
- `git diff --check` 通过。
- 浏览器覆盖中英文、亮色/暗色、桌面 `1440x900` 与移动端 `390x844`、`390x667`、`390x500`。
- 已检查面板溢出、外部点击、Escape/焦点恢复、确认框焦点循环、reset 成功、`409`、`503`，以及
  run 进行中禁用 reset。

浏览器验证期间，真实后端登录请求曾返回 `Unexpected end of JSON input`，因此上下文成功与错误 UI
路径使用受控响应完成验收，不能替代真实登录态端到端测试。当前也没有 Java 自动化测试，且尚未执行
依赖真实 MySQL、真实模型凭据、服务重启和故障注入的集成测试。最终代码合并后仍应复跑 Maven package、
TypeScript、Vite build 和 `git diff --check`；上述部署前集成测试是生产启用的必要门槛。

## 10. 官方依据

- [AgentScope Java 2.0 Context & AgentState](https://java.agentscope.io/v2/en/docs/building-blocks/context.html)
- [AgentScope Java 2.0 Context Compaction](https://java.agentscope.io/v2/en/docs/harness/compaction.html)
- [AgentScope Java 2.0 AG-UI](https://java.agentscope.io/v2/en/integration/protocol/agui.html)
- [AgentStateStore](https://java.agentscope.io/v2/en/integration/session/overview.html)
- [AgentScope Java repository](https://github.com/agentscope-ai/agentscope-java)
