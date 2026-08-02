package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.agent.AuthenticatedAgentDelegate;
import com.alibaba.cloud.ai.copilot.agent.AgentScopeShutdownRegistry;
import com.alibaba.cloud.ai.copilot.agent.FailClosedAgentStateStore;
import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.model.ToolMergeMode;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.ShutdownStateSaver;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
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

    private static final long DEFAULT_RUN_TIMEOUT_SECONDS = 300;
    private static final long SSE_TIMEOUT_GRACE_SECONDS = 5;
    private static final ShutdownStateSaver DETACHED_SHUTDOWN_SAVER = ignored -> {
    };

    private final CopilotAgentFactory agentFactory;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ModelConfigMapper modelConfigMapper;
    private final AppProperties appProperties;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;
    private final ConversationContextService contextService;
    private final SessionRunGuard sessionRunGuard;
    private final FailClosedAgentStateStore agentStateStore;

    @Value("${app.conversation.run-timeout-seconds:300}")
    private long runTimeoutSeconds = DEFAULT_RUN_TIMEOUT_SECONDS;

    @Override
    public SseEmitter handleBuilderMode(ChatRequest request) {
        try {
            return handleBuilderModeInternal(request);
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("聊天同步初始化失败", e);
            throw new ServiceException("聊天初始化失败", 500).setDetailMessage(e.getMessage());
        }
    }

    private SseEmitter handleBuilderModeInternal(ChatRequest request) {
        validateRequest(request);
        Long userIdLong = LoginHelper.getUserId();
        if (userIdLong == null) {
            throw new ServiceException("未登录", 401);
        }
        Duration runTimeout = resolveRunTimeout();

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
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            throw new ServiceException("会话锁服务暂不可用，请稍后重试", 503)
                    .setDetailMessage(e.getMessage());
        }
        AuthenticatedAgentDelegate scopedAgent = null;
        HarnessAgent builtAgent = null;
        FailClosedAgentStateStore.LeaseBoundAgentStateStore scopedStateStore = null;
        boolean newConversationPersisted = false;
        try {
            final long runDeadlineNanos = runDeadlineFromNow(runTimeout);
            // Fail before committing an SSE response. This also prevents a Store outage from
            // being interpreted as a fresh empty conversation by AgentScope 2.0.0.
            lease.assertOwned();
            contextService.assertStoreReadable(finalConversationId, userIdLong, lease);
            lease.assertOwned();
            final String modelConfigId;
            if (createNewConversation) {
                modelConfigId = request.getModelConfigId();
            } else {
                // Close the check-then-acquire race with deletion/reset. The session may have
                // changed while this request was waiting for the lease.
                assertConversationAccess(finalConversationId, userIdLong);
                modelConfigId = resolveModelConfigId(request, finalConversationId, userIdLong);
            }
            // Build and scope the Agent before committing a new conversation. If Agent setup
            // fails there is no database row to orphan; if persistence fails the outer cleanup
            // closes the already-built Agent.
            final FailClosedAgentStateStore.LeaseBoundAgentStateStore requestStateStore =
                    agentStateStore.bind(lease, userKey, finalConversationId);
            scopedStateStore = requestStateStore;
            final HarnessAgent agent = agentFactory.buildAgent(modelConfigId, requestStateStore);
            builtAgent = agent;
            final AuthenticatedAgentDelegate requestAgent =
                    createScopedAgent(agent, userKey, finalConversationId);
            scopedAgent = requestAgent;

            lease.assertOwned();
            if (createNewConversation) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(modelConfigId);
                conversationService.createConversation(
                        userIdLong,
                        createRequest,
                        finalConversationId,
                        lease);
                newConversationPersisted = true;
                log.info("创建新会话: conversationId={}, userId={}", finalConversationId, userIdLong);
            }
            lease.assertOwned();

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
            Flux<AguiEvent> rawAguiEvents = adapter.run(runInput);
            lease.assertOwned();
            conversationService.appendUserMessage(
                    finalConversationId,
                    userMessageContent,
                    modelConfigId,
                    userIdLong,
                    lease);
            lease.assertOwned();
            Duration remainingRunTime = remainingRunTime(runDeadlineNanos);
            SseEmitter emitter = new SseEmitter(resolveSseTimeoutMillis(remainingRunTime));
            AtomicReference<reactor.core.Disposable> subscriptionRef = new AtomicReference<>();
            AtomicReference<StringBuilder> assistantText = new AtomicReference<>(new StringBuilder());
            AtomicBoolean runFailed = new AtomicBoolean();
            AtomicBoolean cleanedUp = new AtomicBoolean();
            Runnable releaseResources = () -> {
                if (cleanedUp.compareAndSet(false, true)) {
                    // Stop the watchdog before Agent cleanup. The row remains owned until close;
                    // if Agent.close() hangs, the unrenewed lease expires instead of locking the
                    // conversation forever.
                    lease.stopRenewal();
                    detachAgentShutdownSaver(agent);
                    // AgentScope 2.0 retains a shutdown saver per request-built Agent and does not
                    // close its state store from Agent.close(). Detach the lease/request graph
                    // explicitly before potentially blocking Agent cleanup.
                    requestStateStore.close();
                    try {
                        requestAgent.close();
                    } catch (RuntimeException closeError) {
                        log.warn("关闭 Agent 失败: conversationId={}", finalConversationId, closeError);
                    } finally {
                        try {
                            lease.close();
                        } catch (RuntimeException releaseError) {
                            log.warn("释放会话 lease 失败: conversationId={}",
                                    finalConversationId, releaseError);
                        }
                    }
                }
            };
            Runnable cancelRun = () -> {
                runFailed.set(true);
                try {
                    reactor.core.Disposable subscription = subscriptionRef.get();
                    if (subscription != null && !subscription.isDisposed()) {
                        subscription.dispose();
                    }
                } finally {
                    releaseResources.run();
                }
            };
            emitter.onCompletion(cancelRun);
            emitter.onTimeout(cancelRun);
            emitter.onError(error -> cancelRun.run());
            Runnable cancelRunAfterLeaseLoss = () -> {
                runFailed.set(true);
                try {
                    emitter.completeWithError(
                            new IllegalStateException("session lease ownership was lost"));
                } finally {
                    cancelRun.run();
                }
            };

            Flux<AguiEvent> aguiEvents = withOverallDeadline(rawAguiEvents, runDeadlineNanos)
                    .onErrorResume(TimeoutException.class, error -> {
                        log.warn("Agent 执行超时: conversationId={}, timeoutMs={}",
                                finalConversationId, runTimeout.toMillis());
                        return Flux.just(
                                new AguiEvent.RunError(
                                        finalConversationId,
                                        runId,
                                        "Agent 执行超时",
                                        "RUN_TIMEOUT"),
                                new AguiEvent.RunFinished(finalConversationId, runId));
                    })
                    .onErrorResume(error -> {
                        log.error("Agent 执行失败: conversationId={}", finalConversationId, error);
                        return Flux.just(
                                new AguiEvent.RunError(
                                        finalConversationId,
                                        runId,
                                        "Agent 执行失败",
                                        "INTERNAL_ERROR"),
                                new AguiEvent.RunFinished(finalConversationId, runId));
                    })
                    .doOnNext(event -> {
                        if (event instanceof AguiEvent.TextMessageContent textContent
                                && textContent.delta() != null) {
                            assistantText.get().append(textContent.delta());
                        }
                        if (event instanceof AguiEvent.RunError) {
                            runFailed.set(true);
                        }
                    })
                    // The frontend maps RUN_FINISHED to [DONE]. Emit context metadata before it.
                    .concatMap(event -> {
                        if (!(event instanceof AguiEvent.RunFinished) || runFailed.get()) {
                            return Flux.just(event);
                        }
                        return finalizeSuccessfulRun(
                                event,
                                finalConversationId,
                                runId,
                                assistantText.get().toString(),
                                userMessageContent,
                                userIdLong,
                                requestAgent,
                                lease,
                                runFailed);
                    })
                    // Covers normal completion, downstream send failures and external
                    // cancellation. Emitter callbacks additionally dispose the subscription.
                    .doFinally(signalType -> releaseResources.run());

            lease.assertOwned();
            sseEventService.sendConversationId(emitter, finalConversationId);
            reactor.core.Disposable subscription = aguiEvents.subscribe(
                    event -> sendAguiEvent(emitter, event),
                    error -> {
                        runFailed.set(true);
                        log.error("Agent 执行出错: conversationId={}", finalConversationId, error);
                        releaseResources.run();
                        sseEventService.sendComplete(emitter);
                    },
                    () -> {
                        releaseResources.run();
                        sseEventService.sendComplete(emitter);
                    });
            subscriptionRef.set(subscription);
            // Register after the handle exists. Registration immediately fires when ownership
            // was already lost, so the stream cannot continue silently on a stale lease.
            lease.onOwnershipLost(cancelRunAfterLeaseLoss);
            // A synchronous publisher can complete before subscribe() returns. In that
            // case cleanup ran before the handle was registered; dispose it immediately.
            if (cleanedUp.get() && !subscription.isDisposed()) {
                subscription.dispose();
            }
            return emitter;
        } catch (FailClosedAgentStateStore.StateReadFailureError e) {
            cleanupFailedSetup(scopedAgent, builtAgent, scopedStateStore, lease,
                    newConversationPersisted,
                    finalConversationId, userIdLong);
            throw new ServiceException("读取上下文失败", 503).setDetailMessage(e.getMessage());
        } catch (ServiceException e) {
            cleanupFailedSetup(scopedAgent, builtAgent, scopedStateStore, lease,
                    newConversationPersisted,
                    finalConversationId, userIdLong);
            throw e;
        } catch (SessionRunGuard.SessionRunUnavailableException e) {
            cleanupFailedSetup(scopedAgent, builtAgent, scopedStateStore, lease,
                    newConversationPersisted,
                    finalConversationId, userIdLong);
            throw new ServiceException("会话锁已失效，请稍后重试", 503)
                    .setDetailMessage(e.getMessage());
        } catch (RuntimeException e) {
            cleanupFailedSetup(scopedAgent, builtAgent, scopedStateStore, lease,
                    newConversationPersisted,
                    finalConversationId, userIdLong);
            log.error("聊天同步初始化失败: conversationId={}", finalConversationId, e);
            throw new ServiceException("聊天初始化失败", 500).setDetailMessage(e.getMessage());
        }
    }

    private Duration resolveRunTimeout() {
        if (runTimeoutSeconds <= 0) {
            throw new ServiceException("聊天运行超时配置无效", 500);
        }
        return Duration.ofSeconds(runTimeoutSeconds);
    }

    private long runDeadlineFromNow(Duration runTimeout) {
        try {
            return Math.addExact(System.nanoTime(), runTimeout.toNanos());
        } catch (ArithmeticException e) {
            throw new ServiceException("聊天运行超时配置无效", 500)
                    .setDetailMessage(e.getMessage());
        }
    }

    private Duration remainingRunTime(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new ServiceException("聊天初始化超时", 504);
        }
        return Duration.ofNanos(remainingNanos);
    }

    private long resolveSseTimeoutMillis(Duration runTimeout) {
        try {
            return runTimeout.plusSeconds(SSE_TIMEOUT_GRACE_SECONDS).toMillis();
        } catch (ArithmeticException e) {
            throw new ServiceException("聊天运行超时配置无效", 500)
                    .setDetailMessage(e.getMessage());
        }
    }

    private static Flux<AguiEvent> withOverallDeadline(
            Flux<AguiEvent> source,
            long deadlineNanos) {
        return Flux.defer(() -> {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return Flux.error(new TimeoutException("Agent run deadline already elapsed"));
            }
            return withOverallTimeout(source, Duration.ofNanos(remainingNanos));
        });
    }

    Flux<AguiEvent> finalizeSuccessfulRun(
            AguiEvent runFinished,
            String conversationId,
            String runId,
            String assistantContent,
            String userMessageContent,
            Long userId,
            AuthenticatedAgentDelegate requestAgent,
            SessionRunGuard.Lease lease,
            AtomicBoolean runFailed) {
        return Flux.defer(() -> {
            try {
                lease.assertOwned();
                conversationService.persistSuccessfulAssistantTurn(
                        conversationId,
                        assistantContent,
                        userMessageContent,
                        userId,
                        lease);
            } catch (SessionRunGuard.SessionRunUnavailableException error) {
                runFailed.set(true);
                log.error("保存聊天时间线前会话锁已失效: conversationId={}",
                        conversationId, error);
                return Flux.just(
                        new AguiEvent.RunError(
                                conversationId,
                                runId,
                                "会话锁已失效",
                                "LEASE_LOST"),
                        runFinished);
            } catch (RuntimeException error) {
                runFailed.set(true);
                log.error("保存 assistant 时间线失败: conversationId={}",
                        conversationId, error);
                return Flux.just(
                        new AguiEvent.RunError(
                                conversationId,
                                runId,
                                "保存聊天记录失败",
                                "TIMELINE_PERSIST_FAILED"),
                        runFinished);
            }

            try {
                AuthenticatedAgentDelegate.TokenUsageSnapshot usage = requestAgent.getTokenUsage();
                ConversationContextStatus status = contextService.recordSuccessfulRun(
                        conversationId, userId, usage, lease);
                lease.assertOwned();
                return Flux.just(
                        new AguiEvent.Custom(conversationId, runId, "token_usage", usage),
                        new AguiEvent.Custom(conversationId, runId, "context_status", status),
                        runFinished);
            } catch (RuntimeException error) {
                // AgentScope and the assistant timeline have already been committed. Metadata is
                // an observability projection. Lease loss after that commit point is also
                // non-retryable: reporting failure would invite a duplicate user turn.
                log.error("上下文元数据投影失败，保留已成功的 Agent 运行: conversationId={}",
                        conversationId, error);
                return Flux.just(runFinished);
            }
        });
    }

    /** Apply a duration timeout to the whole publisher, not an inactivity timeout per token. */
    static Flux<AguiEvent> withOverallTimeout(Flux<AguiEvent> source, Duration timeout) {
        AtomicBoolean timedOut = new AtomicBoolean();
        Mono<Long> deadline = Mono.delay(timeout)
                .doOnNext(ignored -> timedOut.set(true));
        return source.takeUntilOther(deadline)
                .concatWith(Flux.defer(() -> timedOut.get()
                        ? Flux.<AguiEvent>error(new TimeoutException("Agent run exceeded " + timeout))
                        : Flux.<AguiEvent>empty()));
    }

    private AuthenticatedAgentDelegate createScopedAgent(
            HarnessAgent agent,
            String userId,
            String conversationId) {
        try {
            return new AuthenticatedAgentDelegate(agent, userId, conversationId);
        } catch (RuntimeException setupError) {
            if (agent != null) {
                try {
                    agent.close();
                } catch (RuntimeException closeError) {
                    setupError.addSuppressed(closeError);
                }
            }
            throw setupError;
        }
    }

    private void cleanupFailedSetup(
            AuthenticatedAgentDelegate scopedAgent,
            HarnessAgent builtAgent,
            FailClosedAgentStateStore.LeaseBoundAgentStateStore scopedStateStore,
            SessionRunGuard.Lease lease,
            boolean deleteNewConversation,
            String conversationId,
            Long userId) {
        detachAgentShutdownSaver(builtAgent);
        if (scopedStateStore != null) {
            // Detach the request-bound lease even when Agent construction/scoping failed.
            scopedStateStore.close();
        }
        if (scopedAgent != null) {
            lease.stopRenewal();
            try {
                scopedAgent.close();
            } catch (RuntimeException closeError) {
                log.warn("关闭初始化失败的 Agent 失败: conversationId={}", conversationId, closeError);
            }
        }
        // Compensation uses ConversationService.deleteConversation(), which acquires its own
        // lease. Release this run first to avoid conflicting with ourselves.
        try {
            lease.close();
        } catch (RuntimeException releaseError) {
            log.warn("释放初始化失败请求的会话 lease 失败: conversationId={}",
                    conversationId, releaseError);
        }
        if (deleteNewConversation) {
            try {
                conversationService.deleteConversation(conversationId, userId);
            } catch (RuntimeException compensationError) {
                log.error("补偿删除初始化失败的新会话失败: conversationId={}, userId={}",
                        conversationId, userId, compensationError);
            }
        }
    }

    private void detachAgentShutdownSaver(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            String agentId = agent.getAgentId();
            if (agentId == null || agentId.isBlank()) {
                return;
            }
            AgentScopeShutdownRegistry.unregister(agent);
        } catch (RuntimeException e) {
            // Preserve the larger object-graph cleanup if a future AgentScope version changes the
            // private registry layout. The pinned 2.0.0 path removes the entry entirely.
            GracefulShutdownManager.getInstance().bindStateSaver(agent, DETACHED_SHUTDOWN_SAVER);
            log.warn("注销 AgentScope shutdown saver 失败，已退化为无状态占位", e);
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

    /**
     * 把单个 AG-UI 事件编码为 SSE 帧并发送。
     * 直接用 agentscope JsonUtils 序列化为 JSON 作为 SSE data，事件类型名作为 SSE event 字段，
     * 便于前端按名订阅。累积 assistant 文本用于落库。
     */
    private void sendAguiEvent(SseEmitter emitter, AguiEvent event) {
        try {
            // 序列化为 JSON 并下发（SSE 帧：event:<TYPE>\ndata:<json>\n\n）
            String json = JsonUtils.getJsonCodec().toJson(event);
            emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new IllegalStateException("发送 AG-UI 事件失败", e);
        }
    }

}
