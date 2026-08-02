package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
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
 * <p>短期会话上下文由 AgentScope AgentStateStore 自动恢复和持久化；长期记忆、偏好与
 * RAG 注入不在本类处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotAgentFactory {

    private final DynamicModelService dynamicModelService;
    private final AppProperties appProperties;

    private static final String AGENT_NAME = "copilot_agent";

    /**
     * 构建一个绑定指定模型配置的 HarnessAgent。
     *
     * @param modelConfigId 模型配置 ID（model_config.id）
     * @param requestStateStore 绑定当前请求原始租约的状态存储
     * @return 新建的 HarnessAgent
     */
    public HarnessAgent buildAgent(String modelConfigId, AgentStateStore requestStateStore) {
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
                // Token threshold and retained message count are different units. Disable the
                // framework defaults so a message count cannot trigger an unexpected compaction.
                .triggerMessages(0)
                .triggerTokens(sum.getMaxTokensBeforeSummary())
                .keepMessages(sum.getMessagesToKeep())
                .keepTokens(0)
                .flushBeforeCompact(false)
                .offloadBeforeCompact(false)
                .build();

        // 5. 系统 prompt（与原 ChatServiceImpl 一致）
        String prompt = buildSystemPrompt(rootDirectory);

        // 6. 构建 agent（FilesystemTool 由 HarnessAgent 在 build 时自动注册）
        //    stateStore：agentscope 自动按 sessionId(=conversationId) load/save AgentState（含消息历史），多轮对话天然连续
        //    toolkit：补 delete_file 工具（自带 FilesystemTool 不含 delete）
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new DeleteFileTool(rootDirectory));

        HarnessAgent agent = HarnessAgent.builder()
                .name(AGENT_NAME)
                .model(model)
                .sysPrompt(prompt)
                .workspace(workspacePath)
                .filesystem(filesystemSpec)
                .toolkit(toolkit)
                .compaction(compaction)
                .disableToolResultEviction()
                .stateStore(requestStateStore)
                .maxIters(50)
                .build();

        log.info("构建 HarnessAgent：modelConfigId={}, workspace={}", modelConfigId, rootDirectory);
        return agent;
    }

    private String buildSystemPrompt(String rootDirectory) {
        return "【基础约束】\n" +
                "你是编程agent，使用工具在项目根目录（" + rootDirectory + "）内完成编程任务。\n\n" +
                "【前端开发规范 - 必须遵守】\n" +
                "1. 禁止手写大量CSS！必须使用 Tailwind CSS 框架\n" +
                "2. HTML页面必须引入 Tailwind CSS CDN：<script src=\"https://cdn.tailwindcss.com\"></script>\n" +
                "【技术栈】\n" +
                "擅长 java+vue+element 技术栈，用户没有明确编程需求时正常对话即可，" +
                "前端开发默认使用 HTML + Tailwind CSS，保持简洁专业的风格。";
    }
}
