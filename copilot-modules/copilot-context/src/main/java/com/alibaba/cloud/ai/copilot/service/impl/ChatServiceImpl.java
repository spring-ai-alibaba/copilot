package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.model.ToolMergeMode;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天服务实现（agentscope 2.0 + AG-UI 协议）。
 *
 * <p>每个请求：动态构建 {@link HarnessAgent} → 用 {@link AguiAgentAdapter} 把
 * agent.streamEvents() 的 AgentEvent 流转成 AG-UI {@link AguiEvent} 流 → 经
 *
 * <p>工具调用全程流式：AG-UI 的 TOOL_CALL_START / TOOL_CALL_ARGS(delta) /
 * TOOL_CALL_END / TOOL_CALL_RESULT 事件逐帧下发，前端可在工具执行过程中
 * 实时看到参数增量与结果——这正是原 spring-ai-alibaba 框架做不到的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CopilotAgentFactory agentFactory;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final AppProperties appProperties;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;
    private final ConversationContextService contextService;
    private final SessionRunGuard sessionRunGuard;

    @Override
    public SseEmitter handleBuilderMode(ChatRequest request) {
        validateRequest(request);
        Long userIdLong = LoginHelper.getUserId();
        if (userIdLong == null) {
            throw new ServiceException("未登录", 401);
        }

        // Validate the model before creating a conversation. Otherwise an invalid or
        // private model id could leave an orphaned conversation (and expose a model
        // configuration to DynamicModelService).
        assertModelConfigAccess(request.getModelConfigId(), userIdLong);

        String conversationId = request.getConversationId();
        boolean createNewConversation = conversationId == null || conversationId.isBlank();
        if (createNewConversation) {
            conversationId = UUID.randomUUID().toString().replace("-", "");
        } else {
            assertConversationAccess(conversationId, userIdLong);
        }

        final String finalConversationId = conversationId;
        final String userKey = String.valueOf(userIdLong);
        final String userMessageContent = request.getMessage().getContent();
        String runId = UUID.randomUUID().toString();
        SessionRunGuard.Lease lease;
        try {
            lease = sessionRunGuard.acquire(userKey, finalConversationId, runId);
        } catch (SessionRunGuard.SessionRunConflictException e) {
            throw new ServiceException("会话正在处理中，请稍后重试", 409);
        }
        AuthenticatedAgentDelegate scopedAgent = null;
        try {
            // Fail before committing an SSE response. This also prevents a Store outage from
            // being interpreted as a fresh empty conversation by AgentScope 2.0.0.
            contextService.assertStoreReadable(finalConversationId, userIdLong);
            final String modelConfigId;
            final HarnessAgent agent;
            if (createNewConversation) {
                modelConfigId = request.getModelConfigId();
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(modelConfigId);
                // Create the conversation before building the agent. If creation fails
                // there is no agent to leak (scopedAgent is not assigned yet).
                conversationService.createConversation(userIdLong, createRequest, finalConversationId);
                agent = agentFactory.buildAgent(modelConfigId);
                log.info("创建新会话: conversationId={}, userId={}", finalConversationId, userIdLong);
            } else {
                // Close the check-then-acquire race with deletion/reset. The session may have
                // changed while this request was waiting for the lease.
                assertConversationAccess(finalConversationId, userIdLong);
                modelConfigId = resolveModelConfigId(request, finalConversationId, userIdLong);
                agent = agentFactory.buildAgent(modelConfigId);
            }
            final AuthenticatedAgentDelegate requestAgent =
                    new AuthenticatedAgentDelegate(agent, userKey, finalConversationId);
            scopedAgent = requestAgent;

            saveUserMessage(finalConversationId, userMessageContent);

            String messageId = UUID.randomUUID().toString();
            Map<String, Object> forwardedProps = new java.util.HashMap<>();
            forwardedProps.put("modelConfigId", modelConfigId);
            forwardedProps.put("conversationId", finalConversationId);
            // UI flags are retained for compatibility only. They are not identity or context data.
            forwardedProps.put("enablePreferences",
                    request.getEnablePreferences() == null || request.getEnablePreferences());
            forwardedProps.put("enablePreferenceLearning",
                    request.getEnablePreferenceLearning() == null || request.getEnablePreferenceLearning());

            RunAgentInput runInput = RunAgentInput.builder()
                    .threadId(finalConversationId)
                    .runId(runId)
                    .messages(List.of(AguiMessage.userMessage(messageId, userMessageContent)))
                    .forwardedProps(forwardedProps)
                    .build();

            AguiAdapterConfig adapterConfig = AguiAdapterConfig.builder()
                    .emitToolCallArgs(true)
                    .enableReasoning(true)
                    .emitStateEvents(false)
                    .toolMergeMode(ToolMergeMode.AGENT_ONLY)
                    .build();
            AguiAgentAdapter adapter = new AguiAgentAdapter(requestAgent, adapterConfig);
            SseEmitter emitter = new SseEmitter(0L);
            AtomicReference<reactor.core.Disposable> subscriptionRef = new AtomicReference<>();
            AtomicReference<StringBuilder> assistantText = new AtomicReference<>(new StringBuilder());
            AtomicBoolean runFailed = new AtomicBoolean();
            AtomicBoolean cleanedUp = new AtomicBoolean();
            Runnable cleanup = () -> {
                if (cleanedUp.compareAndSet(false, true)) {
                    reactor.core.Disposable subscription = subscriptionRef.get();
                    if (subscription != null && !subscription.isDisposed()) {
                        subscription.dispose();
                    }
                    requestAgent.close();
                    lease.close();
                }
            };
            emitter.onCompletion(cleanup);
            emitter.onTimeout(cleanup);
            emitter.onError(error -> cleanup.run());

            sseEventService.sendConversationId(emitter, finalConversationId);
            Flux<AguiEvent> aguiEvents = adapter.run(runInput)
                    .onErrorResume(error -> Flux.just(
                            new AguiEvent.RunError(
                                    finalConversationId,
                                    runId,
                                    "Agent 执行失败",
                                    "INTERNAL_ERROR"),
                            new AguiEvent.RunFinished(finalConversationId, runId)))
                    .doOnNext(event -> {
                        if (event instanceof AguiEvent.RunError) {
                            runFailed.set(true);
                        }
                    })
                    // The frontend maps RUN_FINISHED to [DONE]. Emit context metadata before it.
                    .concatMap(event -> {
                        if (!(event instanceof AguiEvent.RunFinished) || runFailed.get()) {
                            return Flux.just(event);
                        }
                        return Flux.defer(() -> {
                            AuthenticatedAgentDelegate.TokenUsageSnapshot usage = requestAgent.getTokenUsage();
                            ConversationContextStatus status = contextService.recordSuccessfulRun(
                                    finalConversationId, userIdLong, usage);
                            return Flux.just(
                                    new AguiEvent.Custom(finalConversationId, runId, "token_usage", usage),
                                    new AguiEvent.Custom(finalConversationId, runId, "context_status", status),
                                    event);
                        }).onErrorResume(error -> {
                            // AgentScope has already committed agent_state before RunFinished.
                            // Metadata is an observability projection: failing it must not tell
                            // the client to retry an already-applied run or skip timeline storage.
                            log.error("上下文元数据投影失败，保留已成功的 Agent 运行: conversationId={}",
                                    finalConversationId, error);
                            return Flux.just(event);
                        });
                    });

            reactor.core.Disposable subscription = aguiEvents.subscribe(
                    event -> sendAguiEvent(emitter, event, assistantText),
                    error -> {
                        runFailed.set(true);
                        log.error("Agent 执行出错: conversationId={}", finalConversationId, error);
                        cleanup.run();
                        sseEventService.sendComplete(emitter);
                    },
                    () -> {
                        if (!runFailed.get()) {
                            saveAssistantMessage(finalConversationId, assistantText.get().toString());
                            updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                        }
                        cleanup.run();
                        sseEventService.sendComplete(emitter);
                    });
            subscriptionRef.set(subscription);
            // A synchronous publisher can complete before subscribe() returns. In that
            // case cleanup ran before the handle was registered; dispose it immediately.
            if (cleanedUp.get() && !subscription.isDisposed()) {
                subscription.dispose();
            }
            return emitter;
        } catch (FailClosedAgentStateStore.StateReadFailureError e) {
            if (scopedAgent != null) {
                scopedAgent.close();
            }
            lease.close();
            throw new ServiceException("读取上下文失败", 503).setDetailMessage(e.getMessage());
        } catch (RuntimeException e) {
            if (scopedAgent != null) {
                scopedAgent.close();
            }
            lease.close();
            throw e;
        }
    }

    private void validateRequest(ChatRequest request) {
        if (request == null || request.getMessage() == null
                || request.getMessage().getContent() == null
                || request.getMessage().getContent().isBlank()) {
            throw new ServiceException("消息内容不能为空", 422);
        }
        if (request.getModelConfigId() == null || request.getModelConfigId().isBlank()) {
            throw new ServiceException("必须选择模型配置", 422);
        }
    }

    private void assertConversationAccess(String conversationId, Long userId) {
        try {
            conversationService.checkConversationPermission(conversationId, userId);
        } catch (IllegalArgumentException e) {
            // Do not reveal whether another user's conversation exists.
            throw new ServiceException("会话不存在或无权访问", 403);
        }
    }

    private String resolveModelConfigId(ChatRequest request, String conversationId, Long userId) {
        ConversationDTO conversation = conversationService.getConversation(conversationId);
        if (conversation == null || conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            throw new ServiceException("会话不存在或无权访问", 403);
        }
        String requestedModelId = request.getModelConfigId();
        if (conversation.getModelConfigId() != null) {
            try {
                if (!conversation.getModelConfigId().equals(Long.valueOf(requestedModelId))) {
                    throw new ServiceException("模型配置与会话不一致", 422);
                }
            } catch (NumberFormatException e) {
                throw new ServiceException("模型配置无效", 422);
            }
        }
        assertModelConfigAccess(requestedModelId, userId);
        return requestedModelId;
    }

    private void assertModelConfigAccess(String modelConfigId, Long userId) {
        final long modelId;
        try {
            modelId = Long.parseLong(modelConfigId);
        } catch (NumberFormatException e) {
            throw new ServiceException("模型配置无效", 422);
        }
        ModelConfigEntity model = modelConfigMapper.selectById(modelId);
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                || (!"PUBLIC".equalsIgnoreCase(model.getVisibility())
                && !userId.equals(model.getUserId()))) {
            throw new ServiceException("模型配置不存在或无权使用", 404);
        }
    }

    private void saveUserMessage(String conversationId, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole("user");
        entity.setContent(content);
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        chatMessageMapper.insert(entity);
        conversationService.incrementMessageCount(conversationId);
    }

    /**
     * 把单个 AG-UI 事件编码为 SSE 帧并发送。
     * 直接用 agentscope JsonUtils 序列化为 JSON 作为 SSE data，事件类型名作为 SSE event 字段，
     * 便于前端按名订阅。累积 assistant 文本用于落库。
     */
    private void sendAguiEvent(SseEmitter emitter, AguiEvent event, AtomicReference<StringBuilder> assistantText) {
        try {
            // 累积 assistant 文本（用于落库）
            if (event instanceof AguiEvent.TextMessageContent tmc) {
                if (tmc.delta() != null) {
                    assistantText.get().append(tmc.delta());
                }
            }
            // 序列化为 JSON 并下发（SSE 帧：event:<TYPE>\ndata:<json>\n\n）
            String json = JsonUtils.getJsonCodec().toJson(event);
            emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new IllegalStateException("发送 AG-UI 事件失败", e);
        }
    }

    /**
     * 落库 assistant 展示文本。完整工具链由 AgentScope AgentStateStore 保存，不从该表恢复。
     */
    private void saveAssistantMessage(String conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            ChatMessageEntity entity = new ChatMessageEntity();
            entity.setConversationId(conversationId);
            entity.setMessageId(UUID.randomUUID().toString());
            entity.setRole("assistant");
            entity.setContent(content);
            entity.setCreatedTime(LocalDateTime.now());
            entity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(entity);
        } catch (Exception e) {
            log.error("保存 assistant 消息失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 更新会话标题（如果是新会话且标题为默认值）。
     */
    private void updateConversationTitleIfNeeded(String conversationId, String firstMessage, Long userId) {
        try {
            var conversation = conversationService.getConversation(conversationId);
            if (conversation != null &&
                    ("新对话".equals(conversation.getTitle()) || conversation.getTitle() == null)) {
                String title = firstMessage.length() > 50
                        ? firstMessage.substring(0, 50) + "..."
                        : firstMessage;
                if (userId == null) {
                    log.debug("跳过更新会话标题：userId 为空: conversationId={}", conversationId);
                    return;
                }
                conversationService.updateConversationTitle(conversationId, title, userId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("更新会话标题被拒绝: conversationId={}, reason={}", conversationId, e.getMessage());
        } catch (Exception e) {
            log.error("更新会话标题失败: conversationId={}", conversationId, e);
        }
    }
}
