# UI 重构后端契约与缺口

> 本文记录新 UI 的真实数据来源、临时 adapter 和后续替换点。mock 不允许直接写在 React 组件中。

## 1. 统一约定

### 1.1 响应 envelope

现有多数管理接口使用：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

新 gateway 先兼容当前存在的 raw array、`{ success, ... }` 和 `R<T>` 三种返回，由 adapter 归一化；新接口统一使用上面的 `R<T>`。

### 1.2 错误码

| HTTP | `code` | 含义 |
| --- | --- | --- |
| 400 | 400 | 参数或格式错误 |
| 401 | 401 | 未登录或 token 失效 |
| 403 | 403 | 无权限或能力被禁用 |
| 404 | 404 | 会话、文件、项目、分支等不存在 |
| 409 | 409 | 状态冲突，例如文件版本或分支冲突 |
| 422 | 422 | 参数合法但当前环境无法执行 |
| 500 | 500 | 服务内部错误 |

UI 不根据中文错误字符串判断类型；adapter 输出统一的 `AppError { code, message, retryable, details? }`。

## 2. 已确认存在的接口

| 能力 | 当前接口 | 前端来源 | 状态 |
| --- | --- | --- | --- |
| 登录/注册/当前用户/退出 | `/auth/login`、`/auth/register`、`/auth/me`、`/auth/logout` | `src/api/auth.ts` | 可复用 |
| 流式聊天 | `POST /api/chat`，SSE/AG-UI | `BaseChat` custom fetch | 可复用；跨 chunk UTF-8 与 EOF 尾帧已修复，仍待抽离 gateway/tests |
| 会话 CRUD | `/api/chat/conversations/**` | `src/api/conversation.ts` | 可复用 |
| 模型列表与管理 | `/api/model/**`、`/api/model-provider/**` | `src/api/models.ts` | 可复用，响应格式需 adapter |
| 文件读写 | `/api/files/**` | `src/api/filesystem.ts` | 可复用，操作种类有限 |
| MCP Server/Market | `/api/mcp/**` | `src/api/mcpServers.ts`、`mcpMarkets.ts` | 可复用 |
| 记忆 KV/Search | `/api/memory/**` | `src/api/memory.ts` | 可复用 |
| 工作区索引 | `/api/knowledge/index`、`/workspace-path` | `src/api/knowledge.ts` | 可复用 |
| 提示词优化 | `POST /api/enhancedPrompt` | 当前 ChatInput | 可复用 |
| 部署 | `POST /api/deploy`（当前仅 JSON 占位响应） | `HeaderActions.tsx` | `unavailable`：后端仍为 TODO，前端禁用入口并显示“待接入” |

### 2.1 当前聊天请求

后端 `ChatRequest` 已支持：

```json
{
  "message": { "role": "user", "content": "..." },
  "modelConfigId": "12",
  "conversationId": "uuid-or-null",
  "enablePreferences": true,
  "enablePreferenceLearning": true,
  "otherConfig": {},
  "tools": []
}
```

后端 AG-UI 已开启 `enableReasoning(true)` 与工具调用参数流。当前前端已消费文本、reasoning、`TOOL_CALL_START/ARGS/END/RESULT`、`RUN_ERROR` 和 `RUN_FINISHED`，并将 reasoning、工具与错误编码为消息时间线内容；解析仍位于 `BaseChat` 内部，尚未抽成独立 `ChatGateway`/event normalizer。STEP/STATE 事件当前未启用且只做前向兼容忽略，结构化时间线也尚未由历史接口持久化。

## 3. Adapter 规则

以下是目标边界，不表示这些 gateway 已全部从现有组件中抽离；实际落地状态见第 8 节。

```ts
type CapabilityMode = "real" | "local" | "unavailable";

interface AppCapabilities {
  git: CapabilityMode;
  hostTerminal: CapabilityMode;
  workspaceProjects: CapabilityMode;
  promptLibrary: CapabilityMode;
  knowledgeBases: CapabilityMode;
  reasoningControls: CapabilityMode;
  runCancellation: CapabilityMode;
}
```

- `real`：调用后端。
- `local`：使用 localStorage、IndexedDB 或 WebContainer 的真实本地实现。
- `unavailable`：显示说明和对接入口，不返回伪成功。
- `mock` 只允许在 fixture/test adapter 中启用，生产构建不注册。

### 3.1 Web 与 Electron 运行边界

- 流式聊天与 MCP 都通过同一后端契约工作；MCP 选择器不应仅因运行在 Web 端而隐藏。是否可用由模型 function-call 能力、MCP 服务状态和后端响应决定。
- `/api/chat` 受全局登录拦截器保护；游客可查看既有本地历史，但发送时前端必须打开登录界面，不能发起注定返回 401 的请求。
- 访客会话在 Web 端使用 IndexedDB；Electron 端当前使用其本地存储分支。这是本机数据，不代表账户跨端同步。本地会话支持重命名和删除。
- 预览页“外部打开”在 Electron 中通过 `ipcRenderer` 交给宿主，在 Web 中使用 `window.open(..., "noopener,noreferrer")`；Web 端不得直接调用 Electron IPC。
- 系统代理等宿主配置只在 Electron 能力存在时显示/调用；Web 端不伪造成功。当前终端在两种运行环境中都是 WebContainer 浏览器沙箱，不是主机终端。

## 4. P0：聊天与会话缺口

### 4.1 可配置执行模式与推理参数

建议向现有 `POST /api/chat` 追加可选字段，保持向后兼容：

```json
{
  "executionMode": "chat",
  "reasoningEffort": "high",
  "thinkingEnabled": true
}
```

- `executionMode`: `chat | builder`
- `reasoningEffort`: `low | medium | high | xhigh`
- 模型不支持时返回 `422`，`data.supportedValues` 给出可选项
- 后端未实现前：`reasoningControls = unavailable`，模型面板隐藏该区；Chat/Builder 继续使用当前前端模式
- 替换点：`services/chat/ChatGateway` 与 `features/chat/components/ModelPicker`

### 4.2 真正取消运行

当前 `stop()` 只能中断浏览器读取，服务端 Flux/Agent 可能继续执行。

建议接口：

```http
DELETE /api/chat/runs/{runId}
```

响应：

```json
{
  "code": 200,
  "msg": "cancelled",
  "data": { "runId": "...", "status": "cancelled" }
}
```

错误：`404` run 不存在，`409` 已完成，`403` 不属于当前用户。

后端未实现前：当前前端通过 `AbortController.abort()` 中断浏览器读取，并标记为“已停止接收”；尚未抽出独立取消 adapter，UI 不宣称服务端已取消。

### 4.3 部署能力

当前 `DeployController` 只接收 JSON `Map` 并返回占位 `deploymentId`，没有接收构建产物、托管文件或返回可访问 URL 的实现。因此前端不得发送 ZIP、显示“部署成功”或伪造站点地址。

当前替代：项目操作菜单保留禁用的“部署 · 待接入”入口，行为等同 `deployment = unavailable`；独立 `DeployGateway` 尚未实现。后端完成真实上传/发布契约后，再接入该 gateway。

### 4.4 结构化历史时间线

当前历史接口主要返回 `role/content`，无法恢复 reasoning、工具卡、附件与文件变更。

建议新增：

```http
GET /api/chat/conversations/{conversationId}/timeline?cursor=&size=50
```

```json
{
  "code": 200,
  "data": {
    "items": [
      {
        "messageId": "m1",
        "runId": "r1",
        "role": "assistant",
        "parts": [
          { "type": "text", "text": "完成" },
          {
            "type": "tool",
            "toolCallId": "t1",
            "name": "write_file",
            "args": { "path": "src/App.tsx" },
            "result": { "success": true },
            "status": "done"
          }
        ],
        "createdAt": "2026-07-26T12:00:00+08:00"
      }
    ],
    "nextCursor": null
  }
}
```

后端未实现前：实时会话在内存中保留结构化 part；重新加载后退化为文本历史，adapter 设置 `historyDetailLevel = text-only`。

### 4.5 会话搜索、置顶、归档

建议扩展列表：

```http
GET /api/chat/conversations?q=&pinned=&archived=&cursor=&size=30
PATCH /api/chat/conversations/{conversationId}
```

PATCH body：

```json
{
  "title": "新的标题",
  "pinned": true,
  "archived": false
}
```

当前替代：重命名继续调用现有 title 接口；前端仅对已加载项本地搜索；置顶/归档按钮由 capability 隐藏，不伪造跨端持久化。

## 5. P0：文件、变更与 Git

### 5.1 文件树操作

当前已有获取全部文件、读取、单文件保存和批量保存，但缺少目录元数据、创建目录、移动、重命名和删除。

建议接口：

```http
GET    /api/workspaces/{workspaceId}/tree?path=&depth=2
POST   /api/workspaces/{workspaceId}/entries
PATCH  /api/workspaces/{workspaceId}/entries
DELETE /api/workspaces/{workspaceId}/entries?path=
```

创建：

```json
{ "type": "file", "path": "src/new.ts", "content": "" }
```

移动/重命名：

```json
{ "from": "src/old.ts", "to": "src/new.ts", "expectedVersion": "etag" }
```

错误：`404` 路径不存在，`409` 目标存在/版本冲突，`422` 越界或非法路径。

当前替代：WebContainer workspace 使用 local adapter 完成；服务端工作区仅开放现有能力，缺失菜单显示 disabled 原因。

### 5.2 Git Gateway

建议接口：

```http
GET  /api/git/status?workspaceId=
GET  /api/git/branches?workspaceId=
GET  /api/git/diff?workspaceId=&path=&staged=false
POST /api/git/actions
```

`POST /actions`：

```json
{
  "workspaceId": "default",
  "action": "checkout",
  "args": { "branch": "feature/ui" }
}
```

允许动作建议白名单：`fetch | pull | push | checkout | create-branch | stage | unstage | discard`。`discard` 必须带二次确认 token，禁止前端拼接任意 shell 命令。

响应至少包含 `head`、`branches`、`files[{ path, status, staged, additions, deletions }]`。错误：`409` 工作树冲突，`422` 非 Git 仓库，`403` 写操作禁用。

当前替代：`Changes` Tab 基于 `oldFiles/files` 展示前端 diff；Workbench 尚未提供 Git Tab，也没有真实 status/branch/stage/push 等能力。后续接入 `GitGateway` 前不得把前端文件快照描述成 Git 状态。

### 5.3 主机终端（可选）

当前终端是 WebContainer 沙箱，不等同于 ArcForge 的本机终端。若未来需要主机终端：

```http
POST   /api/terminal/sessions
DELETE /api/terminal/sessions/{id}
WS     /api/terminal/sessions/{id}/stream
```

必须有 cwd 白名单、命令权限策略、审计与会话所有权校验。未实现前 UI 明确显示“浏览器沙箱终端”。

## 6. P1：当前前端已有 UI、后端不完整的能力

### 6.1 提示词库

当前 `src/api/prompts.ts` 含模拟数据，但后端没有对应 CRUD Controller，仅有提示词优化接口。

建议：

```http
GET    /api/prompts?category=&q=&page=&size=
GET    /api/prompts/categories
POST   /api/prompts
PUT    /api/prompts/{id}
DELETE /api/prompts/{id}
```

对象：

```json
{
  "id": "p1",
  "name": "接口设计",
  "category": "development",
  "template": "请为 {{resource}} 设计 API",
  "variables": [{ "name": "resource", "type": "text", "required": true }],
  "updatedAt": "2026-07-26T12:00:00+08:00"
}
```

当前实际：开发模式仍使用进程内 mock 数组，生产路径则请求尚无后端实现的 `/api/prompts`，因此提示词 CRUD 不能记为生产可用。后续应二选一：实现真正的 `PromptGateway.local`（IndexedDB/localStorage，并明确标注“仅本机”），或在后端接口完成前将 capability 设为 `unavailable`；不得把开发 mock 带入生产语义。

### 6.2 知识库管理

当前前端调用 `/api/rag/knowledge-bases/**` 并在开发模式使用 mock；后端目前只确认存在工作区索引与路径接口。

建议保持前端现有路径契约：

```http
GET/POST /api/rag/knowledge-bases
DELETE   /api/rag/knowledge-bases/{kbKey}
GET      /api/rag/knowledge-bases/{kbKey}/documents
POST     /api/rag/knowledge-bases/{kbKey}/upload
DELETE   /api/rag/knowledge-bases/{kbKey}/documents/{documentId}
GET      /api/rag/knowledge-bases/{kbKey}/documents/{documentId}/chunks
POST     /api/rag/knowledge-bases/{kbKey}/search
```

错误需区分：`413` 文件过大、`415` 格式不支持、`422` 分块/嵌入失败、`503` 向量库不可用。

当前实际：视觉开发使用 mock 数据，生产路径直接请求 `/api/rag/**`；独立 capability adapter 与不可用态尚未完整落地，因此知识库管理不能记为后端可用。目标行为是：生产若 capability 不存在，只显示真实可用的工作区索引入口，不加载假知识库。

### 6.3 找回密码

前端请求 `/auth/reset/password`，但当前 `AuthController` 未确认该映射。

建议：

```http
POST /auth/reset/password
```

```json
{ "account": "user@example.com", "verificationCode": "123456", "newPassword": "..." }
```

未实现前登录 Dialog 隐藏找回密码入口或显示明确说明，不能展示一个必然失败的表单。

### 6.4 设置跨端同步（可选）

主题、语言、Dock 宽度和大部分通用设置当前为 localStorage。若需要账户同步：

```http
GET /api/user/preferences
PUT /api/user/preferences
```

写入需支持 `version`/ETag，冲突返回 `409`。未实现前保存指示器标注“已保存到本机”。

## 7. P2：ArcForge 式多项目

第一阶段只映射当前工作区，不阻塞 UI 重构。未来接口：

```http
GET    /api/workspaces
POST   /api/workspaces
PATCH  /api/workspaces/{id}
DELETE /api/workspaces/{id}
```

```json
{
  "id": "ws1",
  "name": "copilot",
  "path": "F:/copilot",
  "pinned": true,
  "archived": false,
  "lastActiveAt": "2026-07-26T12:00:00+08:00"
}
```

Web 端必须由后端授予可访问 workspace ID，不能接受任意绝对路径作为权限依据。

## 8. 前端替换点总表

| Gateway | 初始实现 | 后端到位后的替换范围 |
| --- | --- | --- |
| `ChatGateway` | 尚未独立抽出；`BaseChat` 内直接调用 `/api/chat` 并解析 AG-UI | 先完成 gateway/controller 抽离，再增加 cancel、reasoning 字段 |
| `ConversationGateway` | 尚未独立抽出；现有 API/store 提供 CRUD | 增加 timeline/search/pin/archive，不改列表视图 |
| `WorkspaceGateway` | 现有 API + WebContainer local | 换完整 entry API，不改 FilesTool |
| `GitGateway` | 尚未实现；当前仅有 `Changes` 文件快照 diff | 新增 unavailable/real adapter 与 Git UI |
| `TerminalGateway` | WebContainer local | 可选主机终端 adapter |
| `PromptGateway` | 尚未实现；开发为进程内 mock，生产接口缺失 | 实现 local adapter 或切 `/api/prompts` |
| `KnowledgeGateway` | 尚未完整实现；开发 mock，生产接口未确认 | 建立 capability/unavailable 后再切 `/api/rag/**` |
| `SettingsGateway` | localStorage | 可选账户同步 |
| `DeployGateway` | 尚未实现；当前入口禁用 | 后端提供构建产物上传、状态查询和站点 URL 后新增 real adapter |

## 9. 文档维护规则

每接入一个真实接口，必须同时更新：

1. capability 默认值；
2. adapter 实现；
3. 本文“当前替代”状态；
4. 请求/响应 fixture；
5. 错误态测试。

禁止因为接口尚未完成而在页面组件中写临时数组、随机延迟或永远成功的 Promise。
