package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.agent.CopilotAgentFactory;
import com.alibaba.cloud.ai.copilot.agent.PlanApprovalAgent;
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
import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final int MAX_PLAN_CONTENT_LENGTH = 100_000;
    private static final int MAX_GIT_STATUS_LENGTH = 20_000;
    private static final int MAX_PREVIEW_FILES = 5;
    private static final int MAX_PREVIEW_LINES = 32;
    private static final Duration AGENT_EVENT_IDLE_TIMEOUT = Duration.ofMinutes(2);
    private static final Pattern MARKDOWN_FILE_PATTERN =
            Pattern.compile("`([^`\\n]+(?:\\.[A-Za-z0-9_-]+)(?::\\d+(?:-\\d+)?)?)`");
    private static final Pattern FILE_LINE_RANGE_PATTERN =
            Pattern.compile("^(.*?)(?::(\\d+)(?:-(\\d+))?)?$");

    private final CopilotAgentFactory agentFactory;
    private final SseEventService sseEventService;
    private final ConversationService conversationService;
    private final ChatMessageMapper chatMessageMapper;
    private final AppProperties appProperties;
    private final KnowledgeAvailabilityChecker knowledgeAvailabilityChecker;
    private final Set<String> activePlanExecutions = ConcurrentHashMap.newKeySet();

    @Override
    public void handleBuilderMode(ChatRequest request, SseEmitter emitter) {
        AtomicReference<String> claimedPlanExecution = new AtomicReference<>();
        try {
            Long userIdLong = LoginHelper.getUserId();
            if (userIdLong == null) {
                throw new IllegalStateException("登录状态异常，请重新登录后再试");
            }

            // 1. 获取或创建会话
            String conversationId = request.getConversationId();
            PlanAction planAction = PlanAction.from(request.getPlanAction());
            if (planAction != PlanAction.NONE
                    && (conversationId == null || conversationId.isBlank())) {
                throw new IllegalArgumentException("审批计划时缺少会话ID");
            }
            if (conversationId == null || conversationId.isEmpty()) {
                CreateConversationRequest createRequest = new CreateConversationRequest();
                createRequest.setModelConfigId(request.getModelConfigId());
                conversationId = conversationService.createConversation(userIdLong, createRequest);
                log.info("创建新会话: conversationId={}, userId={}", conversationId, userIdLong);
            } else {
                log.debug("使用现有会话: conversationId={}", conversationId);
            }

            final String finalConversationId = conversationId;
            final String userMessageContent = resolveUserMessageContent(request, planAction);
            final boolean planModeEnabled =
                    Boolean.TRUE.equals(request.getPlanMode()) || planAction != PlanAction.NONE;

            if (planAction == PlanAction.APPROVE
                    && !activePlanExecutions.add(finalConversationId)) {
                sendPlanStatus(
                        emitter,
                        finalConversationId,
                        "RUNNING",
                        "计划审批已经受理，Agent 正在执行，请勿重复点击");
                sseEventService.sendComplete(emitter);
                return;
            }
            if (planAction == PlanAction.APPROVE) {
                claimedPlanExecution.set(finalConversationId);
            }

            // 2. 保存用户消息到数据库
            ChatMessageEntity userMessageEntity = new ChatMessageEntity();
            userMessageEntity.setConversationId(finalConversationId);
            userMessageEntity.setMessageId(UUID.randomUUID().toString());
            userMessageEntity.setRole(planAction == PlanAction.NONE ? "user" : "system");
            userMessageEntity.setContent(userMessageContent);
            userMessageEntity.setCreatedTime(LocalDateTime.now());
            userMessageEntity.setUpdatedTime(LocalDateTime.now());
            chatMessageMapper.insert(userMessageEntity);

            // 3. 增加消息计数
            conversationService.incrementMessageCount(finalConversationId);

            // 4. 发送会话ID到前端
            sseEventService.sendConversationId(emitter, finalConversationId);

            // 5. 构建动态 agent
            HarnessAgent agent = agentFactory.buildAgent(
                    request.getModelConfigId(),
                    finalConversationId,
                    planModeEnabled,
                    planAction != PlanAction.APPROVE);
            Agent runAgent =
                    preparePlanMode(agent, finalConversationId, planModeEnabled, planAction);
            if (planAction == PlanAction.APPROVE) {
                PlanRiskLevel riskLevel =
                        applyPlanRiskPermission(agent, finalConversationId);
                sendPlanStatus(
                        emitter,
                        finalConversationId,
                        "RUNNING",
                        "计划已批准，Agent 正在进入执行阶段；"
                                + riskLevel.executionPolicy());
            }

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
            forwardedProps.put("planMode", planModeEnabled);
            forwardedProps.put("planAction", planAction.name());

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
            AguiAgentAdapter adapter = new AguiAgentAdapter(runAgent, adapterConfig);

            // 8. 订阅 AG-UI 事件流，逐帧编码为 SSE 发往前端；并累积 assistant 文本以便落库
            final AtomicReference<StringBuilder> assistantText = new AtomicReference<>(new StringBuilder());

            Flux<AguiEvent> aguiEvents = adapter.run(runInput)
                    // 防止工具执行线程异常退出却没有向上游传播，导致 SSE 永久挂起。
                    // timeout 会在每次收到事件后重新计时，因此正常的长任务不会被总时长截断。
                    .timeout(AGENT_EVENT_IDLE_TIMEOUT);
            aguiEvents.subscribe(
                    event -> sendAguiEvent(
                            emitter,
                            event,
                            assistantText,
                            planModeEnabled),
                    error -> {
                        log.error("Agent 执行出错: conversationId={}", finalConversationId, error);
                        if (planAction == PlanAction.APPROVE) {
                            sendPlanStatus(
                                    emitter,
                                    finalConversationId,
                                    "FAILED",
                                    resolveAgentErrorMessage(error));
                            activePlanExecutions.remove(finalConversationId);
                            claimedPlanExecution.set(null);
                        }
                        sseEventService.sendRunError(
                                emitter, resolveAgentErrorMessage(error));
                        sseEventService.sendComplete(emitter);
                    },
                    () -> {
                        // 流完成：落库 assistant 文本 + 更新会话标题
                        boolean approvalStillPending =
                                planAction == PlanAction.APPROVE
                                        && agent.isPlanModeActive(null, finalConversationId);
                        String planReviewBlock = approvalStillPending
                                ? ""
                                : sendPlanReviewIfPending(
                                        emitter,
                                        agent,
                                        finalConversationId,
                                        planModeEnabled);
                        saveAssistantMessage(
                                finalConversationId,
                                assistantText.get().toString() + planReviewBlock);
                        updateConversationTitleIfNeeded(finalConversationId, userMessageContent, userIdLong);
                        if (planAction == PlanAction.APPROVE) {
                            sendPlanStatus(
                                    emitter,
                                    finalConversationId,
                                    approvalStillPending ? "FAILED" : "COMPLETED",
                                    approvalStillPending
                                            ? "审批已收到，但 Agent 未能进入执行阶段，请重试"
                                            : "计划已批准，Agent 执行完成");
                            activePlanExecutions.remove(finalConversationId);
                            claimedPlanExecution.set(null);
                        }
                        sseEventService.sendComplete(emitter);
                    }
            );

        } catch (Exception e) {
            log.error("Unexpected error in builder mode", e);
            String claimedConversationId = claimedPlanExecution.getAndSet(null);
            if (claimedConversationId != null) {
                activePlanExecutions.remove(claimedConversationId);
                sendPlanStatus(
                        emitter,
                        claimedConversationId,
                        "FAILED",
                        e.getMessage() == null ? "计划审批执行失败，请重试" : e.getMessage());
            }
            String clientMessage = e instanceof IllegalArgumentException
                    || e instanceof IllegalStateException
                    ? e.getMessage()
                    : "聊天处理失败，请稍后重试";
            sseEventService.sendRunError(emitter, clientMessage);
            sseEventService.sendComplete(emitter);
        }
    }

    private String resolveAgentErrorMessage(Throwable error) {
        if (error instanceof TimeoutException) {
            return "Agent 超过 2 分钟没有返回新事件，任务已停止。请检查模型或工具配置后重试";
        }
        if (error instanceof LinkageError
                || error.getCause() instanceof LinkageError) {
            return "Agent 工具依赖加载失败，请检查服务端依赖版本";
        }
        return "模型或工具调用失败，请检查配置后重试";
    }

    private String resolveUserMessageContent(ChatRequest request, PlanAction planAction) {
        if (planAction == PlanAction.APPROVE) {
            return "计划已批准。请退出计划阶段，按计划创建任务列表并开始执行。";
        }
        if (planAction == PlanAction.REJECT) {
            String feedback = request.getPlanFeedback();
            if (feedback == null || feedback.isBlank()) {
                feedback = "请重新检查方案并补充关键细节。";
            }
            return "PLAN REJECTED: " + feedback.strip()
                    + "\n请继续保持在计划阶段，重新探索并调用 plan_write 更新完整计划，"
                    + "完成后再次调用 plan_exit，不要执行任何修改。";
        }
        if (request.getMessage() == null
                || request.getMessage().getContent() == null
                || request.getMessage().getContent().isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        return request.getMessage().getContent();
    }

    private Agent preparePlanMode(
            HarnessAgent agent,
            String conversationId,
            boolean planModeEnabled,
            PlanAction planAction) {
        if (!planModeEnabled) {
            return agent;
        }

        if (planAction == PlanAction.APPROVE || planAction == PlanAction.REJECT) {
            return preparePlanDecision(agent, conversationId, planAction);
        }

        agent.enterPlanMode(null, conversationId);
        return agent;
    }

    /**
     * 恢复 ASKING 状态的 plan_exit，并将用户决定作为 AgentScope 原生确认结果注入
     * 本次运行。批准会继续执行原工具调用并进入 BUILD，驳回则留在 PLAN 中修订。
     */
    Agent preparePlanDecision(
            HarnessAgent agent,
            String conversationId,
            PlanAction planAction) {
        if (planAction == PlanAction.APPROVE
                && !Files.isRegularFile(agentFactory.resolvePlanFile(conversationId))) {
            throw new IllegalStateException("当前会话没有可执行的计划，请先生成计划");
        }

        if (!agent.isPlanModeActive(null, conversationId)) {
            log.info("恢复已离开 Plan Mode 的审批会话: conversationId={}", conversationId);
            agent.enterPlanMode(null, conversationId);
        }

        AgentState state = agent.getDelegate().getAgentState(null, conversationId);
        ToolUseBlock pendingPlanExit = state.getContext().stream()
                .flatMap(message -> message.getContentBlocks(ToolUseBlock.class).stream())
                .filter(toolCall -> "plan_exit".equals(toolCall.getName()))
                .filter(toolCall -> toolCall.getState() == ToolCallState.ASKING)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException(
                        "当前计划缺少待确认的 plan_exit，请重新生成计划"));
        return new PlanApprovalAgent(
                agent,
                pendingPlanExit,
                planAction == PlanAction.APPROVE);
    }

    private void sendPlanStatus(
            SseEmitter emitter,
            String conversationId,
            String status,
            String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("status", status);
        payload.put("message", message);
        sseEventService.sendSseEvent(emitter, "plan-status", payload);
    }

    private String sendPlanReviewIfPending(
            SseEmitter emitter,
            HarnessAgent agent,
            String conversationId,
            boolean planModeEnabled) {
        if (!planModeEnabled || !agent.isPlanModeActive(null, conversationId)) {
            return "";
        }

        Path planFile = agentFactory.resolvePlanFile(conversationId);
        if (!Files.isRegularFile(planFile)) {
            sseEventService.sendRunError(
                    emitter,
                    "Agent 仍处于计划模式，但没有生成 PLAN.md，请重新提交任务");
            return "";
        }

        try {
            String content = Files.readString(planFile, StandardCharsets.UTF_8);
            if (content.length() > MAX_PLAN_CONTENT_LENGTH) {
                content = content.substring(0, MAX_PLAN_CONTENT_LENGTH)
                        + "\n\n> 计划内容过长，已截断展示。";
            }

            Map<String, Object> payload = new HashMap<>();
            List<String> affectedFiles = extractAffectedFiles(content);
            PlanRiskLevel riskLevel = assessRisk(content);
            Path workspace = agentFactory.resolveConversationWorkspace(conversationId);
            payload.put("conversationId", conversationId);
            payload.put("planFile", planFile.toString());
            payload.put("planContent", content);
            payload.put("affectedFiles", affectedFiles);
            payload.put("filePreviews", buildFilePreviews(workspace, affectedFiles));
            payload.put("gitStatus", collectGitStatus(workspace));
            payload.put("riskLevel", riskLevel.name());
            payload.put("permissionMode", riskLevel.permissionMode().name());
            payload.put("executionPolicy", riskLevel.executionPolicy());
            payload.put("status", "PENDING");
            sseEventService.sendSseEvent(emitter, "plan-review", payload);
            String payloadJson = JsonUtils.getJsonCodec().toJson(payload);
            String encodedPayload = URLEncoder.encode(payloadJson, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return "\n\n```arc-plan\n" + encodedPayload + "\n```\n";
        } catch (IOException e) {
            log.error("读取计划文件失败: conversationId={}, file={}", conversationId, planFile, e);
            sseEventService.sendRunError(emitter, "计划已经生成，但读取失败，请稍后重试");
            return "";
        }
    }

    List<String> extractAffectedFiles(String planContent) {
        Set<String> files = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_FILE_PATTERN.matcher(planContent);
        while (matcher.find()) {
            String candidate = matcher.group(1).strip();
            if (isLikelyWorkspaceFile(candidate)) {
                files.add(candidate);
            }
        }

        for (String line : planContent.split("\\R")) {
            if (!line.startsWith("|")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length < 3) {
                continue;
            }
            String candidate = cells[1].strip().replace("`", "");
            if (!candidate.equalsIgnoreCase("文件")
                    && !candidate.matches("-+")
                    && isLikelyWorkspaceFile(candidate)) {
                files.add(candidate);
            }
        }
        return new ArrayList<>(files);
    }

    private boolean isLikelyWorkspaceFile(String candidate) {
        return candidate != null
                && !candidate.isBlank()
                && !candidate.contains(" ")
                && !candidate.contains("://")
                && candidate.matches(
                        ".+\\.[A-Za-z0-9_-]+(?::\\d+(?:-\\d+)?)?");
    }

    PlanRiskLevel assessRisk(String planContent) {
        String normalized = planContent.lines()
                .filter(line -> !line.stripLeading().startsWith("- [ ]"))
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
        if (containsAny(
                normalized,
                "migration",
                "alter table",
                "drop table",
                "delete_file",
                "删除文件",
                "不可逆")) {
            return PlanRiskLevel.HIGH;
        }
        if (containsAny(
                normalized,
                "http",
                "外部 api",
                "第三方 api",
                "shell",
                "数据库",
                "并发",
                "线程安全")) {
            return PlanRiskLevel.MEDIUM;
        }
        return PlanRiskLevel.LOW;
    }

    private PlanRiskLevel applyPlanRiskPermission(
            HarnessAgent agent, String conversationId) {
        Path planFile = agentFactory.resolvePlanFile(conversationId);
        try {
            String content = Files.readString(planFile, StandardCharsets.UTF_8);
            PlanRiskLevel riskLevel = assessRisk(content);
            agent.setPermissionMode(
                    null,
                    conversationId,
                    riskLevel.permissionMode());
            log.info(
                    "按计划风险设置执行权限: conversationId={}, risk={}, permissionMode={}",
                    conversationId,
                    riskLevel,
                    riskLevel.permissionMode());
            return riskLevel;
        } catch (IOException e) {
            throw new IllegalStateException("读取计划风险失败，请重新生成计划", e);
        }
    }

    String collectGitStatus(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return "会话工作区不存在，无法读取 Git 状态";
        }

        try {
            Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
            Process rootProcess = new ProcessBuilder(
                    "git",
                    "-C",
                    normalizedWorkspace.toString(),
                    "rev-parse",
                    "--show-toplevel")
                    .redirectErrorStream(true)
                    .start();
            if (!rootProcess.waitFor(3, TimeUnit.SECONDS)) {
                rootProcess.destroyForcibly();
                return "Git 状态读取超时";
            }
            String rootOutput = new String(
                    rootProcess.getInputStream().readNBytes(MAX_GIT_STATUS_LENGTH),
                    StandardCharsets.UTF_8).strip();
            if (rootProcess.exitValue() != 0
                    || rootOutput.isBlank()
                    || !Path.of(rootOutput).toAbsolutePath().normalize()
                            .equals(normalizedWorkspace)) {
                return "当前会话工作区不是 Git 仓库";
            }

            Process process = new ProcessBuilder(
                    "git",
                    "-C",
                    normalizedWorkspace.toString(),
                    "status",
                    "--short",
                    "--branch",
                    "--untracked-files=normal")
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] bytes = process.getInputStream().readNBytes(MAX_GIT_STATUS_LENGTH);
                    return new String(bytes, StandardCharsets.UTF_8).strip();
                } catch (IOException e) {
                    return "";
                }
            });

            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "Git 状态读取超时";
            }
            String output = outputFuture.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                return "当前会话工作区不是 Git 仓库";
            }
            return output.isBlank() ? "工作区干净，没有未提交改动" : output;
        } catch (Exception e) {
            log.debug("读取 Git 状态失败: workspace={}, error={}", workspace, e.getMessage());
            return "Git 状态暂不可用";
        }
    }

    List<PlanFilePreview> buildFilePreviews(
            Path workspace, List<String> affectedFiles) {
        if (workspace == null || affectedFiles == null || affectedFiles.isEmpty()) {
            return List.of();
        }

        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        List<PlanFilePreview> previews = new ArrayList<>();
        for (String reference : affectedFiles.stream().limit(MAX_PREVIEW_FILES).toList()) {
            previews.add(buildFilePreview(normalizedWorkspace, reference));
        }
        return previews;
    }

    private PlanFilePreview buildFilePreview(Path workspace, String reference) {
        Matcher matcher = FILE_LINE_RANGE_PATTERN.matcher(reference.strip());
        if (!matcher.matches()) {
            return PlanFilePreview.unavailable(reference, "无法解析文件路径");
        }

        String pathText = matcher.group(1).replace('\\', '/');
        int requestedStart = parsePositiveInt(matcher.group(2), 1);
        int requestedEnd = parsePositiveInt(
                matcher.group(3),
                requestedStart + MAX_PREVIEW_LINES - 1);
        int endLine = Math.min(
                Math.max(requestedStart, requestedEnd),
                requestedStart + MAX_PREVIEW_LINES - 1);

        try {
            Path relativePath = Path.of(pathText);
            if (relativePath.isAbsolute()) {
                return PlanFilePreview.unavailable(reference, "仅展示工作区内的相对路径");
            }
            Path resolved = workspace.resolve(relativePath).normalize();
            if (!resolved.startsWith(workspace)) {
                return PlanFilePreview.unavailable(reference, "路径超出会话工作区");
            }
            if (!Files.isRegularFile(resolved)) {
                return PlanFilePreview.unavailable(reference, "计划引用的文件当前不存在");
            }

            StringBuilder snippet = new StringBuilder();
            int actualEnd = requestedStart - 1;
            try (BufferedReader reader = Files.newBufferedReader(
                    resolved, StandardCharsets.UTF_8)) {
                int lineNumber = 1;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lineNumber >= requestedStart && lineNumber <= endLine) {
                        snippet.append(String.format(
                                Locale.ROOT,
                                "%4d | %s%n",
                                lineNumber,
                                line));
                        actualEnd = lineNumber;
                    }
                    if (lineNumber >= endLine) {
                        break;
                    }
                    lineNumber++;
                }
            }

            if (snippet.isEmpty()) {
                return PlanFilePreview.unavailable(reference, "指定行范围暂无内容");
            }
            return new PlanFilePreview(
                    reference,
                    requestedStart,
                    actualEnd,
                    snippet.toString().stripTrailing(),
                    "AVAILABLE");
        } catch (Exception e) {
            return PlanFilePreview.unavailable(reference, "文件片段读取失败");
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    enum PlanRiskLevel {
        HIGH(
                PermissionMode.DEFAULT,
                "高风险策略：保持默认权限，敏感操作继续请求人工确认"),
        MEDIUM(
                PermissionMode.DONT_ASK,
                "中风险策略：遇到需要额外确认的操作直接拒绝，避免无人值守卡住"),
        LOW(
                PermissionMode.BYPASS,
                "低风险策略：批准后自动执行，显式拒绝规则仍然有效");

        private final PermissionMode permissionMode;
        private final String executionPolicy;

        PlanRiskLevel(
                PermissionMode permissionMode, String executionPolicy) {
            this.permissionMode = permissionMode;
            this.executionPolicy = executionPolicy;
        }

        PermissionMode permissionMode() {
            return permissionMode;
        }

        String executionPolicy() {
            return executionPolicy;
        }
    }

    record PlanFilePreview(
            String path,
            int startLine,
            int endLine,
            String content,
            String status) {

        static PlanFilePreview unavailable(String path, String reason) {
            return new PlanFilePreview(path, 0, 0, reason, "UNAVAILABLE");
        }
    }

    private enum PlanAction {
        NONE,
        APPROVE,
        REJECT;

        private static PlanAction from(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("不支持的计划审批操作: " + value);
            }
        }
    }

    /**
     * 把单个 AG-UI 事件编码为 SSE 帧并发送。
     * 直接用 agentscope JsonUtils 序列化为 JSON 作为 SSE data，事件类型名作为 SSE event 字段，
     * 便于前端按名订阅。累积 assistant 文本用于落库。
     */
    private void sendAguiEvent(
            SseEmitter emitter,
            AguiEvent event,
            AtomicReference<StringBuilder> assistantText,
            boolean planModeEnabled) {
        try {
            if (isExpectedPlanReviewPause(event, planModeEnabled)) {
                log.debug("忽略 Plan Mode 正常审批暂停事件: {}", event);
                return;
            }
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

    boolean isExpectedPlanReviewPause(
            AguiEvent event,
            boolean planModeEnabled) {
        if (!planModeEnabled || !(event instanceof AguiEvent.RunError runError)) {
            return false;
        }
        String message = runError.message();
        return message != null
                && message.contains("paused for human-in-the-loop confirmation")
                && message.contains("plan_exit");
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
