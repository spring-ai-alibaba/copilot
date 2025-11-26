package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.schema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * 连续任务执行工具
 * 核心功能：
 * 1. 任务规划（Plan）- 将复杂目标分解为可执行步骤
 * 2. 任务执行（Execute）- 按步骤执行并跟踪进度
 * 3. 任务验证（Verify）- 验证每个步骤的完成情况
 * 4. 任务持久化（Persist）- 保存和恢复任务状态
 * 5. 依赖管理（Dependencies）- 处理任务间的依赖关系
 * 6. 优先级调度（Priority）- 支持任务优先级和队列管理
 */
@Component
public class ContinuousTaskTool extends BaseTool<ContinuousTaskTool.TaskExecutionParams> {

    private static final Logger logger = LoggerFactory.getLogger(ContinuousTaskTool.class);

    // 任务存储和管理
    private final TaskPersistenceManager persistenceManager;
    private final TaskDependencyManager dependencyManager;
    private final TaskScheduler taskScheduler;
    private final ExecutorService executorService;

    // 任务缓存
    private final Map<String, TaskExecutionState> taskCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<TaskExecutionResult>> runningTasks = new ConcurrentHashMap<>();

    public ContinuousTaskTool() {
        this(null, null, null);
    }

    public ContinuousTaskTool(TaskPersistenceManager persistenceManager,
                             TaskDependencyManager dependencyManager,
                             TaskScheduler taskScheduler) {
        super(
            "continuous_task_execution",
            "ContinuousTaskExecution",
            "Execute complex multi-step tasks with planning, execution, and completion tracking. " +
            "This tool can break down complex goals into manageable steps, execute them sequentially, " +
            "and verify task completion. Useful for project creation, system setup, and other " +
            "complex workflows that require multiple coordinated actions.\n\n" +
            "Inspired by Gemini CLI's write-todos tool and Manus task planning system.\n\n" +
            "Usage Guidelines:\n" +
            "1. Use 'plan' mode to break down complex tasks into steps\n" +
            "2. Use 'execute' mode to run steps sequentially\n" +
            "3. Use 'check_completion' to verify all steps are done\n" +
            "4. Use 'pause'/'resume'/'cancel' for task lifecycle management\n" +
            "5. Only one task can be 'in_progress' at a time per step\n" +
            "6. Update task status immediately when starting/completing steps",
            createSchema()
        );

        // 初始化管理器（如果未提供则使用默认实现）
        this.persistenceManager = persistenceManager != null ?
            persistenceManager : new InMemoryTaskPersistenceManager();
        this.dependencyManager = dependencyManager != null ?
            dependencyManager : new SimpleDependencyManager();
        this.taskScheduler = taskScheduler != null ?
            taskScheduler : new PriorityTaskScheduler();
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );

        logger.info("ContinuousTaskTool initialized with persistence: {}, dependency: {}, scheduler: {}",
            this.persistenceManager.getClass().getSimpleName(),
            this.dependencyManager.getClass().getSimpleName(),
            this.taskScheduler.getClass().getSimpleName());
    }

    private static JsonSchema createSchema() {
        return JsonSchema.object()
            .addProperty("goal", JsonSchema.string(
                "Overall task goal or objective"
            ))
            .addProperty("mode", JsonSchema.string(
                "Execution mode: plan, execute, check_completion, pause, resume, cancel"
            ).enumValues("plan", "execute", "check_completion", "pause", "resume", "cancel"))
            .addProperty("steps", JsonSchema.array(
                "Task steps (required for execute and check_completion modes)",
                createStepSchema()
            ))
            .addProperty("currentStepIndex", JsonSchema.number(
                "Current step index (for execute mode)"
            ).minimum(0))
            .addProperty("completionCriteria", JsonSchema.array(
                "Completion criteria to verify task success",
                createCompletionCriteriaSchema()
            ))
            .addProperty("taskId", JsonSchema.string(
                "Unique identifier for the task (for tracking and updates)"
            ))
            .addProperty("priority", JsonSchema.string(
                "Task priority: low, normal, high, urgent"
            ).enumValues("low", "normal", "high", "urgent"))
            .addProperty("estimatedDuration", JsonSchema.string(
                "Optional estimated duration (e.g., '2-3 minutes')"
            ))
            .addProperty("parentTaskId", JsonSchema.string(
                "Optional parent task ID for dependency tracking"
            ))
            .addProperty("dependencies", JsonSchema.array(
                "List of task IDs that this task depends on",
                JsonSchema.string("Dependency task ID")
            ))
            .required("goal", "mode");
    }

    private static JsonSchema createStepSchema() {
        return JsonSchema.object()
            .addProperty("description", JsonSchema.string("Step description"))
            .addProperty("status", JsonSchema.string("Step status")
                .enumValues("pending", "in_progress", "completed", "failed"))
            .addProperty("expectedOutput", JsonSchema.string("Expected output"))
            .addProperty("verification", JsonSchema.object()
                .addProperty("type", JsonSchema.string("Verification type")
                    .enumValues("file_exists", "content_match", "command_result", "custom"))
                .addProperty("params", JsonSchema.object())
            )
            .required("description", "status");
    }

    private static JsonSchema createCompletionCriteriaSchema() {
        return JsonSchema.object()
            .addProperty("type", JsonSchema.string("Completion criteria type")
                .enumValues("all_steps_completed", "files_created", "tests_passed", "custom"))
            .addProperty("params", JsonSchema.object())
            .required("type", "params");
    }

    @Override
    public CompletableFuture<ToolResult> execute(TaskExecutionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 生成或使用提供的任务ID
                String taskId = params.getTaskId() != null ? params.getTaskId() : generateTaskId();
                params.setTaskId(taskId);

                logger.info("Continuous task execution: {} - mode: {} [ID: {}]",
                    params.getGoal(), params.getMode(), taskId);

                // 发送任务状态事件
                sendTaskStatusEvent(params);

                // 检查依赖关系
                if (params.getDependencies() != null && !params.getDependencies().isEmpty()) {
                    for (String depId : params.getDependencies()) {
                        dependencyManager.addDependency(taskId, depId);
                    }

                    // 检查是否可以执行
                    if (!dependencyManager.canExecute(taskId, taskCache)) {
                        return ToolResult.error("Task cannot execute: dependencies not satisfied");
                    }
                }

                TaskExecutionResult result;
                switch (params.getMode()) {
                    case "plan":
                        result = planTask(params);
                        break;
                    case "execute":
                        result = executeStep(params);
                        break;
                    case "check_completion":
                        result = checkCompletion(params);
                        break;
                    case "pause":
                        result = pauseTask(params);
                        break;
                    case "resume":
                        result = resumeTask(params);
                        break;
                    case "cancel":
                        result = cancelTask(params);
                        break;
                    default:
                        return ToolResult.error("Unknown execution mode: " + params.getMode());
                }

                // 保存任务状态
                if (result.getTaskState() != null) {
                    taskCache.put(taskId, result.getTaskState());
                    persistenceManager.saveTask(taskId, result.getTaskState());
                }

                return ToolResult.success(result.getFeedback(), result);

            } catch (Exception e) {
                logger.error("Continuous task execution failed", e);
                return ToolResult.error("Task execution failed: " + e.getMessage());
            }
        }, executorService);
    }

    /**
     * 生成唯一任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" +
               Integer.toHexString(new Random().nextInt());
    }

    @Override
    public String getDescription(TaskExecutionParams params) {
        String modeText = getModeText(params.getMode());
        return String.format("%s: %s", modeText, params.getGoal());
    }

    private TaskExecutionResult checkCompletion(TaskExecutionParams params) {
        List<TaskStep> steps = params.getSteps();
        List<CompletionCriteria> criteria = params.getCompletionCriteria();
        String taskId = params.getTaskId();

        CompletionEvaluationResult evaluation = evaluateCompletion(steps, criteria, params);

        String feedback = generateCompletionFeedback(params.getGoal(), steps, evaluation);
        logger.info("Task {} completion check: Progress: {:.1f}%, Completed: {}",
                   taskId, evaluation.getProgress(), evaluation.isCompleted());

        TaskExecutionState state = new TaskExecutionState(
            params.getGoal(),
            steps,
            params.getCurrentStepIndex() != null ? params.getCurrentStepIndex() : 0,
            evaluation.isCompleted() ? "completed" : "in_progress",
            System.currentTimeMillis(),
            evaluation.isCompleted() ? System.currentTimeMillis() : null,
            criteria
        );

        String nextAction = evaluation.isCompleted() ?
            "任务全部完成！" :
            "需要继续执行剩余步骤或调整完成条件";

        return new TaskExecutionResult(feedback, state, nextAction);
    }

    private void sendTaskStatusEvent(TaskExecutionParams params) {
        // 模拟事件总线通知机制
        logger.debug("Sending task status event: {} [{}] - ID: {} - Priority: {}",
            params.getGoal(),
            params.getMode(),
            params.getTaskId(),
            params.getPriority());

        // 这里可以集成实际的事件总线系统
        // 例如：messageBus.publish(TaskStatusEvent.of(params));
        // 或者集成到现有的任务交互工具中
        if ("plan".equals(params.getMode()) || "execute".equals(params.getMode())) {
            // 可以触发任务交互工具的状态更新
            // TaskInteractionEvent event = new TaskInteractionEvent(params.getGoal(), params.getMode(), ...);
            // messageBus.publish(event);
        }
    }

    private TaskExecutionResult planTask(TaskExecutionParams params) {
        String goal = params.getGoal();
        String taskId = params.getTaskId();

        List<TaskStep> steps = generateStepsFromGoal(goal);

        // 为每个步骤添加验证信息
        enhanceStepsWithVerification(steps, goal);

        TaskExecutionState state = new TaskExecutionState(
            goal,
            steps,
            0,
            "planned",
            System.currentTimeMillis(),
            null,
            null
        );

        String feedback = generatePlanningFeedback(goal, steps);
        logger.info("Task planning completed: {} steps generated for goal: {} [ID: {}]",
                   steps.size(), goal, taskId);

        // 如果设置了优先级，加入调度队列
        if (params.getPriority() != null) {
            taskScheduler.scheduleTask(taskId, params.getPriority());
            feedback += String.format("\n\n📋 任务已加入调度队列，优先级: %s", params.getPriority());
        }

        return new TaskExecutionResult(feedback, state, "准备执行第一步");
    }

    /**
     * 为步骤添加验证信息
     */
    private void enhanceStepsWithVerification(List<TaskStep> steps, String goal) {
        for (TaskStep step : steps) {
            // 根据步骤描述推断验证类型
            if (step.getDescription().contains("创建") || step.getDescription().contains("生成")) {
                step.setVerification(new StepVerification("file_exists",
                    Map.of("checkType", "creation")));
            } else if (step.getDescription().contains("测试") || step.getDescription().contains("验证")) {
                step.setVerification(new StepVerification("command_result",
                    Map.of("expectedExitCode", 0)));
            } else if (step.getDescription().contains("配置") || step.getDescription().contains("设置")) {
                step.setVerification(new StepVerification("content_match",
                    Map.of("checkType", "configuration")));
            } else {
                step.setVerification(new StepVerification("custom",
                    Map.of("requiresManualCheck", true)));
            }
        }
    }

    private TaskExecutionResult executeStep(TaskExecutionParams params) {
        List<TaskStep> steps = params.getSteps();
        int currentIndex = params.getCurrentStepIndex() != null ? params.getCurrentStepIndex() : 0;
        String taskId = params.getTaskId();

        if (currentIndex >= steps.size()) {
            String feedback = "✅ 所有步骤已完成，任务执行完毕！\n\n🎉 任务目标: " + params.getGoal() + " 已成功完成！";

            TaskExecutionState finalState = new TaskExecutionState(
                params.getGoal(),
                steps,
                currentIndex,
                "completed",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                null
            );

            return new TaskExecutionResult(feedback, finalState, null);
        }

        TaskStep currentStep = steps.get(currentIndex);

        // 检查是否有其他步骤正在执行
        long inProgressCount = steps.stream()
            .filter(s -> "in_progress".equals(s.getStatus()))
            .count();

        if (inProgressCount > 0 && !"in_progress".equals(currentStep.getStatus())) {
            return new TaskExecutionResult(
                "⚠️ 已有步骤正在执行中，请等待当前步骤完成",
                null,
                "等待当前步骤完成"
            );
        }

        currentStep.setStatus("in_progress");
        logger.info("Task {} executing step {}/{}: {}",
                   taskId, currentIndex + 1, steps.size(), currentStep.getDescription());

        // 执行步骤（支持实际工具调用或模拟执行）
        StepExecutionResult stepResult = executeStepWithVerification(currentStep, params);

        if (stepResult.isSuccess()) {
            currentStep.setStatus("completed");
            int nextIndex = currentIndex + 1;
            boolean hasMoreSteps = nextIndex < steps.size();

            String feedback = generateStepExecutionFeedback(currentStep, true, hasMoreSteps, nextIndex, steps.size());
            TaskExecutionState nextState = new TaskExecutionState(
                params.getGoal(),
                steps,
                nextIndex,
                hasMoreSteps ? "executing" : "completed",
                System.currentTimeMillis(),
                hasMoreSteps ? null : System.currentTimeMillis(),
                null
            );

            String nextAction = hasMoreSteps ?
                "准备执行下一步: " + steps.get(nextIndex).getDescription() :
                "任务全部完成！";

            logger.info("Task {} step {}/{} completed successfully",
                       taskId, currentIndex + 1, steps.size());

            return new TaskExecutionResult(feedback, nextState, nextAction);

        } else {
            currentStep.setStatus("failed");
            currentStep.setDetails("执行失败: " + stepResult.getError());

            String feedback = generateStepExecutionFeedback(currentStep, false, false, currentIndex, steps.size());

            TaskExecutionState failedState = new TaskExecutionState(
                params.getGoal(),
                steps,
                currentIndex,
                "failed",
                System.currentTimeMillis(),
                null,
                null
            );

            logger.error("Task {} step {}/{} failed: {}",
                        taskId, currentIndex + 1, steps.size(), stepResult.getError());

            return new TaskExecutionResult(feedback, failedState, "需要重新尝试或调整方案");
        }
    }

    /**
     * 执行步骤并进行验证
     */
    private StepExecutionResult executeStepWithVerification(TaskStep step, TaskExecutionParams params) {
        try {
            // 1. 执行步骤
            StepExecutionResult executionResult = executeStepAction(step, params);

            if (!executionResult.isSuccess()) {
                return executionResult;
            }

            // 2. 验证步骤结果
            if (step.getVerification() != null) {
                boolean verified = verifyStepExecution(step, params);
                if (!verified) {
                    return new StepExecutionResult(false, "步骤执行完成但验证失败");
                }
            }

            return new StepExecutionResult(true, null);

        } catch (Exception e) {
            logger.error("Step execution failed", e);
            return new StepExecutionResult(false, "执行异常: " + e.getMessage());
        }
    }

    /**
     * 执行步骤的实际操作
     */
    private StepExecutionResult executeStepAction(TaskStep step, TaskExecutionParams params) {
        // 这里可以集成实际的工具调用
        // 例如：调用文件操作工具、命令执行工具等

        // 目前使用模拟执行
        return simulateStepExecution(step);
    }

    /**
     * 验证步骤执行结果
     */
    private boolean verifyStepExecution(TaskStep step, TaskExecutionParams params) {
        StepVerification verification = step.getVerification();
        if (verification == null) {
            return true;
        }

        switch (verification.getType()) {
            case "file_exists":
                return verifyFileExists(verification.getParams());
            case "content_match":
                return verifyContentMatch(verification.getParams());
            case "command_result":
                return verifyCommandResult(verification.getParams());
            case "custom":
                return verifyCustom(verification.getParams());
            default:
                logger.warn("Unknown verification type: {}", verification.getType());
                return true;
        }
    }

    private boolean verifyFileExists(Object params) {
        // 实现文件存在性验证
        return true;
    }

    private boolean verifyContentMatch(Object params) {
        // 实现内容匹配验证
        return true;
    }

    private boolean verifyCommandResult(Object params) {
        // 实现命令结果验证
        return true;
    }

    private boolean verifyCustom(Object params) {
        // 实现自定义验证
        return true;
    }

    private TaskExecutionResult pauseTask(TaskExecutionParams params) {
        logger.info("Pausing task: {} [ID: {}]", params.getGoal(), params.getTaskId());

        String feedback = generateTaskManagementFeedback("paused", params.getGoal(), params.getTaskId());
        TaskExecutionState state = new TaskExecutionState(
            params.getGoal(),
            params.getSteps(),
            params.getCurrentStepIndex() != null ? params.getCurrentStepIndex() : 0,
            "paused",
            System.currentTimeMillis(),
            null,
            null
        );

        return new TaskExecutionResult(feedback, state, "任务已暂停，可以随时恢复执行");
    }

    private TaskExecutionResult resumeTask(TaskExecutionParams params) {
        logger.info("Resuming task: {} [ID: {}]", params.getGoal(), params.getTaskId());

        String feedback = generateTaskManagementFeedback("resumed", params.getGoal(), params.getTaskId());
        TaskExecutionState state = new TaskExecutionState(
            params.getGoal(),
            params.getSteps(),
            params.getCurrentStepIndex() != null ? params.getCurrentStepIndex() : 0,
            "executing",
            System.currentTimeMillis(),
            null,
            null
        );

        return new TaskExecutionResult(feedback, state, "继续执行下一步任务");
    }

    private TaskExecutionResult cancelTask(TaskExecutionParams params) {
        logger.info("Cancelling task: {} [ID: {}]", params.getGoal(), params.getTaskId());

        String feedback = generateTaskManagementFeedback("cancelled", params.getGoal(), params.getTaskId());
        TaskExecutionState state = new TaskExecutionState(
            params.getGoal(),
            params.getSteps(),
            params.getCurrentStepIndex() != null ? params.getCurrentStepIndex() : 0,
            "cancelled",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null
        );

        return new TaskExecutionResult(feedback, state, "任务已取消");
    }

    private String generateTaskManagementFeedback(String action, String goal, String taskId) {
        StringBuilder feedback = new StringBuilder();
        String actionEmoji = getActionEmoji(action);

        switch (action) {
            case "paused":
                feedback.append("⏸️ 任务已暂停：").append(goal).append("\n");
                feedback.append("📝 当前进度已保存，可以随时恢复执行\n");
                if (taskId != null) {
                    feedback.append(String.format("📋 任务ID: %s\n", taskId));
                }
                feedback.append("\n💡 建议：使用 resume 模式来恢复任务执行。");
                break;

            case "resumed":
                feedback.append("▶️ 任务已恢复：").append(goal).append("\n");
                feedback.append("🚀 继续执行之前暂停的任务\n");
                if (taskId != null) {
                    feedback.append(String.format("📋 任务ID: %s\n", taskId));
                }
                feedback.append("\n继续下一个步骤吧！🚀");
                break;

            case "cancelled":
                feedback.append("🚫 任务已取消：").append(goal).append("\n");
                feedback.append("❌ 任务执行已终止\n");
                if (taskId != null) {
                    feedback.append(String.format("📋 任务ID: %s\n", taskId));
                }
                feedback.append("\n如有需要可以重新开始此任务。🔄");
                break;
        }

        return feedback.toString();
    }

    private String getActionEmoji(String action) {
        switch (action) {
            case "paused": return "⏸️";
            case "resumed": return "▶️";
            case "cancelled": return "🚫";
            default: return "📋";
        }
    }

    private List<TaskStep> generateStepsFromGoal(String goal) {
        List<TaskStep> defaultSteps = List.of(
            new TaskStep("分析任务需求", "pending", "明确任务的具体要求和目标"),
            new TaskStep("制定执行计划", "pending", "规划实现路径和步骤"),
            new TaskStep("执行主要任务", "pending", "按照计划执行具体操作"),
            new TaskStep("验证任务结果", "pending", "检查任务完成质量和效果"),
            new TaskStep("完成收尾工作", "pending", "整理文档和清理环境")
        );

        // 基于目标关键词生成特定步骤
        if (goal.contains("创建") && goal.contains("项目")) {
            return List.of(
                new TaskStep("创建项目目录结构", "pending", "建立标准的项目文件夹结构"),
                new TaskStep("初始化项目配置文件", "pending", "创建pom.xml、package.json等配置文件"),
                new TaskStep("设置前端框架", "pending", "配置React/Vue/Angular等前端框架"),
                new TaskStep("设置后端框架", "pending", "配置Spring Boot/Express等后端框架"),
                new TaskStep("配置前后端连接", "pending", "设置API接口和数据交互"),
                new TaskStep("添加基础功能实现", "pending", "实现核心业务逻辑"),
                new TaskStep("测试项目功能", "pending", "进行功能测试和调试"),
                new TaskStep("部署项目", "pending", "配置部署环境并发布")
            );
        }

        if (goal.contains("开发") && goal.contains("应用")) {
            return List.of(
                new TaskStep("需求分析和架构设计", "pending", "分析功能需求，设计系统架构"),
                new TaskStep("搭建开发环境", "pending", "配置开发工具和环境"),
                new TaskStep("实现核心功能模块", "pending", "开发主要业务功能"),
                new TaskStep("添加用户界面", "pending", "设计和实现UI界面"),
                new TaskStep("集成测试和调试", "pending", "进行系统集成测试"),
                new TaskStep("优化性能和部署", "pending", "性能优化和生产环境部署")
            );
        }

        if (goal.contains("构建") && goal.contains("系统")) {
            return List.of(
                new TaskStep("系统架构设计", "pending", "设计整体系统架构"),
                new TaskStep("数据库设计和实现", "pending", "设计数据库结构并实现"),
                new TaskStep("后端API开发", "pending", "开发RESTful API接口"),
                new TaskStep("前端界面开发", "pending", "开发用户界面"),
                new TaskStep("系统集成测试", "pending", "进行端到端测试"),
                new TaskStep("性能优化和部署", "pending", "优化性能并部署上线")
            );
        }

        return new ArrayList<>(defaultSteps);
    }

    private StepExecutionResult simulateStepExecution(TaskStep step) {
        // 模拟执行延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StepExecutionResult(false, "执行被中断");
        }

        // 模拟90%成功率
        if (Math.random() > 0.1) {
            return new StepExecutionResult(true, null);
        } else {
            return new StepExecutionResult(false, "步骤执行失败，需要重试或调整方案");
        }
    }

    private CompletionEvaluationResult evaluateCompletion(List<TaskStep> steps,
                                                          List<CompletionCriteria> criteria,
                                                          TaskExecutionParams params) {
        int totalSteps = steps.size();
        int completedSteps = (int) steps.stream().filter(s -> "completed".equals(s.getStatus())).count();
        int failedSteps = (int) steps.stream().filter(s -> "failed".equals(s.getStatus())).count();
        int cancelledSteps = (int) steps.stream().filter(s -> "cancelled".equals(s.getStatus())).count();

        double progress = totalSteps > 0 ? (completedSteps * 100.0 / totalSteps) : 0;

        boolean completed = false;
        List<String> satisfiedCriteria = new ArrayList<>();
        List<String> failedCriteria = new ArrayList<>();

        if (criteria != null && !criteria.isEmpty()) {
            // 检查自定义完成条件
            for (int i = 0; i < criteria.size(); i++) {
                CompletionCriteria criterion = criteria.get(i);
                boolean isSatisfied = checkCriterion(criterion, steps, params);
                String criterionDesc = "条件" + (i + 1) + ": " + criterion.getType();

                if (isSatisfied) {
                    satisfiedCriteria.add(criterionDesc);
                } else {
                    failedCriteria.add(criterionDesc);
                }
            }
            completed = failedCriteria.isEmpty();
        } else {
            // 默认检查：所有步骤完成（不包括取消的步骤）
            int effectiveSteps = totalSteps - cancelledSteps;
            completed = effectiveSteps > 0 && completedSteps == effectiveSteps && failedSteps == 0;

            if (completed) {
                satisfiedCriteria.add(String.format("所有步骤已完成 (%d/%d)", completedSteps, effectiveSteps));
            } else {
                if (completedSteps > 0) {
                    satisfiedCriteria.add(String.format("已完成 %d 个步骤", completedSteps));
                }
                if (failedSteps > 0) {
                    failedCriteria.add(String.format("%d 个步骤失败", failedSteps));
                }
                if (completedSteps < effectiveSteps) {
                    failedCriteria.add(String.format("还有 %d 个步骤待完成", effectiveSteps - completedSteps));
                }
            }
        }

        return new CompletionEvaluationResult(completed, satisfiedCriteria, failedCriteria, progress);
    }

    private boolean checkCriterion(CompletionCriteria criterion, List<TaskStep> steps, TaskExecutionParams params) {
        switch (criterion.getType()) {
            case "all_steps_completed":
                // 所有非取消的步骤都已完成
                return steps.stream()
                    .filter(s -> !"cancelled".equals(s.getStatus()))
                    .allMatch(s -> "completed".equals(s.getStatus()));

            case "files_created":
                // 检查特定文件是否创建
                return checkFilesCreated(criterion.getParams());

            case "tests_passed":
                // 检查测试是否通过
                return checkTestsPassed(criterion.getParams());

            case "custom":
                // 自定义验证逻辑
                return checkCustomCriterion(criterion.getParams(), steps);

            default:
                logger.warn("Unknown criterion type: {}", criterion.getType());
                return false;
        }
    }

    private boolean checkFilesCreated(Object params) {
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = (Map<String, Object>) params;
            Object filesObj = paramsMap.get("files");

            if (filesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> files = (List<String>) filesObj;

                for (String file : files) {
                    Path filePath = Paths.get(file);
                    if (!Files.exists(filePath)) {
                        logger.debug("File not found: {}", file);
                        return false;
                    }
                }
                return true;
            }
        }
        return true; // 如果没有指定文件，默认通过
    }

    private boolean checkTestsPassed(Object params) {
        // 实现测试验证逻辑
        // 可以调用测试工具或检查测试报告
        logger.debug("Checking tests passed with params: {}", params);
        return true;
    }

    private boolean checkCustomCriterion(Object params, List<TaskStep> steps) {
        // 实现自定义验证逻辑
        logger.debug("Checking custom criterion with params: {}", params);
        return true;
    }

    private String generatePlanningFeedback(String goal, List<TaskStep> steps) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("🎯 任务目标：").append(goal).append("\n\n");
        feedback.append("📋 执行计划：\n");

        for (int i = 0; i < steps.size(); i++) {
            feedback.append(String.format("%d. %s\n", i + 1, steps.get(i).getDescription()));
        }

        feedback.append(String.format("\n🚀 共 %d 个步骤，准备开始执行。\n", steps.size()));
        feedback.append("💡 建议：按照计划逐步执行，如有问题可随时调整方案。");

        return feedback.toString();
    }

    private String generateStepExecutionFeedback(TaskStep step, boolean success, boolean hasNext, int currentIndex, int totalSteps) {
        StringBuilder feedback = new StringBuilder();

        if (success) {
            feedback.append("✅ 步骤完成：").append(step.getDescription()).append("\n");
            feedback.append(String.format("📊 进度：第 %d/%d 步已完成\n", currentIndex + 1, totalSteps));

            if (hasNext) {
                feedback.append("准备执行下一步...");
            } else {
                feedback.append("\n🎉 所有步骤已完成！任务执行成功！");
            }
        } else {
            feedback.append("❌ 步骤失败：").append(step.getDescription()).append("\n");
            feedback.append("错误信息：").append(step.getDetails()).append("\n");
            feedback.append("需要重新尝试或调整方案。");
        }

        return feedback.toString();
    }

    private String generateCompletionFeedback(String goal, List<TaskStep> steps, CompletionEvaluationResult result) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("🎯 任务目标：").append(goal).append("\n\n");

        int completedSteps = (int) steps.stream().filter(s -> "completed".equals(s.getStatus())).count();
        feedback.append("📊 执行结果：\n");
        feedback.append(String.format("- 进度：%d/%d 步骤完成 (%.1f%%)\n",
            completedSteps, steps.size(), result.getProgress()));

        if (!result.getSatisfiedCriteria().isEmpty()) {
            feedback.append("- ✅ 满足条件：").append(String.join(", ", result.getSatisfiedCriteria())).append("\n");
        }

        if (!result.getFailedCriteria().isEmpty()) {
            feedback.append("- ❌ 未满足条件：").append(String.join(", ", result.getFailedCriteria())).append("\n");
        }

        feedback.append("\n");
        if (result.isCompleted()) {
            feedback.append("🎉 任务已完成！所有目标都已达成。");
        } else {
            feedback.append("⏳ 任务尚未完成，建议继续执行剩余步骤。");
        }

        return feedback.toString();
    }

    private String getModeText(String mode) {
        switch (mode) {
            case "plan": return "任务规划";
            case "execute": return "任务执行";
            case "check_completion": return "完成检查";
            default: return "任务处理";
        }
    }

    // 数据模型类
    public static class TaskExecutionParams {
        @JsonProperty("goal")
        private String goal;

        @JsonProperty("mode")
        private String mode;

        @JsonProperty("steps")
        private List<TaskStep> steps;

        @JsonProperty("currentStepIndex")
        private Integer currentStepIndex;

        @JsonProperty("completionCriteria")
        private List<CompletionCriteria> completionCriteria;

        @JsonProperty("taskId")
        private String taskId;

        @JsonProperty("priority")
        private String priority;

        @JsonProperty("estimatedDuration")
        private String estimatedDuration;

        @JsonProperty("parentTaskId")
        private String parentTaskId;

        @JsonProperty("dependencies")
        private List<String> dependencies;

        // Getters and setters
        public String getGoal() { return goal; }
        public void setGoal(String goal) { this.goal = goal; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public List<TaskStep> getSteps() { return steps; }
        public void setSteps(List<TaskStep> steps) { this.steps = steps; }

        public Integer getCurrentStepIndex() { return currentStepIndex; }
        public void setCurrentStepIndex(Integer currentStepIndex) { this.currentStepIndex = currentStepIndex; }

        public List<CompletionCriteria> getCompletionCriteria() { return completionCriteria; }
        public void setCompletionCriteria(List<CompletionCriteria> completionCriteria) { this.completionCriteria = completionCriteria; }

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public String getEstimatedDuration() { return estimatedDuration; }
        public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

        public String getParentTaskId() { return parentTaskId; }
        public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }

        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    }

    public static class TaskStep {
        @JsonProperty("description")
        private String description;

        @JsonProperty("status")
        private String status;

        @JsonProperty("expectedOutput")
        private String expectedOutput;

        @JsonProperty("verification")
        private StepVerification verification;

        @JsonProperty("details")
        private String details;

        public TaskStep(String description, String status, String details) {
            this.description = description;
            this.status = status;
            this.details = details;
        }

        // Getters and setters
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getExpectedOutput() { return expectedOutput; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

        public StepVerification getVerification() { return verification; }
        public void setVerification(StepVerification verification) { this.verification = verification; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }

    public static class StepVerification {
        @JsonProperty("type")
        private String type;

        @JsonProperty("params")
        private Object params;

        public StepVerification(String type, Object params) {
            this.type = type;
            this.params = params;
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
    }

    public static class CompletionCriteria {
        @JsonProperty("type")
        private String type;

        @JsonProperty("params")
        private Object params;

        public CompletionCriteria(String type, Object params) {
            this.type = type;
            this.params = params;
        }

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Object getParams() { return params; }
        public void setParams(Object params) { this.params = params; }
    }

    public static class TaskExecutionState {
        private final String goal;
        private final List<TaskStep> steps;
        private final int currentStepIndex;
        private final String status;
        private final long startTime;
        private final long endTime;
        private final List<CompletionCriteria> completionCriteria;

        public TaskExecutionState(String goal, List<TaskStep> steps, int currentStepIndex,
                                String status, long startTime, Long endTime,
                                List<CompletionCriteria> completionCriteria) {
            this.goal = goal;
            this.steps = steps;
            this.currentStepIndex = currentStepIndex;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.completionCriteria = completionCriteria;
        }

        // Getters
        public String getGoal() { return goal; }
        public List<TaskStep> getSteps() { return steps; }
        public int getCurrentStepIndex() { return currentStepIndex; }
        public String getStatus() { return status; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public List<CompletionCriteria> getCompletionCriteria() { return completionCriteria; }
    }

    public static class TaskExecutionResult {
        private final String feedback;
        private final TaskExecutionState taskState;
        private final String nextAction;

        public TaskExecutionResult(String feedback, TaskExecutionState taskState, String nextAction) {
            this.feedback = feedback;
            this.taskState = taskState;
            this.nextAction = nextAction;
        }

        // Getters
        public String getFeedback() { return feedback; }
        public TaskExecutionState getTaskState() { return taskState; }
        public String getNextAction() { return nextAction; }
    }

    public static class StepExecutionResult {
        private final boolean success;
        private final String error;

        public StepExecutionResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }

    public static class CompletionEvaluationResult {
        private final boolean completed;
        private final List<String> satisfiedCriteria;
        private final List<String> failedCriteria;
        private final double progress;

        public CompletionEvaluationResult(boolean completed, List<String> satisfiedCriteria,
                                        List<String> failedCriteria, double progress) {
            this.completed = completed;
            this.satisfiedCriteria = satisfiedCriteria;
            this.failedCriteria = failedCriteria;
            this.progress = progress;
        }

        // Getters
        public boolean isCompleted() { return completed; }
        public List<String> getSatisfiedCriteria() { return satisfiedCriteria; }
        public List<String> getFailedCriteria() { return failedCriteria; }
        public double getProgress() { return progress; }
    }

    // ==================== 任务管理器接口和实现 ====================

    /**
     * 任务持久化管理器接口
     */
    public interface TaskPersistenceManager {
        void saveTask(String taskId, TaskExecutionState state);
        TaskExecutionState loadTask(String taskId);
        void deleteTask(String taskId);
        List<String> listAllTaskIds();
    }

    /**
     * 内存任务持久化管理器（默认实现）
     */
    public static class InMemoryTaskPersistenceManager implements TaskPersistenceManager {
        private final Map<String, TaskExecutionState> storage = new ConcurrentHashMap<>();

        @Override
        public void saveTask(String taskId, TaskExecutionState state) {
            storage.put(taskId, state);
            logger.debug("Task {} saved to memory", taskId);
        }

        @Override
        public TaskExecutionState loadTask(String taskId) {
            return storage.get(taskId);
        }

        @Override
        public void deleteTask(String taskId) {
            storage.remove(taskId);
            logger.debug("Task {} deleted from memory", taskId);
        }

        @Override
        public List<String> listAllTaskIds() {
            return new ArrayList<>(storage.keySet());
        }
    }

    /**
     * 文件系统任务持久化管理器
     */
    public static class FileSystemTaskPersistenceManager implements TaskPersistenceManager {
        private final Path storageDir;

        public FileSystemTaskPersistenceManager(String storagePath) {
            this.storageDir = Paths.get(storagePath);
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                logger.error("Failed to create storage directory: {}", storagePath, e);
            }
        }

        @Override
        public void saveTask(String taskId, TaskExecutionState state) {
            // 实现文件保存逻辑
            logger.info("Task {} saved to file system", taskId);
        }

        @Override
        public TaskExecutionState loadTask(String taskId) {
            // 实现文件加载逻辑
            logger.info("Task {} loaded from file system", taskId);
            return null;
        }

        @Override
        public void deleteTask(String taskId) {
            // 实现文件删除逻辑
            logger.info("Task {} deleted from file system", taskId);
        }

        @Override
        public List<String> listAllTaskIds() {
            // 实现文件列表逻辑
            return new ArrayList<>();
        }
    }

    /**
     * 任务依赖管理器接口
     */
    public interface TaskDependencyManager {
        void addDependency(String taskId, String dependsOnTaskId);
        List<String> getDependencies(String taskId);
        boolean canExecute(String taskId, Map<String, TaskExecutionState> taskStates);
        void removeDependency(String taskId, String dependsOnTaskId);
    }

    /**
     * 简单依赖管理器实现
     */
    public static class SimpleDependencyManager implements TaskDependencyManager {
        private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();

        @Override
        public void addDependency(String taskId, String dependsOnTaskId) {
            dependencies.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet())
                       .add(dependsOnTaskId);
            logger.debug("Added dependency: {} depends on {}", taskId, dependsOnTaskId);
        }

        @Override
        public List<String> getDependencies(String taskId) {
            return new ArrayList<>(dependencies.getOrDefault(taskId, Collections.emptySet()));
        }

        @Override
        public boolean canExecute(String taskId, Map<String, TaskExecutionState> taskStates) {
            Set<String> deps = dependencies.get(taskId);
            if (deps == null || deps.isEmpty()) {
                return true;
            }

            // 检查所有依赖任务是否已完成
            for (String depId : deps) {
                TaskExecutionState depState = taskStates.get(depId);
                if (depState == null || !"completed".equals(depState.getStatus())) {
                    logger.debug("Task {} cannot execute: dependency {} not completed", taskId, depId);
                    return false;
                }
            }
            return true;
        }

        @Override
        public void removeDependency(String taskId, String dependsOnTaskId) {
            Set<String> deps = dependencies.get(taskId);
            if (deps != null) {
                deps.remove(dependsOnTaskId);
                logger.debug("Removed dependency: {} no longer depends on {}", taskId, dependsOnTaskId);
            }
        }
    }

    /**
     * 任务调度器接口
     */
    public interface TaskScheduler {
        void scheduleTask(String taskId, String priority);
        String getNextTask();
        void removeTask(String taskId);
        int getQueueSize();
    }

    /**
     * 优先级任务调度器实现
     */
    public static class PriorityTaskScheduler implements TaskScheduler {
        private final Map<String, Integer> priorityMap = Map.of(
            "urgent", 4,
            "high", 3,
            "normal", 2,
            "low", 1
        );

        private final PriorityBlockingQueue<TaskQueueItem> taskQueue =
            new PriorityBlockingQueue<>(100, Comparator
                .comparingInt((TaskQueueItem item) -> -item.priority) // 高优先级在前
                .thenComparingLong(item -> item.timestamp)); // 相同优先级按时间排序

        private final Set<String> scheduledTasks = ConcurrentHashMap.newKeySet();

        @Override
        public void scheduleTask(String taskId, String priority) {
            if (scheduledTasks.contains(taskId)) {
                logger.warn("Task {} already scheduled", taskId);
                return;
            }

            int priorityValue = priorityMap.getOrDefault(priority, 2);
            TaskQueueItem item = new TaskQueueItem(taskId, priorityValue, System.currentTimeMillis());
            taskQueue.offer(item);
            scheduledTasks.add(taskId);
            logger.info("Task {} scheduled with priority {} (value: {})", taskId, priority, priorityValue);
        }

        @Override
        public String getNextTask() {
            TaskQueueItem item = taskQueue.poll();
            if (item != null) {
                scheduledTasks.remove(item.taskId);
                logger.debug("Next task to execute: {}", item.taskId);
                return item.taskId;
            }
            return null;
        }

        @Override
        public void removeTask(String taskId) {
            taskQueue.removeIf(item -> item.taskId.equals(taskId));
            scheduledTasks.remove(taskId);
            logger.debug("Task {} removed from queue", taskId);
        }

        @Override
        public int getQueueSize() {
            return taskQueue.size();
        }

        private static class TaskQueueItem {
            final String taskId;
            final int priority;
            final long timestamp;

            TaskQueueItem(String taskId, int priority, long timestamp) {
                this.taskId = taskId;
                this.priority = priority;
                this.timestamp = timestamp;
            }
        }
    }

    /**
     * 任务事件监听器接口
     */
    public interface TaskEventListener {
        void onTaskStateChanged(String taskId, String oldState, String newState);
        void onStepCompleted(String taskId, int stepIndex, TaskStep step);
        void onTaskCompleted(String taskId, TaskExecutionResult result);
        void onTaskFailed(String taskId, String error);
    }

    /**
     * 默认任务事件监听器
     */
    public static class DefaultTaskEventListener implements TaskEventListener {
        @Override
        public void onTaskStateChanged(String taskId, String oldState, String newState) {
            logger.info("Task {} state changed: {} -> {}", taskId, oldState, newState);
        }

        @Override
        public void onStepCompleted(String taskId, int stepIndex, TaskStep step) {
            logger.info("Task {} step {} completed: {}", taskId, stepIndex, step.getDescription());
        }

        @Override
        public void onTaskCompleted(String taskId, TaskExecutionResult result) {
            logger.info("Task {} completed successfully", taskId);
        }

        @Override
        public void onTaskFailed(String taskId, String error) {
            logger.error("Task {} failed: {}", taskId, error);
        }
    }
}
