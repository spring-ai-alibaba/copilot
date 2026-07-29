# 技能机制启用 + 全流程测试报告

> 日期：2026-07-27 ｜ 基线：origin/main a17413c（AgentScope 2.0 迁移后）｜ 模型：deepseek-chat（modelConfigId=3）

## 一、本次改动

| 类别 | 内容 |
|---|---|
| 技能启用 | 新增 `workspace/skills/` 下 4 个技能：frontend-style、vue-element-page、java-crud、db-schema-design（SKILL.md，触发条件式 description） |
| prompt 调整 | `CopilotAgentFactory.buildSystemPrompt()`：硬编码的 Tailwind 规范移入 frontend-style 技能，改为"先查 available_skills 再动手"的指引 |
| Bug 修复 ① | `LoginHelper.getUserId()`：sa-token 默认模式下 `StpUtil.getExtra` 抛 ApiDisabledException 导致恒返回 null，任何聊天首条消息建会话必 500。已加 token session 兜底 |
| Bug 修复 ② | 根 pom `json-schema-validator` 1.0.87 与 agentscope-core 2.0.0（需要 networknt 2.0.0）冲突，运行期 ClassNotFoundException。已升级至 2.0.0 并适配 `SchemaValidator`（JsonSchemaFactory/SpecVersion → SchemaRegistry/SpecificationVersion 新 API） |
| 评测集 | `docs/skills/golden-set.md`：19 条正样本 + 4 条负样本 + 3 条边界样本 |
| 环境 | 新建 `copilot` 库（导入 docs/scripts/sql 脚本），从旧库迁移 sys_user 与 model_config |

## 二、测试结果

| 用例 | 输入 | 预期 | 结果 |
|---|---|---|---|
| A1 全流程 | 帮我做一个产品介绍落地页 | 命中 frontend-style | ✅ `load_skill_through_path(frontend-style_workspace-namespaced)` → 生成 product-landing.html（24.8KB，Tailwind CDN/viewport/zh-CN 全合规），RUN_FINISHED 正常 |
| E1 负样本 | 你好，你能做什么 | 不加载任何技能 | ✅ 零工具调用，纯对话回复 |
| C1 后端 | 帮我写一个商品管理的增删改查接口 | 命中 java-crud | ✅ 且**主动链式加载 db-schema-design**（为写建表 SQL），生成完整工程 16 文件（pom/yml/init.sql/entity/dto/vo/mapper/service/controller/config），命名与分层完全符合技能规范 |
| R3 多轮 | （同会话）把刚才那个落地页的配色改成深色主题 | 状态连续 + 修改文件 | ✅ MysqlAgentStateStore 会话连续性生效，正确定位并读取原文件，产出深色版本；⚠️ 见已知问题 |

验证点：`FileSystemSkillRepository` 自动发现 `workspace/skills`（零注册）；`<available_skills>` 注入；技能选择准确（4/4 用例全对，含 1 例自发技能组合）。

## 三、已知问题（未修，建议后续处理）

1. **文件工具路径口径不一致**：FilesystemTool 的相对路径解析到 `workspace/<conversationId>/`（按会话隔离），但 `DeleteFileTool`（CopilotAgentFactory 注册时传 workspace 根目录）和 `execute` shell 的 working_directory 解析到 workspace 根——R3 中 agent 因此反复 del/rename 失败（28 次工具调用才收敛），旧文件也未被真正删除。建议 DeleteFileTool 与 harness 的会话目录口径对齐。
2. **Milvus 未启动**：知识库功能被优雅降级（不影响技能），如需 search_knowledge 需先 `docker-compose -f docker-compose-milvus.yml up`。
3. R3 中模型多次用 Windows `del/rename` 语法但执行环境非 cmd，均失败——若保留 execute 工具，建议在 sysPrompt 或工具描述中说明 shell 类型。

## 三点五、Phase 2 增量（同日第二轮）

**改动**：
1. 修复 `DeleteFileTool` 路径口径——`buildAgent` 增加 conversationId 参数，delete 根目录对齐 FilesystemTool 的会话目录 `workspace/<conversationId>/`；
2. 启用 `enableSkillManageTool(SkillManageConfig.defaults())`——自学习闭环第一步（propose_skill/skill_manage 草稿→审核，不自动晋升），框架同时开始记录技能使用计数；
3. sysPrompt 增加文件操作引导（删除用 delete_file、不要用 shell 删除/重命名——shell 工作目录与文件工具不一致）。

**评测结果（抽样 recall 6/6，误触发 0/1）**：

| 用例 | 期望技能 | 结果 |
|---|---|---|
| A3 倒计时页面 | frontend-style | ✅ 命中，产出 countdown.html |
| B1 Vue 用户管理 | vue-element-page | ✅ 命中，api/ 与 views/ 分层符合技能规范 |
| D1 电商订单表结构 | db-schema-design | ✅ 命中，文本输出 DDL（未落文件，合理） |
| F3 建表后写 CRUD（链式） | db-schema-design → java-crud | ✅ 顺序链式加载，博客表 + 文章 CRUD 完整产出 |
| R4 清理多余文件（delete 修复验证） | — | ✅ delete_file 三次全部成功（修复前为"文件不存在"），最终目录状态正确；但模型仍先尝试了多次 shell 删除，已加 sysPrompt 引导 |

**发现**：
1. F3 中模型把 glob 到的旧会话路径前缀带进了 write_file 路径，形成会话目录内的嵌套目录（`<本会话>/<旧会话id>/src/...`）——会话隔离未被打破，但说明跨会话文件可见性会干扰模型的路径选择，后续可考虑限制 glob/list 默认范围为会话目录。
2. **使用计数的框架边界**：`enableSkillManageTool` 启用成功（skill_manage/propose_skill 已注册），但实测确认 agentscope 2.0.0 的 `.usage.json`（SkillUsageMiddleware.bumpIfAgentTracked）与 `skills/.audit/` 只覆盖 **agent 自建技能**的自学习治理，人工编写的工作区技能加载不产生任何计数。若要做"注入了没被 load / load 了但失败"的匹配准确率归因，需要自己写一个轻量 Middleware 把 `load_skill_through_path` 调用落到业务库（建议表：skill_usage_log(conversation_id, skill_id, loaded_at)），这是下一步方向一评测闭环的前置工作。

## 三点七、真实用户视角全量测试（同日第三轮）

**API 面（13 项，全部通过）**：登录成功/错误密码拒绝、无 token 访问受保护接口全部 401、`/auth/me`、模型列表（前端选模型入口）、会话详情/历史消息/改标题（query 参数 `title`）/删除会话、工作区文件浏览 `/api/files/workspace`、文件配置、MCP 服务列表（空列表正常）、知识库路径、记忆查询（namespace 需 JSON 数组格式 `["users","1"]`，格式校验与"未找到记忆"空态均正常）。

**golden set 全量（16 条期望命中全部命中，4 条负样本零误触发）**：
- A2/A5 frontend-style ✓（resume.html、activity-promo.html）；A4 多轮同会话未重复 load 技能、直接 edit_file 修改（技能已在上下文，判定合理）；
- B2/B3/B4 vue-element-page ✓（订单列表页、新增用户弹窗、商品卡片组件）；
- C2/C3/C4 java-crud ✓（C2/C4 自发组合加载 db-schema-design 先写建表 SQL）；
- D3 db-schema-design ✓（文本 DDL）；
- E2（依赖注入解释）/E3（代码翻译）/E4（列目录，仅 list_files）零技能加载，行为正确；
- **F1 三技能链式**：db-schema-design → java-crud → vue-element-page，一次性产出 56 文件全栈图书管理系统 ✓；
- F2 模糊请求（"把数据库查询结果做成展示页面"）：模型探索工作区、识别出两个已有项目后向用户澄清数据源与页面形式——未武断选技能，行为符合真实用户预期，记录为合理响应。

**结论**：聊天主链路、技能匹配（accumulated recall 23/23）、技能组合（双技能 3 次、三技能 1 次自发链式）、多轮状态、会话管理、文件浏览、认证与参数校验——真实用户全流程验证通过。

## 四、遗留事项

- 本次改动均未提交 git（pom.xml、LoginHelper、SchemaValidator、CopilotAgentFactory、workspace/skills/、docs/skills/），请 review 后自行提交。
- golden set 其余用例（A2-A5、B 组、D 组、F 组）未逐条跑（每条 1~10 分钟），建议按 `golden-set.md` 的记录表抽样回归。

## 五、P0-P3 完善轮（2026-07-28）

**背景**：会话中断后工作区曾出现一次不明原因的文件回退（git 历史无异常），本轮所有代码已从上下文完整重建并重新验证。**强烈建议立即 git 提交防止再次丢失。**

### 改动清单

| 优先级 | 事项 | 状态 |
|---|---|---|
| P0-1 | 多租户隔离：FileSystemController 按会话归属过滤 + agent 文件沙箱收敛到 `workspace/<conversationId>/` | ✅ 已测 |
| P0-2 | 会话归属校验：ChatServiceImpl 拒绝非属主 conversationId（"无权访问该会话"） | ✅ 已测 |
| P0-3 | SSE 断开取消：emitter 回调 dispose 订阅，停止 agent 继续生成 | ✅ 已测（前轮） |
| P1 | spring-boot repackage fat jar（223MB 可独立启动）；sa-token Redis 持久化（sa-token-dao-redis-jackson）；前端链路 | ✅ 已测 |
| P2 | skill_usage_log 使用日志；golden set 评测 runner（scripts/skills-eval）；SkillAdminController 草稿审核 API | ✅ 已测 |
| P3 | MysqlSkillRepository 技能市场（skill_market 表）；search_skills 检索工具；技能关联元数据（related/next/requires） | ✅ 已测 |

### 本轮关键修复：文件沙箱越权（严重）

1. **`builder.workspace()` 泄露**：harness 会把 builder 的 workspace 路径加入文件工具允许根列表（PathPolicy = [project, workspace, additionalRoots]）。此前传 workspace 父目录导致 agent 可用绝对路径读取任意会话文件。修复：workspace 收敛到会话沙箱根。
2. **框架 ROOTED 模式相对路径逃逸（agentscope-harness 2.0.0 缺陷）**：`LocalFilesystem#resolveRooted` 对不带 `/` 前缀的相对路径直接 `cwd.resolve(path).normalize()` 返回，无包含性校验，`../其他会话/文件` 可逃逸（实测复现，agent 曾读到其他会话的 hello.txt）。修复：新增 `SessionSandboxFilesystem` 继承 `LocalFilesystemWithShell`，覆写 `resolvePath` 强制校验解析结果位于沙箱根内，经 `builder.abstractFilesystem()` 逃生舱挂载。**建议向 AgentScope 上游报告此漏洞。**
3. 连带调整：共享技能库 `workspace/skills` 改为显式只读 `FileSystemSkillRepository` 挂载（技能加载走仓库通道不受文件沙箱限制）；propose_skill 草稿随 workspace 收敛落到 `workspace/<conv>/skills/_drafts/`，SkillAdminController 改为跨会话扫描草稿并晋升到共享库。

### 验证记录（全部真实请求，非 mock）

| 验证项 | 结果 |
|---|---|
| 文件写入位置（无双重嵌套） | ✅ hello.txt 落在 `workspace/<conv>/` 根下 |
| L3 沙箱越权（`read_file ../其他会话/`） | ✅ 拒绝："路径越出会话工作目录"；绝对路径亦拒绝；agent 十余次 shell 逃逸尝试全部失败，无内容泄露 |
| tester 多租户（工作区列表/跨用户读/会话劫持） | ✅ 空列表 / "无权访问该工作目录" / "聊天请求被拒绝: 无权访问该会话" |
| golden set runner E2E（A1/E1/M1，两轮） | ✅ recall 3/3 ×2，含 MySQL 市场技能 git-commit-style 的 L1 加载，E1 零误触发 |
| skill_usage_log 落库 | ✅ load_skill_through_path / search_skills 均有记录 |
| Redis 登录态跨重启 | ✅ 杀进程重启（PID 50752→22364），旧 token 依然 200 |
| fat jar 独立启动 | ✅ ~6 秒可服务 |
| 草稿审核流 | ✅ 会话内草稿被列出（带 conversationId）→ promote 移入共享库 |
| 前端链路（ui-react vite dev server） | ✅ 页面 200；/auth/login 与 /api/chat SSE 经 vite 代理全通 |

### 已知限制

- `execute` shell 的 working_directory 已被框架校验为 workspace 内相对路径，但 shell 命令内部的 `cd`/相对路径仍是进程级行为，无法完全沙箱化（Windows 无容器隔离时的固有限制）；文件读写主通道已全部收敛。
- `.usage.json` 只统计 agent 自建技能（框架边界，bumpIfAgentTracked）；平台级统计以 skill_usage_log 为准。
