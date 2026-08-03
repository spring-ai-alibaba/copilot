package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.service.PlanWorkspaceStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanWorkspaceStateServiceImpl implements PlanWorkspaceStateService {

    static final String STATUS_EVENT = "plan-workspace-status";
    static final String REVIEW_EVENT = "plan-workspace-review";
    static final String TASKS_EVENT = "plan-workspace-tasks";
    private static final Pattern LEGACY_PLAN_PATTERN =
            Pattern.compile("```arc-plan\\s*\\R([\\s\\S]*?)\\R```", Pattern.MULTILINE);

    private final ChatMessageMapper chatMessageMapper;
    private final AgentStateStore agentStateStore;
    private final ObjectMapper objectMapper;

    @Override
    public PlanWorkspaceDTO getWorkspace(String conversationId) {
        PlanWorkspaceDTO workspace = readEvent(conversationId, STATUS_EVENT, PlanWorkspaceDTO.class);
        if (workspace == null) {
            workspace = new PlanWorkspaceDTO();
            workspace.setConversationId(conversationId);
        }
        workspace.setConversationId(conversationId);
        if (workspace.getStatus() == null || workspace.getStatus().isBlank()) {
            workspace.setStatus("IDLE");
        }

        PlanWorkspaceDTO.PlanReview review = readEvent(
                conversationId, REVIEW_EVENT, PlanWorkspaceDTO.PlanReview.class);
        if (review == null) {
            review = readLegacyReview(conversationId);
        }
        workspace.setReview(review);

        List<PlanWorkspaceDTO.PlanTask> tasks = readTasksEvent(conversationId);
        AgentState state = readAgentState(conversationId);
        if (tasks.isEmpty() && state != null && state.getTasksContext() != null) {
            tasks = state.getTasksContext().getTasks().stream()
                    .map(this::fromAgentTask)
                    .toList();
        }
        workspace.setTasks(new ArrayList<>(tasks));

        boolean pendingApproval = hasPendingPlanExit(state);
        if (pendingApproval) {
            if ("IDLE".equals(workspace.getStatus())
                    || "PENDING_APPROVAL".equals(workspace.getStatus())) {
                workspace.setStatus("PENDING_APPROVAL");
                workspace.setDecisionAllowed(true);
                if (workspace.getMessage() == null || workspace.getMessage().isBlank()) {
                    workspace.setMessage("等待审批");
                }
            } else if ("FAILED".equals(workspace.getStatus())) {
                // 保留失败原因，同时允许用户重新审批仍在 ASKING 的计划。
                workspace.setDecisionAllowed(true);
            }
        } else if (workspace.getReview() != null && "IDLE".equals(workspace.getStatus())) {
            boolean hasStoredState = state != null;
            boolean planActive = hasStoredState
                    && state.getPlanModeContext() != null
                    && state.getPlanModeContext().isPlanActive();
            if (!hasStoredState) {
                workspace.setStatus("PENDING_APPROVAL");
                workspace.setDecisionAllowed(true);
            } else if (planActive) {
                workspace.setStatus("PLANNING");
                workspace.setMessage("正在生成或恢复计划");
                workspace.setDecisionAllowed(false);
            } else {
                workspace.setStatus(allTasksCompleted(tasks) || tasks.isEmpty()
                        ? "COMPLETED"
                        : "EXECUTING");
                workspace.setDecisionAllowed(false);
            }
        } else if ("IDLE".equals(workspace.getStatus())
                && state != null
                && state.getPlanModeContext() != null
                && state.getPlanModeContext().isPlanActive()) {
            workspace.setStatus("PLANNING");
            workspace.setMessage("正在生成计划");
        } else if ("IDLE".equals(workspace.getStatus()) && !tasks.isEmpty()) {
            workspace.setStatus(allTasksCompleted(tasks) ? "COMPLETED" : "EXECUTING");
        }
        return workspace;
    }

    @Override
    public void recordStatus(
            String conversationId,
            String status,
            String message,
            boolean decisionAllowed) {
        PlanWorkspaceDTO snapshot = new PlanWorkspaceDTO();
        snapshot.setConversationId(conversationId);
        snapshot.setStatus(status);
        snapshot.setMessage(message);
        snapshot.setDecisionAllowed(decisionAllowed);
        snapshot.setUpdatedAt(LocalDateTime.now());
        persistEvent(conversationId, STATUS_EVENT, snapshot);
    }

    @Override
    public void recordReview(String conversationId, Map<String, Object> reviewPayload) {
        PlanWorkspaceDTO.PlanReview review = objectMapper.convertValue(
                reviewPayload, PlanWorkspaceDTO.PlanReview.class);
        persistEvent(conversationId, REVIEW_EVENT, review);
        recordTasks(conversationId, List.of());
        recordStatus(conversationId, "PENDING_APPROVAL", "等待审批", true);
    }

    @Override
    public void recordTasks(String conversationId, List<PlanWorkspaceDTO.PlanTask> tasks) {
        List<PlanWorkspaceDTO.PlanTask> snapshot = tasks == null ? List.of() : tasks;
        persistEvent(conversationId, TASKS_EVENT, snapshot);
        if (!snapshot.isEmpty()) {
            boolean completed = allTasksCompleted(snapshot);
            recordStatus(
                    conversationId,
                    completed ? "COMPLETED" : "EXECUTING",
                    completed ? "Todo 已全部完成" : "任务进度已更新",
                    false);
        }
    }

    @Override
    public List<PlanWorkspaceDTO.PlanTask> normalizeTasks(Object source) {
        Object value = parseJsonValue(source);
        if (value instanceof Map<?, ?> map) {
            value = firstPresent(map, "todos", "tasks", "items", "raw", "input", "arguments");
            value = parseJsonValue(value);
            if (value instanceof Map<?, ?> nested) {
                value = firstPresent(nested, "todos", "tasks", "items");
            }
        }
        if (!(value instanceof List<?> items)) {
            return List.of();
        }

        List<PlanWorkspaceDTO.PlanTask> tasks = new ArrayList<>();
        for (Object item : items) {
            Object parsed = parseJsonValue(item);
            if (!(parsed instanceof Map<?, ?> taskMap)) {
                continue;
            }
            String content = firstString(taskMap, "content", "subject", "description", "title");
            if (content == null || content.isBlank()) {
                continue;
            }
            PlanWorkspaceDTO.PlanTask task = new PlanWorkspaceDTO.PlanTask();
            task.setId(firstString(taskMap, "id"));
            task.setContent(content.strip());
            task.setStatus(normalizeTaskStatus(firstString(taskMap, "status", "state")));
            task.setPriority(normalizePriority(firstString(taskMap, "priority")));
            task.setOwner(firstString(taskMap, "owner"));
            Object blockedBy = taskMap.get("blockedBy");
            if (blockedBy instanceof List<?> blocked) {
                task.setBlockedBy(blocked.stream().map(String::valueOf).toList());
            }
            tasks.add(task);
        }
        return tasks;
    }

    private <T> T readEvent(String conversationId, String eventType, Class<T> type) {
        ChatMessageEntity entity = chatMessageMapper.selectLatestWorkspaceEvent(
                conversationId, eventType);
        if (entity == null || entity.getContent() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getContent(), type);
        } catch (Exception e) {
            log.warn("解析计划工作区事件失败: conversationId={}, type={}", conversationId, eventType, e);
            return null;
        }
    }

    private List<PlanWorkspaceDTO.PlanTask> readTasksEvent(String conversationId) {
        ChatMessageEntity entity = chatMessageMapper.selectLatestWorkspaceEvent(
                conversationId, TASKS_EVENT);
        if (entity == null || entity.getContent() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    entity.getContent(), new TypeReference<List<PlanWorkspaceDTO.PlanTask>>() {});
        } catch (Exception e) {
            log.warn("解析计划任务快照失败: conversationId={}", conversationId, e);
            return List.of();
        }
    }

    private PlanWorkspaceDTO.PlanReview readLegacyReview(String conversationId) {
        ChatMessageEntity entity = chatMessageMapper.selectLatestLegacyPlan(conversationId);
        if (entity == null || entity.getContent() == null) {
            return null;
        }
        Matcher matcher = LEGACY_PLAN_PATTERN.matcher(entity.getContent());
        if (!matcher.find()) {
            return null;
        }
        try {
            String json = URLDecoder.decode(matcher.group(1).strip(), StandardCharsets.UTF_8);
            PlanWorkspaceDTO.PlanReview review = objectMapper.readValue(
                    json, PlanWorkspaceDTO.PlanReview.class);
            if (review.getReviewId() == null) {
                review.setReviewId("legacy-" + entity.getMessageId());
            }
            return review;
        } catch (Exception e) {
            log.warn("恢复旧版计划卡失败: conversationId={}", conversationId, e);
            return null;
        }
    }

    private AgentState readAgentState(String conversationId) {
        try {
            return agentStateStore.get(null, conversationId, "agent_state", AgentState.class)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("读取 AgentState 失败: conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }

    private boolean hasPendingPlanExit(AgentState state) {
        return state != null && state.getContext().stream()
                .flatMap(message -> message.getContentBlocks(ToolUseBlock.class).stream())
                .anyMatch(tool -> "plan_exit".equals(tool.getName())
                        && tool.getState() == ToolCallState.ASKING);
    }

    private PlanWorkspaceDTO.PlanTask fromAgentTask(Task source) {
        PlanWorkspaceDTO.PlanTask task = new PlanWorkspaceDTO.PlanTask();
        task.setId(source.getId());
        task.setContent(source.getSubject() == null || source.getSubject().isBlank()
                ? source.getDescription()
                : source.getSubject());
        task.setStatus(normalizeTaskStatus(source.getState() == null
                ? null
                : source.getState().getWire()));
        Object priority = source.getMetadata() == null ? null : source.getMetadata().get("priority");
        task.setPriority(normalizePriority(priority == null ? null : String.valueOf(priority)));
        task.setOwner(source.getOwner());
        task.setBlockedBy(source.getBlockedBy() == null ? List.of() : source.getBlockedBy());
        return task;
    }

    private void persistEvent(String conversationId, String eventType, Object payload) {
        try {
            LocalDateTime now = LocalDateTime.now();
            ChatMessageEntity entity = new ChatMessageEntity();
            entity.setConversationId(conversationId);
            entity.setMessageId(UUID.randomUUID().toString());
            entity.setRole("system");
            entity.setContent(objectMapper.writeValueAsString(payload));
            entity.setMetadata(objectMapper.writeValueAsString(Map.of("type", eventType)));
            entity.setCreatedTime(now);
            entity.setUpdatedTime(now);
            chatMessageMapper.insert(entity);
        } catch (Exception e) {
            log.error("保存计划工作区事件失败: conversationId={}, type={}", conversationId, eventType, e);
        }
    }

    private Object parseJsonValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return map;
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String normalizeTaskStatus(String value) {
        String normalized = value == null ? "pending" : value.strip().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        if (List.of("completed", "complete", "done", "success", "succeeded").contains(normalized)) {
            return "completed";
        }
        if (List.of("in_progress", "progress", "doing", "running", "active").contains(normalized)) {
            return "in_progress";
        }
        return "pending";
    }

    private String normalizePriority(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return List.of("high", "medium", "low").contains(normalized) ? normalized : null;
    }

    private boolean allTasksCompleted(List<PlanWorkspaceDTO.PlanTask> tasks) {
        return !tasks.isEmpty() && tasks.stream().allMatch(task -> "completed".equals(task.getStatus()));
    }
}
