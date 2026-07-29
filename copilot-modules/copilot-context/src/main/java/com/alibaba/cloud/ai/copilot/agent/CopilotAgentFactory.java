package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 按需构建 agentscope {@link HarnessAgent}。
 *
 * <p>每个会话请求按 modelConfigId 动态构建一个 agent（模型是用户在 DB 里配置的），
 * agent 本身无状态，构建成本可接受；模型实例由 {@link DynamicModelService} 缓存。</p>
 *
 * <p>阶段1（AG-UI 端到端切片）：使用 HarnessAgent 自带 FilesystemTool
 *（read_file/write_file/edit_file/grep_files/glob_files/list_files）+ compaction（替代原 SummarizationHook）。
 * 工具调用全程流式：streamEvents() 产出 TOOL_CALL_DELTA / TOOL_RESULT_TEXT_DELTA 等事件，
 * 经 AguiAgentAdapter 转 AG-UI 事件发往前端。</p>
 *
 * <p>会话历史/长期记忆的 Middleware 在阶段2 接入；阶段1 agent 自身无持久化中间件，
 * 仅靠每次请求携带的 user message 工作（多轮历史加载在阶段2 补）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotAgentFactory {

    private final DynamicModelService dynamicModelService;
    private final AppProperties appProperties;
    private final MysqlAgentStateStore agentStateStore;

    private static final String AGENT_NAME = "copilot_agent";
    private static final String PLAN_ROOT_DIRECTORY = "plans";

    /**
     * 构建一个绑定指定模型配置的 HarnessAgent。
     *
     * @param modelConfigId 模型配置 ID（model_config.id）
     * @return 新建的 HarnessAgent
     */
    public HarnessAgent buildAgent(String modelConfigId) {
        return buildAgent(modelConfigId, "default", false);
    }

    /**
     * 构建支持按会话隔离 Plan Mode 状态和计划文件的 HarnessAgent。
     *
     * @param modelConfigId 模型配置 ID
     * @param conversationId 会话 ID
     * @param planModeEnabled 是否注册 Plan Mode / Todo 工具
     * @return 新建的 HarnessAgent
     */
    public HarnessAgent buildAgent(
            String modelConfigId,
            String conversationId,
            boolean planModeEnabled) {
        return buildAgent(
                modelConfigId,
                conversationId,
                planModeEnabled,
                planModeEnabled);
    }

    /**
     * @param planningPhaseActive true 表示当前要生成或修订计划；
     *                            false 表示计划已获批准、进入执行阶段
     */
    public HarnessAgent buildAgent(
            String modelConfigId,
            String conversationId,
            boolean planModeEnabled,
            boolean planningPhaseActive) {
        // 1. 获取 agentscope Model（缓存命中或按配置新建）
        Model model = dynamicModelService.getChatModelWithConfigId(modelConfigId);

        // 2. workspace 根目录（与原 ChatServiceImpl 一致：user.dir/workspace）
        String rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
        Path workspacePath = Path.of(rootDirectory);

        // 3. 文件系统沙箱：ROOTED 模式，项目根即 workspace，放行其下读写
        LocalFilesystemSpec filesystemSpec = new LocalFilesystemSpec()
                .project(workspacePath)
                .mode(LocalFsMode.ROOTED)
                .projectWritable(true);

        // 4. 消息压缩（替代原 SummarizationHook）
        AppProperties.Conversation.Summarization sum = appProperties.getConversation().getSummarization();
        CompactionConfig compaction = CompactionConfig.builder()
                .triggerMessages(sum.getMaxTokensBeforeSummary())
                .keepMessages(sum.getMessagesToKeep())
                .flushBeforeCompact(true)
                .build();

        // 5. 系统 prompt（与原 ChatServiceImpl 一致）
        String planDirectory = planDirectory(conversationId);
        String prompt = buildSystemPrompt(
                rootDirectory,
                planDirectory,
                planModeEnabled,
                planningPhaseActive);

        // 6. 构建 agent（FilesystemTool 由 HarnessAgent 在 build 时自动注册）
        //    stateStore：agentscope 自动按 sessionId(=conversationId) load/save AgentState（含消息历史），多轮对话天然连续
        //    toolkit：补 delete_file 工具（自带 FilesystemTool 不含 delete）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new DeleteFileTool(rootDirectory));

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(AGENT_NAME)
                .model(model)
                .sysPrompt(prompt)
                .workspace(workspacePath)
                .filesystem(filesystemSpec)
                .toolkit(toolkit)
                .compaction(compaction)
                .stateStore(agentStateStore)
                .maxIters(50);

        if (planModeEnabled) {
            builder.enablePlanMode()
                    .enableTaskList()
                    .planFileDirectory(planDirectory)
                    .allowShellInPlanMode();
        }

        HarnessAgent agent = builder.build();

        log.info(
                "构建 HarnessAgent：modelConfigId={}, workspace={}, planMode={}",
                modelConfigId,
                rootDirectory,
                planModeEnabled);
        return agent;
    }

    /**
     * 返回某个会话的计划文件路径，供审批界面读取。
     */
    public Path resolvePlanFile(String conversationId) {
        String safeConversationId = sanitizeConversationId(conversationId);
        return resolveConversationWorkspace(conversationId)
                // planFileDirectory 相对于会话工作区解析。
                .resolve(planDirectory(conversationId))
                .resolve("PLAN.md")
                .normalize();
    }

    /**
     * 返回 HarnessAgent 为指定会话创建的隔离工作区。
     */
    public Path resolveConversationWorkspace(String conversationId) {
        return Paths.get(System.getProperty("user.dir"), "workspace")
                .resolve(sanitizeConversationId(conversationId))
                .normalize();
    }

    private String planDirectory(String conversationId) {
        return PLAN_ROOT_DIRECTORY + "/" + sanitizeConversationId(conversationId);
    }

    private String sanitizeConversationId(String conversationId) {
        return conversationId == null
                ? "default"
                : conversationId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    String buildSystemPrompt(
            String rootDirectory,
            String planDirectory,
            boolean planModeEnabled,
            boolean planningPhaseActive) {
        String basePrompt = "【基础约束】\n" +
                "你是编程agent，使用工具在项目根目录（" + rootDirectory + "）内完成编程任务。\n\n" +
                "【工具调用约束 - 必须遵守】\n" +
                "1. 每次工具调用都必须输出完整、合法的 JSON 参数，所有必填字段和引号必须完整闭合。\n" +
                "2. 单次 write_file 或 edit_file 携带的文件内容不得超过 8000 个字符。\n" +
                "3. 大文件先用 write_file 创建可运行的精简骨架，再按工具参数规范调用 edit_file 分段完善；" +
                "每段成功后再继续下一段，禁止一次生成完整的大型页面。\n" +
                "4. 参数校验失败时，必须检查必填字段并缩小单次内容，禁止使用相同参数原样重试。\n\n" +
                "【前端开发规范 - 必须遵守】\n" +
                "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n" +
                "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n" +
                "【技术栈】\n" +
                "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，" +
                "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。";

        if (!planModeEnabled) {
            return basePrompt;
        }

        if (!planningPhaseActive) {
            return basePrompt + "\n\n" +
                    "【计划已批准 - 执行阶段】\n" +
                    "人工已经批准 " + planDirectory + "/PLAN.md。你现在处于执行阶段，可以修改文件和运行命令。\n" +
                    "先读取该计划，再调用 todo_write 创建 5-8 个可独立验证的任务；" +
                    "每个任务包含文件路径，始终只保留一个 in_progress，并按计划逐项执行与验证。";
        }

        return basePrompt + "\n\n" +
                "【Plan Mode - 必须遵守】\n" +
                "你当前处于严格的只读计划阶段。先探索代码库，不得修改、创建或删除业务文件，" +
                "不得执行会改变项目状态的命令。\n" +
                "你可以调用 execute 执行只读 Shell 探索，但每次执行前先说明目的。\n" +
                "允许的命令：pwd、ls、cat、head、tail、sed -n、grep/rg、find、" +
                "git status/log/diff/show、mvn test、gradle test、npm/pnpm test。\n" +
                "禁止的命令：任何重定向写入、tee、sed -i、rm、mv、cp、touch、mkdir、" +
                "包安装、数据库写入、网络写操作、git add/commit/push/reset/checkout。\n" +
                "不得用管道、子 Shell 或脚本包装绕过以上限制。\n" +
                "探索完成后必须调用 plan_write，并严格使用以下 Markdown 结构。\n" +
                "所有文件路径都必须使用反引号包裹，例如 `src/main/App.java:20-40`，供审批界面生成改前片段：\n\n" +
                "## 任务理解\n" +
                "- 要解决的问题是：\n" +
                "- 涉及文件：（必须写实际路径，尽量带行号）\n" +
                "- 不碰的范围：\n\n" +
                "## 方案设计\n" +
                "- 选择方案及原因：\n" +
                "- 被排除的方案及原因：\n\n" +
                "## 变更清单\n" +
                "| 文件 | 操作 | 影响范围 |\n" +
                "|------|------|----------|\n\n" +
                "## 测试策略\n" +
                "- 新增或更新的测试：\n" +
                "- 手动验证：\n\n" +
                "## 风险点\n" +
                "- [ ] 数据库 migration 或不可逆操作\n" +
                "- [ ] 外部 API 调用\n" +
                "- [ ] 并发或线程安全问题\n" +
                "存在真实风险的项目必须改为 [x] 并说明具体影响；确认不存在的项目保持 [ ] 并写明“无”。\n\n" +
                "## 回滚方案\n" +
                "- 如果失败，如何恢复：\n\n" +
                "写入完整计划后调用 plan_exit 请求人工审批。在审批通过前不要开始执行。";
    }
}
