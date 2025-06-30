package com.alibaba.cloud.ai.example.copilot.planning;

import com.alibaba.cloud.ai.example.copilot.service.LlmService;
import com.alibaba.cloud.ai.example.copilot.service.SseService;
import com.alibaba.cloud.ai.example.copilot.template.TemplateBasedProjectGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 任务协调器
 * 负责协调任务规划和执行的整个流程
 * 实现分步执行，每次只执行一个步骤，然后根据结果规划下一步
 */
@Service
public class TaskCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(TaskCoordinator.class);

    private final TaskPlanningService planningService;
    private final LlmService llmService;
    private final SseService sseService;
    private final TemplateBasedProjectGenerator templateGenerator;

    // 存储正在执行的任务
    private final ConcurrentMap<String, TaskPlan> activeTasks = new ConcurrentHashMap<>();

    public TaskCoordinator(TaskPlanningService planningService,
                           LlmService llmService,
                          SseService sseService,
                          TemplateBasedProjectGenerator templateGenerator) {
        this.planningService = planningService;
        this.llmService = llmService;
        this.sseService = sseService;
        this.templateGenerator = templateGenerator;
    }

    /**
     * 开始执行任务
     * @param userRequest 用户请求
     * @param taskId 任务ID
     */
    public void startTask(String userRequest, String taskId) {
        logger.info("开始执行任务，任务ID: {}", taskId);
        // 异步执行模板项目生成
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 执行模板项目生成
                String projectInfo = executeTemplateProjectGeneration(userRequest, taskId);

                // 2. 获取下一步执行计划
                TaskPlan continuePlan = planningService.createInitialPlan(projectInfo, taskId);

                // 3. 开始循环执行计划（会自动执行所有步骤直到完成）
                executeStep(taskId, continuePlan);

            } catch (Exception e) {
                logger.error("模板项目生成和继续处理失败，任务ID: {}", taskId, e);
                // 发送错误信息
                sseService.sendTaskUpdate(taskId, createErrorTaskPlan(taskId, e.getMessage()));
            }
        });
    }


    /**
     * 执行单个步骤
     * @param taskPlan 任务计划
     */
    private void executeStep(String taskId,TaskPlan taskPlan) {

        TaskStep step = taskPlan.getStep();


        logger.info("开始执行步骤，任务ID: {}, 步骤: {}", taskId, step.getStepIndex());

        // 构建提示内容
        String promptContent = String.format(
                """
                步骤索引: %d
                执行要求: %s
                工具名称: %s
                返回结果: %s
                """,
                step.getStepIndex(),
                step.getStepRequirement(),
                step.getToolName() != null ? step.getToolName() : "",
                step.getResult() != null ? step.getResult() : ""
        );

        TaskPlanningPromptBuilder promptBuilder = new TaskPlanningPromptBuilder();
        String systemText = promptBuilder.buildTaskPlanningPrompt(taskPlan, step.getStepIndex(), step.getStepRequirement());
        Message userMessage = new UserMessage(promptContent);
        SystemMessage systemMessage = new SystemMessage(systemText);
        Prompt prompt = new Prompt(List.of(systemMessage,userMessage));

        // 更新步骤状态为执行中
        step.setStatus("executing");
        step.setStartTime(System.currentTimeMillis());
        sseService.sendTaskUpdate(taskId, taskPlan);

        // 执行计划
        ChatClient chatClient = llmService.getChatClient();
        Flux<String> content = chatClient.prompt(prompt).stream().content();

        // 实时处理流式响应
        StringBuilder resultBuilder = new StringBuilder();
        AtomicLong lastUpdateTime = new AtomicLong(0);
        final long UPDATE_INTERVAL = 100; // 300ms更新间隔

        content.doOnNext(chunk -> {
            // 每收到一个块就追加到结果中
            resultBuilder.append(chunk);
            logger.info("返回信息：{}", chunk);
            // 实时发送chunk到前端（用于流式显示）
            sseService.sendStepChunkUpdate(taskId, step.getStepIndex(), chunk, false);

            // 节流发送完整任务状态更新
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime.get() >= UPDATE_INTERVAL) {
                lastUpdateTime.set(currentTime);
                step.setResult(resultBuilder.toString());
                sseService.sendTaskUpdate(taskId, taskPlan);
            }
        }).doOnComplete(() -> {
            // 发送步骤完成的chunk标记
            sseService.sendStepChunkUpdate(taskId, step.getStepIndex(), "", true);
            // 执行完当前步骤后，继续执行下一步
            continueNextStep(taskId, taskPlan, resultBuilder.toString());
        }).doOnError(error -> {
            logger.error("流式响应处理出错，任务ID: {}, 步骤: {}, 错误: {}", taskId, step.getStepIndex(), error.getMessage(), error);
            // 设置步骤状态为失败
            step.setStatus("failed");
            step.setEndTime(System.currentTimeMillis());
            step.setResult("执行失败: " + error.getMessage());
            // 发送错误状态更新
            sseService.sendTaskUpdate(taskId, taskPlan);
            sseService.sendStepChunkUpdate(taskId, step.getStepIndex(), "", true);
        }).subscribe();

        // 步骤执行完成
        String finalResult = resultBuilder.toString();
        step.setStatus("completed");
        step.setEndTime(System.currentTimeMillis());
        step.setResult(finalResult);

        // 发送最终状态更新
        sseService.sendTaskUpdate(taskId, taskPlan);

        logger.info("步骤执行完成，任务ID: {}, 步骤: {}", taskId, step.getStepIndex());


    }

    /**
     * 继续执行下一步
     * 根据当前步骤执行结果，获取下一步计划并执行
     * 实现循环执行逻辑：每次执行完成后将执行信息加入上下文再次获取下一步执行计划，直到大模型确认任务已经完成
     * 如果没有下一步，则标记任务完成
     * @param taskId 任务ID
     * @param currentTaskPlan 当前任务计划
     * @param stepResult 当前步骤执行结果
     */
    private void continueNextStep(String taskId, TaskPlan currentTaskPlan, String stepResult) {
        try {
            logger.info("开始获取下一步执行计划，任务ID: {}", taskId);

            // 将当前任务计划存储到活跃任务中
            activeTasks.put(taskId, currentTaskPlan);

            // 调用规划服务获取下一步计划
            TaskPlan nextTaskPlan = planningService.generateNextStep(stepResult,taskId);

            if (nextTaskPlan != null && nextTaskPlan.getStep() != null) {
                // 有下一步，继续执行
                logger.info("获取到下一步计划，任务ID: {}, 下一步索引: {}", taskId, nextTaskPlan.getStep().getStepIndex());

                // 保持任务的基本信息连续性
                nextTaskPlan.setTaskId(taskId);
                if (nextTaskPlan.getTitle() == null || nextTaskPlan.getTitle().isEmpty()) {
                    nextTaskPlan.setTitle(currentTaskPlan.getTitle());
                }
                if (nextTaskPlan.getDescription() == null || nextTaskPlan.getDescription().isEmpty()) {
                    nextTaskPlan.setDescription(currentTaskPlan.getDescription());
                }
                nextTaskPlan.setPlanStatus("processing");

                // 更新活跃任务
                activeTasks.put(taskId, nextTaskPlan);

                // 发送任务更新
                sseService.sendTaskUpdate(taskId, nextTaskPlan);

                // 递归执行下一步
                executeStep(taskId, nextTaskPlan);

            } else {
                // 没有下一步，任务完成
                logger.info("任务执行完成，任务ID: {}", taskId);

                // 标记任务为完成状态
                currentTaskPlan.setPlanStatus("completed");
                activeTasks.put(taskId, currentTaskPlan);

                // 发送任务完成通知
                sseService.sendTaskUpdate(taskId, currentTaskPlan);

                // 发送任务完成的特殊消息
                sseService.sendStepChunkUpdate(taskId, -1, "\n\n## 🎉 任务执行完成！\n\n所有步骤已成功执行，您的项目已准备就绪。", true);
            }

        } catch (Exception e) {
            logger.error("获取下一步计划失败，任务ID: {}", taskId, e);

            // 标记任务为失败状态
            currentTaskPlan.setPlanStatus("failed");
            if (currentTaskPlan.getStep() != null) {
                currentTaskPlan.getStep().setStatus("failed");
                currentTaskPlan.getStep().setResult("获取下一步计划失败: " + e.getMessage());
            }
            activeTasks.put(taskId, currentTaskPlan);

            // 发送错误通知
            sseService.sendTaskUpdate(taskId, currentTaskPlan);
            sseService.sendStepChunkUpdate(taskId, -1, "\n\n❌ 任务执行失败: " + e.getMessage(), true);
        }
    }

    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @return 任务计划
     */
    public TaskPlan getTaskStatus(String taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancelTask(String taskId) {
        TaskPlan taskPlan = activeTasks.get(taskId);
        if (taskPlan != null) {
            taskPlan.setPlanStatus("cancelled");
            sseService.sendTaskUpdate(taskId, taskPlan);
            activeTasks.remove(taskId);
            logger.info("任务已取消，任务ID: {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * 获取所有活跃任务
     * @return 活跃任务映射
     */
    public ConcurrentMap<String, TaskPlan> getActiveTasks() {
        return new ConcurrentHashMap<>(activeTasks);
    }

    /**
     * 清理已完成的任务
     */
    public void cleanupCompletedTasks() {
        activeTasks.entrySet().removeIf(entry -> {
            String status = entry.getValue().getPlanStatus();
            return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
        });
        logger.info("已清理完成的任务，当前活跃任务数: {}", activeTasks.size());
    }

    /**
     * 手动触发下一步规划
     * 用于调试或手动控制执行流程
     * @param taskId 任务ID
     * @param stepResult 步骤执行结果
     * @return 更新后的任务计划
     */
    public void triggerNextStep(String taskId, String stepResult) {
        TaskPlan taskPlan = activeTasks.get(taskId);
        if (taskPlan == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        try {
            TaskPlan updatedPlan = planningService.generateNextStep(stepResult,taskId);
            if(updatedPlan!=null){
                activeTasks.put(taskId, updatedPlan);
                sseService.sendTaskUpdate(taskId, updatedPlan);
                logger.info("手动触发下一步规划完成，任务ID: {}", taskId);
            }

        } catch (Exception e) {
            logger.error("手动触发下一步规划失败，任务ID: {}", taskId, e);
            throw new RuntimeException("触发下一步规划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重新执行失败的步骤
     * @param taskId 任务ID
     * @param stepIndex 步骤索引
     */
    public void  retryFailedStep(String taskId, int stepIndex) {
        TaskPlan taskPlan = activeTasks.get(taskId);
        if (taskPlan == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        TaskStep step = taskPlan.getStep();
        // TODO 重新执行失败的步骤

        if (!"failed".equals(step.getStatus())) {
            throw new IllegalStateException("只能重试失败的步骤");
        }

        // 重置步骤状态
        step.setStatus("pending");
        step.setResult(null);
        step.setStartTime(0);
        step.setEndTime(0);

        logger.info("开始重试失败步骤，任务ID: {}, 步骤: {}", taskId, stepIndex);

    }





    /**
     * 解析用户请求中的项目信息
     */
    private ProjectInfo parseProjectInfo(String userRequest) {
        ProjectInfo info = new ProjectInfo();

        // 使用AI来解析用户请求
//        try {
//            String prompt = String.format("""
//                请分析以下用户请求，提取项目信息：
//
//                用户请求: %s
//
//                请提取以下信息（如果用户没有明确指定，请提供合理的默认值）：
//                1. 项目名称（简短的英文名称，适合作为文件夹名）
//                2. 项目描述（一句话描述项目功能）
//                3. 特殊需求（用户提到的特定功能或要求）
//
//                请按以下格式返回：
//                项目名称: [名称]
//                项目描述: [描述]
//                特殊需求: [需求]
//                """, userRequest);
//
//            String response = llmService.getChatClient().prompt()
//                .user(prompt)
//                .call()
//                .content();
//
//            // 解析AI响应
//            String[] lines = response.split("\n");
//            for (String line : lines) {
//                if (line.startsWith("项目名称:")) {
//                    info.name = line.substring(5).trim();
//                } else if (line.startsWith("项目描述:")) {
//                    info.description = line.substring(5).trim();
//                } else if (line.startsWith("特殊需求:")) {
//                    info.requirements = line.substring(5).trim();
//                }
//            }
//
//        } catch (Exception e) {
//            logger.warn("AI解析项目信息失败，使用默认值", e);
//        }

        // 设置默认值
        if (info.name == null || info.name.isEmpty()) {
            info.name = "ai-chat-app";
        }
        if (info.description == null || info.description.isEmpty()) {
            info.description = "基于Spring AI和Vue3的智能聊天应用" + userRequest;
        }
        if (info.requirements == null || info.requirements.isEmpty()) {
            info.requirements = "基础聊天功能";
        }

        return info;
    }

    /**
     * 执行模板项目生成并返回项目信息
     * @param userRequest 用户请求
     * @param taskId 任务ID
     * @return 项目信息字符串
     */
    private String executeTemplateProjectGeneration(String userRequest, String taskId) throws IOException {
        logger.info("开始执行模板项目生成，任务ID: {}", taskId);

        try {
            // 解析用户请求，提取项目信息
            ProjectInfo projectInfo = parseProjectInfo(userRequest);

            String projectPath = createTemplateProjectTaskPlan(taskId, projectInfo);

            // 收集项目信息
            String projectStructure = getProjectStructure(projectPath);

            return String.format("""
                ## 模板项目生成完成

                **项目名称**: %s
                **项目描述**: %s
                **项目绝对路径**: %s
                **自定义需求**: %s

                ## 项目结构
                %s

                ## 状态
                - 模板项目已复制完成
                - 基础配置已更新
                - 项目已准备好进行进一步开发
                """, projectInfo.name, projectInfo.description, projectPath,
                     projectInfo.requirements, projectStructure);

        } catch (Exception e) {
            logger.error("模板项目生成失败", e);
            throw new IOException("模板项目生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取项目目录结构信息
     * @param projectPath 项目路径
     * @return 格式化的目录结构字符串
     */
    private String getProjectStructure(String projectPath) {
        try {
            StringBuilder structure = new StringBuilder();
            structure.append("```\n");
            structure.append(projectPath).append("/\n");

            Path projectDir = Paths.get(projectPath);
            if (Files.exists(projectDir)) {
                buildDirectoryTree(projectDir, structure, "", 0, 3); // 最多显示3层深度
            } else {
                structure.append("  [项目目录不存在]\n");
            }

            structure.append("```\n");
            return structure.toString();

        } catch (Exception e) {
            logger.warn("获取项目结构失败: {}", projectPath, e);
            return "```\n" + projectPath + "/\n  [无法读取目录结构: " + e.getMessage() + "]\n```\n";
        }
    }

    /**
     * 递归构建目录树结构
     * @param dir 当前目录
     * @param structure 结构字符串构建器
     * @param prefix 前缀字符串
     * @param currentDepth 当前深度
     * @param maxDepth 最大深度
     */
    private void buildDirectoryTree(Path dir, StringBuilder structure, String prefix, int currentDepth, int maxDepth) {
        if (currentDepth >= maxDepth) {
            return;
        }

        try {
            List<Path> entries = Files.list(dir)
                    .filter(path -> !path.getFileName().toString().startsWith(".")) // 过滤隐藏文件
                    .filter(path -> !path.getFileName().toString().equals("target")) // 过滤target目录
                    .filter(path -> !path.getFileName().toString().equals("node_modules")) // 过滤node_modules目录
                    .sorted((a, b) -> {
                        // 目录优先，然后按名称排序
                        boolean aIsDir = Files.isDirectory(a);
                        boolean bIsDir = Files.isDirectory(b);
                        if (aIsDir && !bIsDir) return -1;
                        if (!aIsDir && bIsDir) return 1;
                        return a.getFileName().toString().compareTo(b.getFileName().toString());
                    })
                    .collect(Collectors.toList());

            for (int i = 0; i < entries.size(); i++) {
                Path entry = entries.get(i);
                boolean isLast = (i == entries.size() - 1);
                String fileName = entry.getFileName().toString();

                if (Files.isDirectory(entry)) {
                    structure.append(prefix)
                            .append(isLast ? "└── " : "├── ")
                            .append(fileName)
                            .append("/\n");

                    String newPrefix = prefix + (isLast ? "    " : "│   ");
                    buildDirectoryTree(entry, structure, newPrefix, currentDepth + 1, maxDepth);
                } else {
                    structure.append(prefix)
                            .append(isLast ? "└── " : "├── ")
                            .append(fileName)
                            .append("\n");
                }
            }

        } catch (IOException e) {
            structure.append(prefix).append("  [读取目录失败: ").append(e.getMessage()).append("]\n");
        }
    }


    /**
     * 创建模板项目任务计划
     */
    private String createTemplateProjectTaskPlan(String taskId, ProjectInfo projectInfo) throws IOException {
        TaskPlan taskPlan = new TaskPlan();
        taskPlan.setTaskId(taskId);
        taskPlan.setTitle("基于模板生成项目: " + projectInfo.name);
        taskPlan.setDescription(projectInfo.description);
        taskPlan.setPlanStatus("processing");


        // 步骤1: 复制模板项目
        TaskStep copyTemplateStep = new TaskStep();
        copyTemplateStep.setStepIndex(1);
        copyTemplateStep.setStepRequirement("复制基础模板项目");
        copyTemplateStep.setToolName("template_copier");
        copyTemplateStep.setStatus("pending");
        copyTemplateStep.setStartTime(System.currentTimeMillis());
        taskPlan.setStep(copyTemplateStep);
        sseService.sendTaskUpdate(taskId, taskPlan);

        String projectPath = templateGenerator.copyTemplateProject(projectInfo.name);
        copyTemplateStep.setStatus("completed");
        copyTemplateStep.setEndTime(System.currentTimeMillis());
        copyTemplateStep.setResult("模板项目复制完成，路径: " + projectPath);
        sseService.sendTaskUpdate(taskId, taskPlan);


        // 步骤2: 基础定制
        TaskStep basicCustomizeStep = new TaskStep();
        basicCustomizeStep.setStepIndex(2);
        basicCustomizeStep.setStepRequirement("基础项目信息定制");
        basicCustomizeStep.setToolName("basic_customizer");
        basicCustomizeStep.setStatus("pending");
        basicCustomizeStep.setStartTime(System.currentTimeMillis());
        taskPlan.setStep(basicCustomizeStep);
        sseService.sendTaskUpdate(taskId, taskPlan);

        templateGenerator.customizeProjectBasics(projectPath, projectInfo.name, projectInfo.description, projectInfo.requirements);

        basicCustomizeStep.setStatus("completed");
        basicCustomizeStep.setEndTime(System.currentTimeMillis());
        basicCustomizeStep.setResult("基础项目信息定制完成");
        sseService.sendTaskUpdate(taskId, taskPlan);

        return projectPath;
    }

    /**
     * 创建错误任务计划
     */
    private TaskPlan createErrorTaskPlan(String taskId, String errorMessage) {
        TaskPlan errorPlan = new TaskPlan();
        errorPlan.setTaskId(taskId);
        errorPlan.setTitle("任务执行失败");
        errorPlan.setDescription(errorMessage);
        errorPlan.setPlanStatus("failed");
        return errorPlan;
    }

    /**
     * 项目信息内部类
     */
    private static class ProjectInfo {
        String name;
        String description;
        String requirements;
    }
}
