package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.handler.OutputHandlerRegistry;
import com.alibaba.cloud.ai.copilot.hook.ConversationHistoryHook;
import com.alibaba.cloud.ai.copilot.hook.ConversationSaveHook;
import com.alibaba.cloud.ai.copilot.hook.LongTermMemoryHook;
import com.alibaba.cloud.ai.copilot.interceptor.DynamicSystemPromptInterceptor;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolRegistry;
import com.alibaba.cloud.ai.copilot.service.mcp.McpClientManager;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


/**
 * 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AppProperties appProperties;
    private final DynamicModelService dynamicModelService;
    private final OutputHandlerRegistry outputHandlerRegistry;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ConversationHistoryHook conversationHistoryHook;
    private final ConversationSaveHook conversationSaveHook;
    private final DynamicSystemPromptInterceptor dynamicSystemPromptInterceptor;
    private final com.alibaba.cloud.ai.copilot.hook.MessageTraceHook messageTraceHook;
    private final McpClientManager mcpClientManager;
    private final BuiltinToolRegistry builtinToolRegistry;
    private final McpToolInfoMapper mcpToolInfoMapper;
    private final DatabaseStore databaseStore;
    private final LongTermMemoryHook longTermMemoryHook;

    @Override
    public void handleBuilderMode(ChatRequest request, String userId, SseEmitter emitter) {
        try {
            // 1. 获取或创建会话
            String conversationId = request.getConversationId();
            log.debug("收到聊天请求: conversationId={}, userId={}, message={}",
                    conversationId, userId, request.getMessage() != null ? request.getMessage().getContent() : "null");

            if (conversationId == null || conversationId.isEmpty()) {
                // 创建新会话
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());

                Long userIdLong = LoginHelper.getUserId();
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}, 原因: 请求中未提供conversationId",
                    conversationId, userIdLong);
            } else {
                log.debug("使用现有会话: conversationId={}, userId={}", conversationId, userId);
            }

            // 2. 获取 ChatModel
            ChatModel chatModel = dynamicModelService.getChatModelWithConfigId(request.getModelConfigId());

            // 3. 获取用于摘要的模型
            ChatModel summarizationModel = chatModel;

            // 4. 构建 Hooks
            List<Hook> hooks = new ArrayList<>();

            // 4.1 会话历史加载 Hook（从数据库加载历史消息）
            // 改进：只在首次请求时加载历史，后续让 ReactAgent 自己管理消息流
            hooks.add(conversationHistoryHook);

            // 4.2 消息压缩 Hook（当消息过多时自动压缩）
            hooks.add(SummarizationHook.builder()
                .model(summarizationModel)
                .maxTokensBeforeSummary(appProperties.getConversation().getSummarization().getMaxTokensBeforeSummary())
                .messagesToKeep(appProperties.getConversation().getSummarization().getMessagesToKeep())
                .build());

//            // 4.2.1 观测 Hook：用于确认 SummarizationHook 是否触发以及最终送入模型的 messages 长什么样
//            hooks.add(messageTraceHook);

            // 4.3 会话保存 Hook（保存 Assistant 响应到数据库）
            // 改进：只保存工具调用完成后的最终文本响应
            hooks.add(conversationSaveHook);

            // 4.4 长期记忆 Hook（加载用户画像和学习偏好）
            if (appProperties.getMemory().isEnabled()) {
                hooks.add(longTermMemoryHook);
            }

            // 5. 构建 Interceptors
            List<ModelInterceptor> interceptors = new ArrayList<>();

            // 5.1 动态系统提示
            interceptors.add(dynamicSystemPromptInterceptor);

            // 6. 加载工具
            List<ToolCallback> allTools = loadToolCallback();

            log.info("共加载 {} 个工具", allTools.size());

            // 6.3 构建 Agent
            var agentBuilder = ReactAgent.builder()
                    .name("copilot_agent")
                    .model(chatModel)
                    .systemPrompt(buildSystemPrompt())
                    .hooks(hooks.toArray(new Hook[0]))
                    .interceptors(interceptors.toArray(new ModelInterceptor[0]))
                    .saver(new MemorySaver())
                    .tools(allTools.toArray(new ToolCallback[0]));

            ReactAgent agent = agentBuilder.build();

            // 7. 设置会话ID到上下文（供 Hook 和 Interceptor 使用）
            Long userIdLong = LoginHelper.getUserId();
            RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                .addMetadata("conversationId", conversationId)
                .addMetadata("user_id", String.valueOf(userIdLong))
                // 供 LongTermMemoryHook 兜底 LLM 结构化抽取时优先使用当前会话同一个模型配置
                .addMetadata("model_config_id", request.getModelConfigId());

            // 设置偏好相关开关
            boolean enablePreferences = request.getEnablePreferences() != null
                ? request.getEnablePreferences()
                : true; // 默认启用
            boolean enablePreferenceLearning = request.getEnablePreferenceLearning() != null
                ? request.getEnablePreferenceLearning()
                : true; // 默认启用

            configBuilder.addMetadata("enable_preferences", String.valueOf(enablePreferences));
            configBuilder.addMetadata("enable_preference_learning", String.valueOf(enablePreferenceLearning));

            // 设置长期记忆存储（如果启用）
            if (appProperties.getMemory().isEnabled()) {
                configBuilder.store(databaseStore);
            }

            RunnableConfig config = configBuilder.build();

            // 8. 保存用户消息到数据库
            final String finalConversationId = conversationId; // 保存为 final 变量供 lambda 使用
            final String userMessageContent = request.getMessage().getContent(); // 保存用户消息内容

            ChatMessageEntity userMessageEntity = new ChatMessageEntity();
            userMessageEntity.setConversationId(finalConversationId);
            userMessageEntity.setMessageId(UUID.randomUUID().toString());
            userMessageEntity.setRole("user");
            userMessageEntity.setContent(userMessageContent);
            userMessageEntity.setCreatedTime(LocalDateTime.now());
            userMessageEntity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(userMessageEntity);

            // 9. 增加消息计数
            conversationService.incrementMessageCount(finalConversationId);

            // 10. 发送会话ID到前端（供前端保存并复用）
            sseEventService.sendConversationId(emitter, finalConversationId);

            // 11. 执行 Agent
            Flux<NodeOutput> stream = agent.stream(userMessageContent, config);

            stream.subscribe(
                output -> {
                    if (output instanceof StreamingOutput streamingOutput) {
                        outputHandlerRegistry.handle(streamingOutput, emitter);
                    }
                },
                error -> {
                    if (error instanceof WebClientResponseException wcre) {
                        // 关键：打印下游模型服务返回的错误响应体，便于定位 400 的具体原因
                        log.error("Agent execution error: status={}, body={}",
                            wcre.getStatusCode(),
                            wcre.getResponseBodyAsString(),
                            wcre);
                    } else {
                        log.error("Agent execution error", error);
                    }
                    sseEventService.sendComplete(emitter);
                },
                () -> {
                    // 流完成后，更新会话标题（基于首条用户消息）
                    updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                    sseEventService.sendComplete(emitter);
                }
            );

        } catch (GraphRunnerException e) {
            log.error("Error in builder mode", e);
            sseEventService.sendComplete(emitter);
        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            sseEventService.sendComplete(emitter);
        }
    }

    /**
     * 加载工具
     */
    private List<ToolCallback> loadToolCallback() {
        LambdaQueryWrapper<McpToolInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(McpToolInfo::getStatus, ToolStatus.ENABLED.getValue());
        List<McpToolInfo> enabledTools = mcpToolInfoMapper.selectList(queryWrapper);
        List<ToolCallback> allTools = new ArrayList<>();
        for (McpToolInfo tool : enabledTools) {
            try {
                if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getType())) {
                    // 内置工具 - 从注册表获取
                    ToolCallback callback = builtinToolRegistry.createToolCallback(tool.getName());
                    if (callback != null) {
                        allTools.add(callback);
                        log.debug("加载内置工具: {}", tool.getName());
                    }
                } else {
                    // MCP 工具 (LOCAL/REMOTE) - 从 McpClientManager 获取
                    List<ToolCallback> mcpCallbacks = mcpClientManager.getToolCallbacks(List.of(tool.getId()));
                    allTools.addAll(mcpCallbacks);
                    log.debug("加载 MCP 工具: {}", tool.getName());
                }
            } catch (Exception e) {
                log.error("加载工具失败: {} - {}", tool.getName(), e.getMessage());
                // 继续加载其他工具，不阻断
            }
        }
        return allTools;
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return "工作目录在:" + appProperties.getWorkspace().getRootDirectory() +
                java.io.File.separator + LoginHelper.getLoginUser().getUserType() + "_" +
                LoginHelper.getLoginUser().getUserId() +
                "\n所有的文件操作请在这个目录下进行";
    }

    /**
     * 更新会话标题（如果是新会话且标题为默认值）
     */
    private void updateConversationTitleIfNeeded(String conversationId, String firstMessage, Long userId) {
        try {
            var conversation = conversationService.getConversation(conversationId);
            if (conversation != null &&
                    ("新对话".equals(conversation.getTitle()) || conversation.getTitle() == null)) {
                // 生成标题（取前50个字符）
                String title = firstMessage.length() > 50
                    ? firstMessage.substring(0, 50) + "..."
                    : firstMessage;
                if (userId == null) {
                    log.debug("跳过更新会话标题：userId 为空: conversationId={}", conversationId);
                    return;
                }
                conversationService.updateConversationTitle(conversationId, title, userId);
                log.debug("更新会话标题: conversationId={}, title={}", conversationId, title);
            }
        } catch (IllegalArgumentException e) {
            // 常见原因：异步线程下无法获取/传递正确的登录上下文，或会话不属于当前用户
            log.warn("更新会话标题被拒绝: conversationId={}, reason={}", conversationId, e.getMessage());
        } catch (Exception e) {
            log.error("更新会话标题失败: conversationId={}", conversationId, e);
        }
    }
}
