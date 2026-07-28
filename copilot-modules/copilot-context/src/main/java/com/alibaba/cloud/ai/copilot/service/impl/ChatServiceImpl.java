package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
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
    private final AppProperties appProperties;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        try {
            Long userIdLong = LoginHelper.getUserId();
            if (userIdLong == null) {
                throw new IllegalStateException("登录状态异常，请重新登录后再试");
            }

            // 1. 获取或创建会话
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}", conversationId, userIdLong);
            } else {
                log.debug("使用现有会话: conversationId={}", conversationId);
            }

            final String finalConversationId = conversationId;
            final String userMessageContent = request.getMessage().getContent();

            // 2. 保存用户消息到数据库
            ChatMessageEntity userMessageEntity = new ChatMessageEntity();
            userMessageEntity.setConversationId(finalConversationId);
            userMessageEntity.setMessageId(UUID.randomUUID().toString());
            userMessageEntity.setRole("user");
            userMessageEntity.setContent(userMessageContent);
            userMessageEntity.setCreatedTime(LocalDateTime.now());
            userMessageEntity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(userMessageEntity);

            // 3. 增加消息计数
            conversationService.incrementMessageCount(finalConversationId);

            // 4. 发送会话ID到前端
            sseEventService.sendConversationId(emitter, finalConversationId);

            // 5. 构建动态 agent
            HarnessAgent agent = agentFactory.buildAgent(request.getModelConfigId());

            // 6. 构建 AG-UI 输入：threadId=conversationId（供历史 Middleware 定位会话），
            //    forwardedProps 携带 modelConfigId / 偏好开关，供阶段2 的 Middleware 读取
            String runId = UUID.randomUUID().toString();
            String messageId = UUID.randomUUID().toString();
            Map<String, Object> forwardedProps = new java.util.HashMap<>();
            forwardedProps.put("modelConfigId", request.getModelConfigId());
            forwardedProps.put("conversationId", finalConversationId);
            forwardedProps.put("userId", String.valueOf(userIdLong));
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

            // 7. AG-UI 适配器：开启工具调用参数流式（emitToolCallArgs）与思考链（enableReasoning）
            AguiAdapterConfig adapterConfig = AguiAdapterConfig.builder()
                    .emitToolCallArgs(true)
                    .enableReasoning(true)
                    .emitStateEvents(false)
                    .build();
            AguiAgentAdapter adapter = new AguiAgentAdapter(agent, adapterConfig);

            // 8. 订阅 AG-UI 事件流，逐帧编码为 SSE 发往前端；并累积 assistant 文本以便落库
            final AtomicReference<StringBuilder> assistantText = new AtomicReference<>(new StringBuilder());

            Flux<AguiEvent> aguiEvents = adapter.run(runInput);
            aguiEvents.subscribe(
                    event -> sendAguiEvent(emitter, event, assistantText),
                    error -> {
                        log.error("Agent 执行出错: conversationId={}", finalConversationId, error);
                        sseEventService.sendRunError(
                                emitter, "模型调用失败，请检查模型配置或稍后重试");
                        sseEventService.sendComplete(emitter);
                    },
                    () -> {
                        // 流完成：落库 assistant 文本 + 更新会话标题
                        saveAssistantMessage(finalConversationId, assistantText.get().toString());
                        updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                        sseEventService.sendComplete(emitter);
                    }
            );

        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            String clientMessage = e instanceof IllegalArgumentException
                    || e instanceof IllegalStateException
                    ? e.getMessage()
                    : "聊天处理失败，请稍后重试";
            sseEventService.sendRunError(emitter, clientMessage);
            sseEventService.sendComplete(emitter);
        }
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
            log.debug("发送 AG-UI 事件失败: {}", e.getMessage());
        }
    }

    /**
     * 落库 assistant 消息（阶段1 仅文本；tool_calls 链还原在阶段2 SaveMiddleware 接入）。
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
