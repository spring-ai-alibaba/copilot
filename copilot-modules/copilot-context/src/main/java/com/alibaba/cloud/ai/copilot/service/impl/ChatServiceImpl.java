package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.domain.entity.SkillUsageLogEntity;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeAvailabilityChecker;
import com.alibaba.cloud.ai.copilot.mapper.SkillUsageLogMapper;
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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天服务实现（agentscope 2.0 + AG-UI 协议）。
 *
 * <p>每个请求：动态构建 {@link HarnessAgent} → 用 {@link AguiAgentAdapter} 把
 * agent.streamEvents() 的 AgentEvent 流转成 AG-UI {@link AguiEvent} 流 → 经 SSE 下发。</p>
 *
 * <p>安全：携带已有 conversationId 时先做归属校验，防止劫持他人会话；
 * SSE 断开/超时即取消 agent 流，避免服务端空跑消耗模型 token。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CopilotAgentFactory agentFactory;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final SkillUsageLogMapper skillUsageLogMapper;
    private final AppProperties appProperties;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;

    /** 需要记录使用日志的技能相关工具 */
    private static final Set<String> SKILL_TOOLS = Set.of("load_skill_through_path", "search_skills");

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        try {
            // 1. 获取或创建会话
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());
                Long userIdLong = LoginHelper.getUserId();
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}", conversationId, userIdLong);
            } else {
                // 归属校验：禁止携带他人 conversationId 继续对话（否则可劫持他人会话历史与文件目录）
                conversationService.checkConversationPermission(conversationId, LoginHelper.getUserId());
                log.debug("使用现有会话: conversationId={}", conversationId);
            }

            Long userIdLong = LoginHelper.getUserId();
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

            // 5. 构建动态 agent（沙箱根=会话目录）
            HarnessAgent agent = agentFactory.buildAgent(request.getModelConfigId(), finalConversationId);

            // 6. 构建 AG-UI 输入：threadId=conversationId
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

            // 7. AG-UI 适配器：开启工具调用参数流式与思考链
            AguiAdapterConfig adapterConfig = AguiAdapterConfig.builder()
                    .emitToolCallArgs(true)
                    .enableReasoning(true)
                    .emitStateEvents(false)
                    .build();
            AguiAgentAdapter adapter = new AguiAgentAdapter(agent, adapterConfig);

            // 8. 订阅 AG-UI 事件流；累积 assistant 文本；追踪技能工具调用
            final AtomicReference<StringBuilder> assistantText = new AtomicReference<>(new StringBuilder());
            final Map<String, String> skillCallNames = new ConcurrentHashMap<>();
            final Map<String, StringBuilder> skillCallArgs = new ConcurrentHashMap<>();

            Flux<AguiEvent> aguiEvents = adapter.run(runInput);
            Disposable subscription = aguiEvents.subscribe(
                    event -> {
                        trackSkillUsage(event, skillCallNames, skillCallArgs);
                        sendAguiEvent(emitter, event, assistantText);
                    },
                    error -> {
                        log.error("Agent 执行出错: conversationId={}", finalConversationId, error);
                        persistSkillUsage(finalConversationId, userIdLong, skillCallNames, skillCallArgs);
                        sseEventService.sendComplete(emitter);
                    },
                    () -> {
                        // 流完成：落库 assistant 文本 + 技能使用日志 + 更新会话标题
                        saveAssistantMessage(finalConversationId, assistantText.get().toString());
                        persistSkillUsage(finalConversationId, userIdLong, skillCallNames, skillCallArgs);
                        updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                        sseEventService.sendComplete(emitter);
                    }
            );

            // 客户端断开/超时即取消 agent 流，避免服务端继续空跑消耗模型 token。
            // 正常完成时 onCompletion 也会触发 dispose，对已终止的流是无害幂等操作。
            Runnable cancel = () -> {
                if (!subscription.isDisposed()) {
                    log.info("SSE 连接结束，取消 agent 执行: conversationId={}", finalConversationId);
                    subscription.dispose();
                }
            };
            emitter.onCompletion(cancel);
            emitter.onTimeout(cancel);
            emitter.onError(t -> cancel.run());

        } catch (IllegalArgumentException e) {
            log.warn("聊天请求被拒绝: {}", e.getMessage());
            sseEventService.sendComplete(emitter);
        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            sseEventService.sendComplete(emitter);
        }
    }

    /**
     * 把单个 AG-UI 事件编码为 SSE 帧并发送。
     */
    private void sendAguiEvent(SseEmitter emitter, AguiEvent event, AtomicReference<StringBuilder> assistantText) {
        try {
            if (event instanceof AguiEvent.TextMessageContent tmc) {
                if (tmc.delta() != null) {
                    assistantText.get().append(tmc.delta());
                }
            }
            String json = JsonUtils.getJsonCodec().toJson(event);
            emitter.send(SseEmitter.event()
                    .name(event.getType().name())
                    .data(json, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("发送 AG-UI 事件失败: {}", e.getMessage());
        }
    }

    /**
     * 追踪技能相关工具调用（load_skill_through_path / search_skills）的流式参数。
     */
    private void trackSkillUsage(AguiEvent event,
                                 Map<String, String> names, Map<String, StringBuilder> args) {
        if (event instanceof AguiEvent.ToolCallStart start) {
            if (SKILL_TOOLS.contains(start.toolCallName())) {
                names.put(start.toolCallId(), start.toolCallName());
                args.put(start.toolCallId(), new StringBuilder());
            }
        } else if (event instanceof AguiEvent.ToolCallArgs argEv) {
            StringBuilder sb = args.get(argEv.toolCallId());
            if (sb != null && argEv.delta() != null) {
                sb.append(argEv.delta());
            }
        }
    }

    /**
     * 把本次 run 追踪到的技能使用记录落库（幂等：落库后清空追踪表）。
     */
    private void persistSkillUsage(String conversationId, Long userId,
                                   Map<String, String> names, Map<String, StringBuilder> args) {
        try {
            for (Map.Entry<String, String> e : names.entrySet()) {
                String raw = args.getOrDefault(e.getKey(), new StringBuilder()).toString();
                String skillId = extractJsonField(raw, "skillId");
                if (skillId == null) {
                    // search_skills 记 query 作为标识；解析失败记 unknown
                    skillId = extractJsonField(raw, "query");
                }
                SkillUsageLogEntity row = new SkillUsageLogEntity();
                row.setConversationId(conversationId);
                row.setUserId(userId);
                row.setSkillId(skillId != null ? skillId : "unknown");
                row.setToolName(e.getValue());
                row.setCreatedTime(LocalDateTime.now());
                skillUsageLogMapper.insert(row);
            }
        } catch (Exception ex) {
            log.warn("技能使用日志落库失败: conversationId={}, err={}", conversationId, ex.getMessage());
        } finally {
            names.clear();
            args.clear();
        }
    }

    /** 从（可能不完整的）JSON 参数串里提取字符串字段值 */
    private String extractJsonField(String json, String field) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 落库 assistant 消息（阶段1 仅文本）。
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
