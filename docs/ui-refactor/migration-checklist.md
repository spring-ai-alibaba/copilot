# UI 重构实施清单

> 原则：开发中可以先搭建新目录，但运行时最终一次切换到新 AppShell；不保留旧版入口或双 UI 功能开关。

> 状态摘要（2026-07-26）：运行入口已切换到新 AppShell，侧栏、聊天画布、Workbench Dock、全屏设置与登录外壳已落地；TypeScript 检查和 Vite production build 已通过，亮/暗主题已做首轮浏览器视觉检查。下列勾选只表示当前工作区有直接证据证明完成，不代表所在 Phase 已整体验收。
>
> 明确剩余：AG-UI/会话/文件副作用仍集中在 `BaseChat`，尚未形成可独立测试的 controller/gateway；模型搜索/供应商分组、Git、真实部署和服务端取消未完成；设置内容仍大量使用 Ant Design，后端地址/代理、账户/关于和统一反馈体系未完成，提示词与知识库仍存在开发 mock/生产接口缺口；自动化测试、关键无障碍测试、完整手工回归与 200% 缩放 QA 尚未完成；旧 UI 文件、硬编码样式和旧依赖仍需清理。

## Phase 0：基线与保护

- [x] 记录当前 `git status`，保留用户已有改动
- [x] 建立关键能力清单：登录、会话、聊天、SSE、文件、IDE、终端、模型、MCP、知识、记忆、提示词、部署
- [ ] 为 AG-UI event normalizer、会话 gateway、文件副作用增加最小测试
- [x] 建立 `1280×720`、`1440×900`、`1920×1080` 视觉基线
- [x] 确认 ArcForge MIT attribution 的落点

退出条件：能够判断 UI 改动是否破坏业务，不依赖人工记忆回归。

## Phase 1：Token 与 UI primitives

- [ ] 合并根 `global.css` 与 `src/global.css`
- [x] 引入 ArcForge 风格 light/dark 语义 token
- [ ] 建立 Button、IconButton、Input、Textarea、Dialog、Dropdown、Popover、Select、Toast、Tooltip（当前只有部分本地 primitives）
- [ ] 建立统一 focus ring、disabled、loading、danger 状态
- [x] 加入 `prefers-reduced-motion`
- [x] 使用系统字体栈，不复制产品品牌字体

退出条件：primitives 在亮/暗主题和键盘操作下通过组件级验收。

## Phase 2：应用壳与导航

- [x] 新建 `AppShell`
- [x] 实现 272px 可折叠左侧导航
- [x] 接入新建/选择/重命名/删除/加载更多会话（远端分页与本地会话重命名均已接入）
- [x] 映射登录与未登录的后端/IndexedDB 数据源
- [x] 实现当前工作区区块
- [x] 实现极简 Chat Header
- [x] 加入设置、账户、主题和 Dock 开关

退出条件：不进入旧 Sidebar/Header 也能完成全部会话导航动作。

## Phase 3：Chat controller 与 AG-UI

- [ ] 从 `BaseChat` 抽出 `ChatGateway`
- [ ] 抽出 SSE frame parser（跨 chunk UTF-8、EOF 尾帧已修复，但 parser 仍在 `BaseChat` 内）
- [x] 标准化 text、reasoning、tool start/args/end/result、run error/finished
- [x] 让每个 toolCallId 有稳定状态机
- [ ] 抽出文件 write/edit/delete 副作用
- [ ] 抽出会话创建、ID 回写和历史水合（ID 回写与历史水合已有，尚未从 `BaseChat` 抽离）
- [x] 区分“客户端停止接收”和“服务端已取消”
- [x] 保留登录用户后端历史与未登录 IndexedDB 历史

退出条件：无 UI 时 controller 测试可验证一轮完整 AG-UI 会话。

## Phase 4：新聊天展示与 Composer

- [ ] 空状态/无模型状态（空状态已实现；无模型状态仍需去除默认模型回退后验证）
- [x] 用户气泡、助手消息、Markdown、代码块、图片
- [x] reasoning 折叠区
- [x] 工具调用与工具结果卡
- [x] 文件变更与错误卡
- [x] hover/focus 消息动作
- [x] Composer 24px 玻璃卡片
- [x] 添加菜单、附件条、mention 菜单
- [ ] 模型搜索与供应商分组
- [x] Chat/Builder 切换
- [x] 发送/停止状态
- [x] 全高展开与动态底部预留
- [ ] 粘贴、拖拽、IME、Enter/Shift+Enter、ESC 验证

退出条件：聊天主路径完全使用新 UI，旧 ChatInput/MessageItem 无运行引用。

## Phase 5：右侧 Workbench Dock

- [x] Dock 折叠/展开、宽度拖拽和本地持久化
- [ ] Tab Strip、关闭、恢复和 launcher（Tab 与最近选中项恢复已有，关闭/完整 launcher 未完成）
- [x] FilesTool 接入现有 file store
- [x] EditorTool 接入 CodeMirror 和未保存状态
- [x] PreviewTool 接入 PreviewIframe
- [x] ApiTool 接入 WeAPI
- [x] TerminalTool 接入 WebContainer/xterm
- [x] ChangesTool 接入 oldFiles/files diff
- [ ] GitTool 接入 capability/unavailable/fixture/real adapter
- [x] 小屏覆盖层行为

退出条件：Builder 模式的现有开发能力全部能从新 Dock 进入。

## Phase 6：设置与认证

本阶段勾选的功能只表示设置入口和现有界面已接入全屏外壳，不等于相应后端契约或本地 adapter 已完成。

- [x] 全屏 SettingsPage 壳
- [x] 外观/语言
- [x] 模型管理
- [x] 提示词库
- [x] 记忆
- [x] 知识库
- [x] MCP Server/Market
- [ ] 后端地址/代理
- [ ] 账户/关于
- [ ] 本机/远端保存状态提示
- [ ] 登录、注册、找回密码 capability（登录/注册外壳已更新；找回密码后端契约未确认）
- [ ] 统一确认框和 Toast（本地 ConfirmDialog/ToastContainer 已接入，但设置页仍混用 Ant Design message/Popconfirm）

退出条件：所有设置均使用新 primitives，不再渲染 Ant Design 外观。

## Phase 7：单次切换与旧 UI 删除

- [x] `src/App.tsx` 只挂载新 AppShell
- [ ] 删除旧 Header/Sidebar/ChatInput/MessageItem/Settings/Login 的全部引用
- [ ] 删除旧硬编码颜色和重复 CSS
- [ ] `rg` 检查 `#18181a`、`#333333`、旧紫色 gradient 等残留
- [ ] `rg` 检查 `antd`、`antd-style`、`styled-components`、`react-toastify` 引用
- [ ] 无引用且构建通过后删除对应依赖
- [x] 不保留 `legacy` 路由、feature flag 或版本切换按钮

退出条件：生产包中只有一套可见设计体系。

## Phase 8：验证与交付

### 自动验证

- [x] TypeScript
- [x] Vite production build
- [ ] AG-UI parser/controller tests
- [ ] Conversation/Workspace/Git adapter tests
- [ ] 设置表单与 capability tests
- [ ] 关键可访问性测试

### 手工回归

- [ ] 未登录进入、登录、注册、退出
- [ ] 新建/切换/重命名/删除会话
- [ ] 文本发送、停止、错误、重试
- [ ] 图片/附件、文件 mention、MCP、知识、记忆
- [ ] Chat/Builder 与模型切换
- [ ] 文件浏览、编辑保存、预览、API、终端、diff
- [ ] 亮/暗主题、语言、刷新后状态恢复
- [ ] 网络断开、401、500、空列表、无模型、后端 capability 缺失

### 视觉 QA

- [ ] Composer closed/add/model/Git 状态与 ArcForge 截图逐项对照
- [x] Sidebar、Settings、Dock 在三种基准尺寸截图
- [x] 亮/暗主题对照
- [ ] 200% 缩放和长中文/英文标题
- [x] 无横向滚动、菜单不被裁剪、最后一条消息不被 Composer 遮挡（基准尺寸及 `390×844` 已检查）

退出条件：自动验证通过、关键路径回归通过、无 P0/P1 视觉缺陷。

## 回滚策略

UI 切换前建立普通 Git commit 作为可恢复点；出现问题通过 revert 提交恢复，不在仓库中长期保留两套运行入口。数据库/API 契约保持向后兼容，UI 回滚不应要求数据回滚。

## 完成定义

- [ ] `design-plan.md` 验收标准全部满足
- [ ] `backend-contracts.md` 中所有临时 adapter 均标注 `real/local/unavailable`
- [ ] 生产构建无 fixture/mock 注册
- [ ] 旧 UI 文件已删除或无引用，并有明确后续清理记录
- [ ] 变更说明包含截图、测试结果、已知后端缺口和下一步
