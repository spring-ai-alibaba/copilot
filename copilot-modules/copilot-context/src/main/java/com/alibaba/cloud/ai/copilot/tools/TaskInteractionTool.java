package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.schema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 任务交互工具
 * 1. 支持任务取消和中断
 * 2. 提供更详细的进度信息
 * 3. 支持任务优先级显示
 * 4. 集成事件总线通知机制
 * 5. 支持任务依赖关系显示
 */
@Component
public class TaskInteractionTool extends BaseTool<TaskInteractionTool.TaskParams> {

    private static final Logger logger = LoggerFactory.getLogger(TaskInteractionTool.class);

    public TaskInteractionTool() {
        super(
            "task_interaction",
            "TaskInteraction",
            "Provide user-friendly feedback about task execution progress. " +
            "This tool gives simple feedback to users about what the system is doing, " +
            "without requiring detailed user attention for each tool call. " +
            "Supports task status updates, progress tracking, cancellation, and dependency management.",
            createSchema()
        );
    }

    private static JsonSchema createSchema() {
        return JsonSchema.object()
            .addProperty("taskDescription", JsonSchema.string(
                "Task description - brief description of what is being executed"
            ))
            .addProperty("status", JsonSchema.string(
                "Task status: started, in_progress, completed, failed, cancelled, paused, resumed"
            ).enumValues("started", "in_progress", "completed", "failed", "cancelled", "paused", "resumed"))
            .addProperty("details", JsonSchema.string(
                "Optional detailed information about the task"
            ))
            .addProperty("progress", JsonSchema.number(
                "Optional progress percentage (0-100)"
            ).minimum(0).maximum(100))
            .addProperty("estimatedDuration", JsonSchema.string(
                "Optional estimated duration (e.g., '2-3 minutes')"
            ))
            .addProperty("priority", JsonSchema.string(
                "Task priority: low, normal, high, urgent"
            ).enumValues("low", "normal", "high", "urgent"))
            .addProperty("taskId", JsonSchema.string(
                "Unique identifier for the task (for tracking and updates)"
            ))
            .addProperty("parentTaskId", JsonSchema.string(
                "Optional parent task ID for dependency tracking"
            ))
            .addProperty("dependencies", JsonSchema.array(
                "List of task IDs that this task depends on",
                JsonSchema.string("Dependency task ID")
            ))
            .addProperty("cancellationReason", JsonSchema.string(
                "Reason for task cancellation (only for cancelled status)"
            ))
            .addProperty("retryCount", JsonSchema.number(
                "Number of retries attempted (for failed tasks)"
            ).minimum(0))
            .required("taskDescription", "status");
    }

    @Override
    public CompletableFuture<ToolResult> execute(TaskParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Task interaction: {} - {} [ID: {}]", params.getStatus(), params.getTaskDescription(), params.getTaskId());

                // 生成用户友好的反馈信息
                String feedback = generateUserFeedback(params);

                // 记录任务状态（可以扩展为事件总线通知）
                logTaskStatus(params);

                // 发送任务状态事件（模拟事件总线）
                sendTaskStatusEvent(params);

                return ToolResult.success(feedback, createTaskStatus(params));

            } catch (Exception e) {
                logger.error("Task interaction failed", e);
                return ToolResult.error("Failed to provide task feedback: " + e.getMessage());
            }
        });
    }

    @Override
    public String getDescription(TaskParams params) {
        String statusEmoji = getStatusEmoji(params.getStatus());
        String statusText = getStatusText(params.getStatus());

        StringBuilder description = new StringBuilder();
        description.append(String.format("%s %s: %s", statusEmoji, statusText, params.getTaskDescription()));

        if (params.getProgress() != null) {
            description.append(String.format(" (%.0f%%)", params.getProgress()));
        }

        if (params.getTaskId() != null) {
            description.append(String.format(" [ID: %s]", params.getTaskId()));
        }

        if (params.getPriority() != null) {
            description.append(String.format(" [%s]", getPriorityDisplay(params.getPriority())));
        }

        return description.toString();
    }

    private String generateUserFeedback(TaskParams params) {
        StringBuilder feedback = new StringBuilder();

        // 添加状态图标和基本反馈
        String statusEmoji = getStatusEmoji(params.getStatus());
        feedback.append(statusEmoji).append(" ");

        switch (params.getStatus()) {
            case "started":
                feedback.append("开始执行: ").append(params.getTaskDescription());
                if (params.getEstimatedDuration() != null) {
                    feedback.append("\n⏱️ 预计需要: ").append(params.getEstimatedDuration());
                }
                if (params.getPriority() != null) {
                    feedback.append(String.format("\n🎯 优先级: %s", getPriorityDisplay(params.getPriority())));
                }
                break;

            case "in_progress":
                feedback.append("正在执行: ").append(params.getTaskDescription());
                if (params.getProgress() != null) {
                    feedback.append(String.format(" (%.0f%% 完成)", params.getProgress()));
                }
                if (params.getDetails() != null) {
                    feedback.append("\n📝 ").append(params.getDetails());
                }
                if (params.getTaskId() != null) {
                    feedback.append(String.format("\n📋 任务ID: %s", params.getTaskId()));
                }
                break;

            case "completed":
                feedback.append("✅ ").append(params.getTaskDescription()).append(" 已完成");
                if (params.getDetails() != null) {
                    feedback.append("\n").append(params.getDetails());
                }
                if (params.getTaskId() != null) {
                    feedback.append(String.format("\n📋 任务ID: %s", params.getTaskId()));
                }
                break;

            case "failed":
                feedback.append("❌ ").append(params.getTaskDescription()).append(" 执行失败");
                if (params.getDetails() != null) {
                    feedback.append("\n错误信息: ").append(params.getDetails());
                }
                if (params.getRetryCount() != null && params.getRetryCount() > 0) {
                    feedback.append(String.format("\n🔄 重试次数: %d", params.getRetryCount()));
                }
                break;

            case "cancelled":
                feedback.append("🚫 ").append(params.getTaskDescription()).append(" 已取消");
                if (params.getCancellationReason() != null) {
                    feedback.append("\n原因: ").append(params.getCancellationReason());
                }
                if (params.getTaskId() != null) {
                    feedback.append(String.format("\n📋 任务ID: %s", params.getTaskId()));
                }
                break;

            case "paused":
                feedback.append("⏸️ ").append(params.getTaskDescription()).append(" 已暂停");
                if (params.getDetails() != null) {
                    feedback.append("\n暂停原因: ").append(params.getDetails());
                }
                break;

            case "resumed":
                feedback.append("▶️ ").append(params.getTaskDescription()).append(" 已恢复");
                if (params.getProgress() != null) {
                    feedback.append(String.format(" (继续从 %.0f%% 进度)", params.getProgress()));
                }
                break;
        }

        // 添加依赖信息
        if (params.getParentTaskId() != null || (params.getDependencies() != null && params.getDependencies().length > 0)) {
            feedback.append("\n\n🔗 依赖关系:");
            if (params.getParentTaskId() != null) {
                feedback.append(String.format("\n   父任务: %s", params.getParentTaskId()));
            }
            if (params.getDependencies() != null && params.getDependencies().length > 0) {
                feedback.append("\n   前置任务: ").append(String.join(", ", params.getDependencies()));
            }
        }

        // 添加鼓励性提示
        switch (params.getStatus()) {
            case "completed":
                feedback.append("\n\n继续下一个任务吧！🚀");
                break;
            case "in_progress":
                feedback.append("\n\n请稍候，正在处理中... ⏳");
                break;
            case "failed":
                feedback.append("\n\n需要重新尝试或调整方案。💡");
                break;
            case "cancelled":
                feedback.append("\n\n如有需要可以重新开始此任务。🔄");
                break;
        }

        return feedback.toString();
    }

    private void logTaskStatus(TaskParams params) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Task Status Update - ")
                .append(params.getTaskDescription())
                .append(" [")
                .append(params.getStatus())
                .append("]");

        if (params.getTaskId() != null) {
            logMessage.append(" [ID: ").append(params.getTaskId()).append("]");
        }

        if (params.getPriority() != null) {
            logMessage.append(" [Priority: ").append(params.getPriority()).append("]");
        }

        logMessage.append(" - Progress: ")
                .append(params.getProgress() != null ? params.getProgress() + "%" : "N/A")
                .append(" - Details: ")
                .append(params.getDetails() != null ? params.getDetails() : "No details");

        logger.info(logMessage.toString());
    }

    private TaskStatus createTaskStatus(TaskParams params) {
        return new TaskStatus(
            params.getTaskDescription(),
            params.getStatus(),
            params.getDetails(),
            params.getProgress(),
            System.currentTimeMillis(),
            params.getTaskId(),
            params.getPriority(),
            params.getRetryCount()
        );
    }

    private String getStatusEmoji(String status) {
        switch (status) {
            case "started": return "🚀";
            case "in_progress": return "🔄";
            case "completed": return "✅";
            case "failed": return "❌";
            case "cancelled": return "🚫";
            case "paused": return "⏸️";
            case "resumed": return "▶️";
            default: return "📋";
        }
    }

    private String getStatusText(String status) {
        switch (status) {
            case "started": return "开始执行";
            case "in_progress": return "正在执行";
            case "completed": return "执行完成";
            case "failed": return "执行失败";
            case "cancelled": return "任务取消";
            case "paused": return "任务暂停";
            case "resumed": return "任务恢复";
            default: return "任务状态";
        }
    }

    private String getPriorityDisplay(String priority) {
        switch (priority) {
            case "urgent": return "🔥 紧急";
            case "high": return "🔴 高";
            case "normal": return "🟡 中";
            case "low": return "🟢 低";
            default: return priority;
        }
    }

    private void sendTaskStatusEvent(TaskParams params) {
        // 模拟事件总线通知机制
        logger.debug("Sending task status event: {} [{}] - ID: {}",
            params.getTaskDescription(),
            params.getStatus(),
            params.getTaskId());

        // 这里可以集成实际的事件总线系统
        // 例如：messageBus.publish(TaskStatusEvent.of(params));
    }

    /**
     * 任务参数
     */
    public static class TaskParams {
        @JsonProperty("taskDescription")
        private String taskDescription;

        @JsonProperty("status")
        private String status;

        @JsonProperty("details")
        private String details;

        @JsonProperty("progress")
        private Double progress;

        @JsonProperty("estimatedDuration")
        private String estimatedDuration;

        @JsonProperty("priority")
        private String priority;

        @JsonProperty("taskId")
        private String taskId;

        @JsonProperty("parentTaskId")
        private String parentTaskId;

        @JsonProperty("dependencies")
        private String[] dependencies;

        @JsonProperty("cancellationReason")
        private String cancellationReason;

        @JsonProperty("retryCount")
        private Integer retryCount;

        // Getters and setters
        public String getTaskDescription() { return taskDescription; }
        public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public Double getProgress() { return progress; }
        public void setProgress(Double progress) { this.progress = progress; }

        public String getEstimatedDuration() { return estimatedDuration; }
        public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }

        public String getParentTaskId() { return parentTaskId; }
        public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }

        public String[] getDependencies() { return dependencies; }
        public void setDependencies(String[] dependencies) { this.dependencies = dependencies; }

        public String getCancellationReason() { return cancellationReason; }
        public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

        @Override
        public String toString() {
            return "TaskParams{" +
                "taskDescription='" + taskDescription + '\'' +
                ", status='" + status + '\'' +
                ", details='" + details + '\'' +
                ", progress=" + progress +
                ", estimatedDuration='" + estimatedDuration + '\'' +
                ", priority='" + priority + '\'' +
                ", taskId='" + taskId + '\'' +
                ", parentTaskId='" + parentTaskId + '\'' +
                ", dependencies=" + java.util.Arrays.toString(dependencies) +
                ", cancellationReason='" + cancellationReason + '\'' +
                ", retryCount=" + retryCount +
                '}';
        }
    }

    /**
     * 任务状态信息
     */
    public static class TaskStatus {
        private final String taskDescription;
        private final String status;
        private final String details;
        private final Double progress;
        private final long timestamp;
        private final String taskId;
        private final String priority;
        private final Integer retryCount;

        public TaskStatus(String taskDescription, String status, String details, Double progress, long timestamp, String taskId, String priority, Integer retryCount) {
            this.taskDescription = taskDescription;
            this.status = status;
            this.details = details;
            this.progress = progress;
            this.timestamp = timestamp;
            this.taskId = taskId;
            this.priority = priority;
            this.retryCount = retryCount;
        }

        // Getters
        public String getTaskDescription() { return taskDescription; }
        public String getStatus() { return status; }
        public String getDetails() { return details; }
        public Double getProgress() { return progress; }
        public long getTimestamp() { return timestamp; }
        public String getTaskId() { return taskId; }
        public String getPriority() { return priority; }
        public Integer getRetryCount() { return retryCount; }
    }
}
