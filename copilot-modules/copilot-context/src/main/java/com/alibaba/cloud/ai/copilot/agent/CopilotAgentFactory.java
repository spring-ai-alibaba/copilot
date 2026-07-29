package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.mcp.McpClientManager;
import com.alibaba.cloud.ai.copilot.skill.MysqlSkillRepository;
import com.alibaba.cloud.ai.copilot.skill.SearchSkillsTool;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 按需构建 agentscope {@link HarnessAgent}。
 *
 * <p>每个会话请求按 modelConfigId 动态构建一个 agent（模型是用户在 DB 里配置的），
 * agent 本身无状态，构建成本可接受；模型实例由 {@link DynamicModelService} 缓存。</p>
 *
 * <p>多租户隔离：文件沙箱根收敛到会话目录 workspace/&lt;conversationId&gt;/（ROOTED），
 * 并禁用 harness 默认的 SESSION 二级隔离避免双重嵌套；DeleteFileTool 与沙箱同根。</p>
 *
 * <p>技能：workspace/skills 由框架自动发现；MySQL 技能市场（skill_market 表）经
 * skillRepository 挂接；search_skills 元工具提供检索式发现；enableSkillManageTool
 * 开启自学习闭环第一步（草稿→人工审核晋升，见 SkillAdminController）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotAgentFactory {

    private final DynamicModelService dynamicModelService;
    private final AppProperties appProperties;
    private final MysqlAgentStateStore agentStateStore;
    private final ObjectProvider<MysqlSkillRepository> skillMarketProvider;
    private final McpClientManager mcpClientManager;

    private static final String AGENT_NAME = "copilot_agent";

    /**
     * 构建一个绑定指定模型配置的 HarnessAgent。
     */
    public HarnessAgent buildAgent(String modelConfigId) {
        return buildAgent(modelConfigId, null);
    }

    /**
     * 构建一个绑定指定模型配置与会话的 HarnessAgent。
     *
     * @param modelConfigId  模型配置 ID（model_config.id）
     * @param conversationId 会话 ID；文件沙箱与 DeleteFileTool 的根目录均为 workspace/&lt;conversationId&gt;/
     */
    public HarnessAgent buildAgent(String modelConfigId, String conversationId) {
        // 1. 获取 agentscope Model（缓存命中或按配置新建）
        Model model = dynamicModelService.getChatModelWithConfigId(modelConfigId);

        // 2. workspace 根目录（user.dir/workspace）
        String rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
        Path workspacePath = Path.of(rootDirectory);

        // 3. 文件系统沙箱：ROOTED 模式。沙箱根收敛到会话目录 workspace/<conversationId>/，
        //    否则 glob/read/write 能跨会话访问其他用户的文件（多租户隔离），
        //    且模型会把 glob 到的旧会话路径带进新写入路径（已多次实测复现）。
        Path sandboxRoot = conversationId != null && !conversationId.isBlank()
                ? workspacePath.resolve(conversationId)
                : workspacePath;
        try {
            Files.createDirectories(sandboxRoot);
        } catch (IOException e) {
            throw new IllegalStateException("创建会话工作目录失败: " + sandboxRoot, e);
        }
        // 沙箱文件系统：框架 ROOTED 模式存在相对路径 "../" 逃逸漏洞，
        // 改用逃生舱 abstractFilesystem 挂载带包含性校验的 SessionSandboxFilesystem
        SessionSandboxFilesystem sandboxFs = new SessionSandboxFilesystem(sandboxRoot);

        // 4. 消息压缩（替代原 SummarizationHook）
        AppProperties.Conversation.Summarization sum = appProperties.getConversation().getSummarization();
        CompactionConfig compaction = CompactionConfig.builder()
                .triggerMessages(sum.getMaxTokensBeforeSummary())
                .keepMessages(sum.getMessagesToKeep())
                .flushBeforeCompact(true)
                .build();

        // 5. 系统 prompt（工作目录=会话沙箱根）
        String prompt = buildSystemPrompt(sandboxRoot.toString());

        // 6. 工具：delete_file（沙箱同根）+ search_skills（检索式技能发现）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new DeleteFileTool(sandboxRoot.toString()));
        MysqlSkillRepository market = skillMarketProvider.getIfAvailable();
        toolkit.registerTool(new SearchSkillsTool(workspacePath.resolve("skills"), market));
        // 启用状态的 MCP 工具注册进 toolkit（连接由 McpClientManager 持久缓存，跨请求复用）
        int mcpCount = mcpClientManager.registerEnabledTools(toolkit);
        if (mcpCount > 0) {
            log.info("已注册 {} 个 MCP server 到 agent toolkit", mcpCount);
        }

        var builder = HarnessAgent.builder()
                .name(AGENT_NAME)
                .model(model)
                .sysPrompt(prompt)
                // workspace 必须与沙箱同根：builder 的 workspace 会被加入文件工具的允许根列表
                // （PathPolicy = [project, workspace, additionalRoots]），若传 workspace 父目录，
                // agent 就能用绝对路径读到其他会话的文件（L3 越权，实测复现）
                .workspace(sandboxRoot)
                .abstractFilesystem(sandboxFs)
                .toolkit(toolkit)
                .compaction(compaction)
                .stateStore(agentStateStore)
                // 自学习闭环第一步：propose_skill/skill_manage 走草稿→审核（defaults 不自动晋升）
                .enableSkillManageTool(SkillManageConfig.defaults())
                .maxIters(50);
        // 共享技能库 workspace/skills：workspace 已收敛到会话沙箱，
        // 共享技能改为显式只读仓库挂载（技能加载走仓库通道，不受文件沙箱限制）
        Path sharedSkillsDir = workspacePath.resolve("skills");
        if (Files.isDirectory(sharedSkillsDir)) {
            builder.skillRepository(new FileSystemSkillRepository(sharedSkillsDir, false));
        }
        // 技能市场（MySQL，只读分发；与 workspace/skills 同名时工作区版本优先）
        if (market != null) {
            builder.skillRepository(market);
        }
        HarnessAgent agent = builder.build();

        log.info("构建 HarnessAgent：modelConfigId={}, sandbox={}", modelConfigId, sandboxRoot);
        return agent;
    }

    private String buildSystemPrompt(String workDirectory) {
        return "【基础约束】\n" +
                "你是编程agent，使用工具在工作目录（" + workDirectory + "）内完成编程任务。\n\n" +
                "【技能使用】\n" +
                "开发规范以技能（skill）形式提供。动手写代码前，先检查 available_skills 中是否有匹配当前任务的技能，" +
                "有则先加载该技能并严格遵循其中的规范；没有匹配时可用 search_skills 检索。\n\n" +
                "【文件操作】\n" +
                "删除文件必须用 delete_file 工具，覆盖文件直接用 write_file；" +
                "不要用 shell 命令做删除/重命名/移动，shell 的工作目录与文件工具不一致，会失败。\n\n" +
                "【技术栈】\n" +
                "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，保持简洁专业的风格。";
    }
}
