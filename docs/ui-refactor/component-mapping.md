# UI 组件迁移映射

> 目标：说明每项能力是复制、改造、重写还是保留，避免在实施中混入第二套设计体系。

## 1. 总体映射

| 新模块 | 当前项目来源 | ArcForge 参考 | 策略 | 后端情况 |
| --- | --- | --- | --- | --- |
| `AppShell` | `src/App.tsx`、`components/Header/**` | `src/App.tsx`、`pages/ChatPage.tsx` | 重写；删除居中品牌 Header | 无新增接口 |
| `NavigationSidebar` | `components/Sidebar`、`ConversationList`、`Header/ProjectTitle` | `components/chat/ChatHistorySidebar.tsx` | 复制布局、接入现有会话 store | 会话 CRUD 已有；pin/archive/search 缺失 |
| `ChatHeader` | `components/Header` | `pages/chat/components/ChatHeader.tsx` | 改造；只留布局动作 | 无新增接口 |
| `ChatTranscript` | `AiChat/chat/index.tsx` 的 `showJsx` | `pages/chat/transcript/**` | 重写展示，保留 controller 数据 | AG-UI 已有，前端解析不完整 |
| `UserMessage` | `MessageItem` | `UserMessageRow.tsx` | 复制视觉结构，适配现有 Message | 历史仅 role/content |
| `AssistantMessage` | `MessageItem`、`ArtifactView` | `AssistantBubble.tsx`、`assistant-bubble/**` | 重写为 part renderer | 工具/reasoning 历史需补齐 |
| `ChatComposer` | `ChatInput/**` | `ChatComposerBar.tsx` | 复制外观与工具栏结构，复用现有输入逻辑 | 发送接口已有 |
| `MentionEditor` | `ChatInput` 内 textarea mention | `MentionComposer.tsx` | 精简重写，不整文件复制 | 文件列表已有 |
| `ModelPicker` | `UploadButtons`、`chatSlice` | `ChatModelPicker.tsx` | 改造 | 模型列表已有；推理参数缺失 |
| `ComposerAddMenu` | 上传、MCP、RAG、Prompt、Memory 现有入口 | `ChatComposerBar.tsx` | 重组现有能力 | 大部分已有，详见接口文档 |
| `WorkbenchDock` | `EditorPreviewTabs.tsx`、`WeIde` | `project-tools/RightDockPanel.tsx` | 复制 Dock 外壳，内容接现有能力 | 无统一项目工具接口 |
| `FilesTool` | `WeIde/.../FileExplorer` | `project-tools/file-tree/**` | 保留数据逻辑，重写视图 | 读写已有；删除/移动缺失 |
| `EditorTool` | `WeIde/components/Editor/**` | `workspace-editor/**` | 保留 CodeMirror，重写 Tab/Toolbar 外壳 | 前端/WebContainer |
| `PreviewTool` | `PreviewIframe.tsx` | Workspace preview overlay | 保留能力、重写容器 | 前端/WebContainer |
| `ApiTool` | `WeAPI/**` | 无直接对应 | 按新 token 重写 | 当前为前端 HTTP 客户端 |
| `TerminalTool` | `WeIde/components/Terminal/**` | `XTermViewport.tsx` | 保留 xterm/WebContainer，改外壳 | 主机终端后端缺失 |
| `ChangesTool` | `oldFiles/files`、Editor diff | `git-review/**` | 先实现生成文件变更；Git 接 adapter | Git 后端缺失 |
| `SettingsPage` | `components/Settings/**` | `pages/SettingsPage.tsx` | 复制页面壳，重写全部可见表单 | 混合：部分真实、部分 mock |
| `AuthDialog` | `components/Login/**` | `settings-modal` 动效 | 保留流程、重写视觉 | login/register/me 有；reset 缺失 |
| `Toast/Dialog` | react-toastify + antd message/modal | `NotifyToast`、UI primitives | 统一重写 | 无新增接口 |
| `ThemeSystem` | 两份 global CSS + `themeSlice` | `src/index.css` token | 合并为单一 token 源 | 本地设置 |

## 2. 业务逻辑保留清单

以下代码不是旧 UI，原则上保留并通过 adapter/controller 接入：

- `src/api/**`
- `src/stores/**`
- `src/hooks/**`
- `src/types/**`
- `src/components/AiChat/sse*`、`useSseMessageParser.tsx` 中仍有效的协议与文件事件逻辑
- `src/components/WeIde/services/**`
- `src/components/WeIde/stores/**`
- CodeMirror 配置、语言支持、历史、diff 和快捷键
- WebContainer 文件系统、服务器和终端服务
- Markdown、图片、文件类型与安全 JSON 工具

保留不代表原封不动：`BaseChat` 中的传输、副作用和渲染会被拆到独立层，但不会丢失功能。

## 3. 可见旧 UI 替换清单

下列模块在新入口稳定后删除或彻底失去引用，不保留运行时切换：

- `src/components/Header/**`
- `src/components/Sidebar/index.tsx`
- `src/components/ConversationList/index.tsx`
- `src/components/AiChat/index.tsx` 的旧容器外观
- `src/components/AiChat/chat/components/ChatInput/**` 的旧展示层
- `src/components/AiChat/chat/components/MessageItem/index.tsx` 的旧展示层
- `src/components/EditorPreviewTabs.tsx` 的旧顶层 Tab 外观
- `src/components/Settings/index.tsx` 的旧弹窗壳
- `src/components/Login/**` 的旧外观
- 当前根 `global.css` 与 `src/global.css` 的重复/冲突规则

实施时先迁移调用方，再删除文件；不使用 `git reset` 或覆盖用户已有改动。

## 4. UI primitives 策略

### 直接借鉴 ArcForge class 与结构

- Button：ghost、outline、destructive、icon、small
- Input/Textarea/Label
- Dialog/ConfirmDialog
- Dropdown/Popover/Select
- ScrollArea/Transient scrollbar
- Toast/Loading/Empty/Error state

### 运行时实现

ArcForge 的 primitives 基于 `@base-ui/react`，其项目同时使用 React 19。当前项目保持 React 18，因此：

1. 优先使用 React 18 兼容的 Radix primitives。
2. 复制 ArcForge 的视觉 class 和 motion，不直接复制 Base UI 特有 API。
3. 仅在没有可访问性 primitives 的简单控件中使用原生 HTML。
4. `antd` 可以在迁移期间作为旧代码依赖存在，但最终可见新 UI 不允许渲染 Ant Design 组件。

## 5. Chat 拆分方案

当前 `BaseChat` 同时负责模型加载、会话水合、IndexedDB、SSE 转换、AG-UI 事件、文件副作用、滚动、上传、拖拽和渲染。拆分后：

| 新单元 | 职责 |
| --- | --- |
| `chatTransport.ts` | 请求 `/api/chat`，按 SSE frame 读取 AG-UI 事件 |
| `aguiEventNormalizer.ts` | 将事件转为 `UiMessagePart`，保留 run/tool/message ID |
| `chatRunController.ts` | 发送、停止、状态机、重试、会话 ID 回写 |
| `conversationController.ts` | 新建、切换、历史水合、标题、删除 |
| `fileEffectController.ts` | write/edit/delete 工具对文件 store 的副作用 |
| `useChatScroll.ts` | 自动跟随、用户滚动、跳到底部 |
| `ChatTranscript.tsx` | 纯时间线容器 |
| `MessagePartRenderer.tsx` | text/reasoning/tool/file/error/attachment 分发 |
| `ChatComposer.tsx` | 输入、附件、mention、菜单、模型、发送动作 |

建议的最小 `UiMessagePart`：

```ts
type UiMessagePart =
  | { type: "text"; text: string }
  | { type: "reasoning"; text: string; state: "streaming" | "done" }
  | { type: "tool"; toolCallId: string; name: string; argsText: string; result?: unknown; state: "args" | "running" | "done" | "error" }
  | { type: "file-change"; path: string; operation: "write" | "edit" | "delete"; state: "running" | "done" | "error" }
  | { type: "attachment"; id: string; name: string; mimeType: string; url: string }
  | { type: "error"; code?: string; message: string };
```

## 6. Workbench 接入方式

`WorkbenchDock` 只负责 Tab、宽度、折叠和生命周期；每个工具通过 registry 注册：

```ts
interface WorkbenchToolDefinition {
  id: "files" | "editor" | "preview" | "api" | "terminal" | "changes" | "git";
  title: string;
  isAvailable(capabilities: AppCapabilities): boolean;
  render(): React.ReactNode;
}
```

这样可以在 Git 或主机终端后端尚未完成时，保留真实的能力检测与空状态，不在组件里散落条件判断。

## 7. 依赖清理目标

重构完成后使用 `rg` 校验并按实际引用删除：

- `antd`
- `antd-style`
- `styled-components`
- `react-toastify`
- 重复图标库（保留 `lucide-react` 为主）

CodeMirror、xterm、WebContainer、Markdown、diff、i18n、Zustand 等业务依赖继续保留。是否删除依赖以“全仓无引用 + build 通过”为前提，不做机械删除。

## 8. 品牌与资产

- 保留产品名 Alibaba Copilot。
- 不复制 ArcForge 的 logo、Tauri 图标或产品文案。
- 不默认复制 OpenAI Sans 字体文件；使用系统字体栈。
- 若复制 MIT 源码的实质部分，在三方许可文件中记录 ArcForge 来源及 MIT 文本。
