package com.alibaba.cloud.ai.example.copilot.planning;

import com.alibaba.cloud.ai.example.copilot.service.LlmService;
import com.alibaba.cloud.ai.example.copilot.service.SseService;
import com.alibaba.cloud.ai.example.copilot.template.TemplateBasedProjectGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * @return 任务计划
     */
    public void startTask(String userRequest, String taskId) {
        logger.info("开始执行任务，任务ID: {}", taskId);

        // 检查是否需要使用模板项目生成
        if (shouldUseTemplateGeneration(userRequest)) {
            logger.info("检测到项目生成需求，使用模板项目生成，任务ID: {}", taskId);
            // 执行模板项目生成，收集执行信息后继续处理
            handleTemplateBasedProjectGenerationAndContinue(userRequest, taskId);
        }

    }


    /**
     * 执行单个步骤
     * @param taskPlan 任务计划
     * @param step 步骤
     */
    private void executeStep(String taskId,TaskPlan taskPlan, TaskStep step) {
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
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage = systemPromptTemplate.createMessage();
        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

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
        final long UPDATE_INTERVAL = 300; // 300ms更新间隔

        content.doOnNext(chunk -> {
            // 每收到一个块就追加到结果中
            resultBuilder.append(chunk);
            logger.info("打印返回的块信息：{}", chunk);
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
        }).blockLast();

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
    public TaskPlan triggerNextStep(String taskId, String stepResult) {
        TaskPlan taskPlan = activeTasks.get(taskId);
        if (taskPlan == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        try {
            TaskPlan updatedPlan = planningService.generateNextStep(taskPlan, stepResult);
            activeTasks.put(taskId, updatedPlan);
            sseService.sendTaskUpdate(taskId, updatedPlan);

            logger.info("手动触发下一步规划完成，任务ID: {}", taskId);
            return updatedPlan;

        } catch (Exception e) {
            logger.error("手动触发下一步规划失败，任务ID: {}", taskId, e);
            throw new RuntimeException("触发下一步规划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重新执行失败的步骤
     * @param taskId 任务ID
     * @param stepIndex 步骤索引
     * @return 执行结果
     */
    public void  retryFailedStep(String taskId, int stepIndex) {
        TaskPlan taskPlan = activeTasks.get(taskId);
        if (taskPlan == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        TaskStep step = taskPlan.getSteps().stream()
            .filter(s -> s.getStepIndex() == stepIndex)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + stepIndex));

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
     * 检查是否应该使用模板项目生成
     * @param userRequest 用户请求
     * @return 是否使用模板生成
     */
    private boolean shouldUseTemplateGeneration(String userRequest) {
        String request = userRequest.toLowerCase();

        // 检查关键词，判断是否是项目生成需求
        return request.contains("创建项目") ||
               request.contains("生成项目") ||
               request.contains("新建项目") ||
               request.contains("项目模板") ||
               request.contains("spring boot") && (request.contains("vue") || request.contains("前端")) ||
               request.contains("聊天应用") ||
               request.contains("ai应用") ||
               request.contains("对话系统");
    }

    /**
     * 处理基于模板的项目生成，完成后收集信息并继续执行
     * @param userRequest 用户请求
     * @param taskId 任务ID
     */
    private void handleTemplateBasedProjectGenerationAndContinue(String userRequest, String taskId) {
        // 异步执行模板项目生成
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 执行模板项目生成
                String projectInfo = executeTemplateProjectGeneration(userRequest, taskId);

                // 2. 收集执行信息，更新用户请求
                String enhancedUserRequest = enhanceUserRequestWithProjectInfo(userRequest, projectInfo);

                // 3. 继续执行后续任务处理
                continueTaskProcessingWithEnhancedRequest(enhancedUserRequest, taskId);

            } catch (Exception e) {
                logger.error("模板项目生成和继续处理失败，任务ID: {}", taskId, e);
                // 发送错误信息
                sseService.sendTaskUpdate(taskId, createErrorTaskPlan(taskId, e.getMessage()));
            }
        });
    }


    /**
     * 解析用户请求中的项目信息
     */
    private ProjectInfo parseProjectInfo(String userRequest) {
        ProjectInfo info = new ProjectInfo();

        // 使用AI来解析用户请求
        try {
            String prompt = String.format("""
                请分析以下用户请求，提取项目信息：

                用户请求: %s

                请提取以下信息（如果用户没有明确指定，请提供合理的默认值）：
                1. 项目名称（简短的英文名称，适合作为文件夹名）
                2. 项目描述（一句话描述项目功能）
                3. 特殊需求（用户提到的特定功能或要求）

                请按以下格式返回：
                项目名称: [名称]
                项目描述: [描述]
                特殊需求: [需求]
                """, userRequest);

            String response = llmService.getChatClient().prompt()
                .user(prompt)
                .call()
                .content();

            // 解析AI响应
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith("项目名称:")) {
                    info.name = line.substring(5).trim();
                } else if (line.startsWith("项目描述:")) {
                    info.description = line.substring(5).trim();
                } else if (line.startsWith("特殊需求:")) {
                    info.requirements = line.substring(5).trim();
                }
            }

        } catch (Exception e) {
            logger.warn("AI解析项目信息失败，使用默认值", e);
        }

        // 设置默认值
        if (info.name == null || info.name.isEmpty()) {
            info.name = "ai-chat-app";
        }
        if (info.description == null || info.description.isEmpty()) {
            info.description = "基于Spring AI和Vue3的智能聊天应用";
        }
        if (info.requirements == null || info.requirements.isEmpty()) {
            info.requirements = "基础聊天功能";
        }

        return info;
    }

    /**
     * 执行深度定制 - 简化版本，避免重复执行
     * 只提供项目信息和基本指导，不进行复杂的AI调用
     */
    private String executeDeepCustomization(String projectPath, String userRequest, String taskId) {
        try {
            logger.info("开始简化深度定制，项目路径: {}, 用户需求: {}", projectPath, userRequest);

            // 获取项目结构信息
            String projectStructure = getProjectStructure(projectPath);

            // 构建简化的结果信息
            String result = String.format("""
                ## 项目创建完成！

                **项目路径**: %s
                **用户需求**: %s

                ## 当前项目结构
                %s

                ## 下一步操作建议
                1. 项目已基于模板创建并完成基础配置
                2. 您可以直接在项目目录中进行进一步的代码编辑
                3. 后端代码位于: %s/backend/
                4. 前端代码位于: %s/frontend/
                5. 可以根据需求添加新的功能模块

                ## 项目已就绪
                基础的Spring AI + Vue3聊天应用已经创建完成，您可以开始进行具体的功能开发。
                """, projectPath, userRequest, projectStructure, projectPath, projectPath);

            // 通过SSE发送完成信息
            sseService.sendStepChunkUpdate(taskId, 2, result, true);

            logger.info("简化深度定制完成");
            return result;

        } catch (Exception e) {
            logger.error("简化深度定制失败", e);
            return "项目创建完成，但获取详细信息失败: " + e.getMessage();
        }
    }

    /**
     * 构建深度定制的AI提示词 - 简化版本
     */
    private String buildDeepCustomizationPrompt(String projectPath, String userRequest) {
        // 简化版本，不再使用复杂的AI提示词
        return String.format("""
            项目路径: %s
            用户需求: %s

            项目已创建完成，可以进行进一步的开发。
            """, projectPath, userRequest);
    }

    /**
     * 执行模板项目生成并返回项目信息
     * @param userRequest 用户请求
     * @param taskId 任务ID
     * @return 项目信息字符串
     */
    private String executeTemplateProjectGeneration(String userRequest, String taskId) throws IOException {
        logger.info("开始执行模板项目生成，任务ID: {}", taskId);

        // 解析用户请求，提取项目信息
        ProjectInfo projectInfo = parseProjectInfo(userRequest);

        // 创建任务计划
        TaskPlan taskPlan = createTemplateProjectTaskPlan(taskId, projectInfo);
        activeTasks.put(taskId, taskPlan);
        sseService.sendTaskUpdate(taskId, taskPlan);

        String projectPath = null;

        try {
            // 步骤1: 复制模板项目
            TaskStep copyStep = taskPlan.getSteps().get(0);
            copyStep.setStatus("executing");
            copyStep.setStartTime(System.currentTimeMillis());
            sseService.sendTaskUpdate(taskId, taskPlan);

            projectPath = templateGenerator.copyTemplateProject(projectInfo.name);

            copyStep.setStatus("completed");
            copyStep.setEndTime(System.currentTimeMillis());
            copyStep.setResult("模板项目复制完成，路径: " + projectPath);
            sseService.sendTaskUpdate(taskId, taskPlan);

            // 步骤2: 基础定制
            TaskStep basicStep = taskPlan.getSteps().get(1);
            basicStep.setStatus("executing");
            basicStep.setStartTime(System.currentTimeMillis());
            sseService.sendTaskUpdate(taskId, taskPlan);

            templateGenerator.customizeProjectBasics(projectPath, projectInfo.name, projectInfo.description, projectInfo.requirements);

            basicStep.setStatus("completed");
            basicStep.setEndTime(System.currentTimeMillis());
            basicStep.setResult("基础项目信息定制完成");
            sseService.sendTaskUpdate(taskId, taskPlan);

            // 完成模板项目生成阶段
            taskPlan.setPlanStatus("template_completed");
            sseService.sendTaskUpdate(taskId, taskPlan);

            // 收集项目信息
            String projectStructure = getProjectStructure(projectPath);

            return String.format("""
                ## 模板项目生成完成

                **项目名称**: %s
                **项目描述**: %s
                **项目路径**: %s
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
     * 使用项目信息增强用户请求
     * @param originalRequest 原始用户请求
     * @param projectInfo 项目信息
     * @return 增强后的用户请求
     */
    private String enhanceUserRequestWithProjectInfo(String originalRequest, String projectInfo) {
        return String.format("""
            ## 原始用户需求
            %s

            ## 项目执行情况
            %s

            ## 继续处理指令
            基于上述已完成的模板项目，请继续根据用户的原始需求进行深度定制和功能开发。
            项目基础框架已就绪，现在可以专注于实现具体的业务功能。
            """, originalRequest, projectInfo);
    }

    /**
     * 使用增强的用户请求继续任务处理
     * @param enhancedUserRequest 增强后的用户请求
     * @param taskId 任务ID
     */
    private void continueTaskProcessingWithEnhancedRequest(String enhancedUserRequest, String taskId) {
        logger.info("继续处理增强后的用户请求，任务ID: {}", taskId);

        try {
            // 获取当前任务计划
            TaskPlan currentPlan = activeTasks.get(taskId);
            if (currentPlan == null) {
                logger.warn("任务计划不存在，创建新的计划，任务ID: {}", taskId);
                currentPlan = new TaskPlan();
                currentPlan.setTaskId(taskId);
                currentPlan.setTitle("继续处理用户需求");
                currentPlan.setDescription("基于已完成的模板项目继续处理用户需求");
            }

            // 更新任务状态为继续处理
            currentPlan.setPlanStatus("continuing");
            sseService.sendTaskUpdate(taskId, currentPlan);

            // 使用增强的请求继续生成任务计划
            TaskPlan continuePlan = planningService.createInitialPlan(enhancedUserRequest, taskId);

            logger.info("输出最终Request: {}", enhancedUserRequest);

            // 合并任务计划（保留已完成的步骤，添加新的步骤）
            if (currentPlan.getSteps() != null) {
                for (TaskStep existingStep : currentPlan.getSteps()) {
                    if (!"completed".equals(existingStep.getStatus())) {
                        break; // 只保留已完成的步骤
                    }
                    continuePlan.getSteps().add(0, existingStep); // 添加到开头
                }
            }

            // 更新任务计划
            activeTasks.put(taskId, continuePlan);
            sseService.sendTaskUpdate(taskId, continuePlan);

            // 执行任务子任务
            executeStepsSequentially(taskId, currentPlan);

        } catch (Exception e) {
            logger.error("继续处理任务失败，任务ID: {}", taskId, e);
            // 发送错误信息
            sseService.sendTaskUpdate(taskId, createErrorTaskPlan(taskId, "继续处理失败: " + e.getMessage()));
        }
    }

    /**
     * 顺序执行任务步骤
     * @param taskPlan 任务计划
     */
    private void executeStepsSequentially(String taskId,TaskPlan taskPlan) {
        for (TaskStep step : taskPlan.getSteps()) {
            try {
                // 设置步骤状态为等待执行
                step.setStatus("waiting");
                sseService.sendTaskUpdate(taskId, taskPlan);

                // 短暂延迟以显示等待状态
                Thread.sleep(500);

                // 执行步骤
                executeStep(taskId,taskPlan, step);

            } catch (Exception e) {
                logger.error("步骤执行失败，任务ID: {}, 步骤: {}", taskId, step.getStepIndex(), e);
                step.setStatus("failed");
                step.setEndTime(System.currentTimeMillis());
                step.setResult("执行失败: " + e.getMessage());
                sseService.sendTaskUpdate(taskId, taskPlan);

                // 如果某个步骤失败，标记整个任务失败
                taskPlan.setPlanStatus("failed");
                sseService.sendTaskUpdate(taskId, taskPlan);
                return;
            }
        }

        // 所有步骤完成，标记任务完成
        taskPlan.setPlanStatus("completed");
        sseService.sendTaskUpdate(taskId, taskPlan);
        logger.info("任务执行完成，任务ID: {}", taskId);
    }

    /**
     * 创建模板项目任务计划
     */
    private TaskPlan createTemplateProjectTaskPlan(String taskId, ProjectInfo projectInfo) {
        TaskPlan taskPlan = new TaskPlan();
        taskPlan.setTaskId(taskId);
        taskPlan.setTitle("基于模板生成项目: " + projectInfo.name);
        taskPlan.setDescription("使用Spring AI + Vue3模板生成项目");
        taskPlan.setPlanStatus("processing");

        // 步骤1: 复制模板项目
        TaskStep copyTemplateStep = new TaskStep();
        copyTemplateStep.setStepIndex(1);
        copyTemplateStep.setStepRequirement("复制基础模板项目");
        copyTemplateStep.setToolName("template_copier");
        copyTemplateStep.setStatus("pending");
        taskPlan.addStep(copyTemplateStep);

        // 步骤2: 基础定制
        TaskStep basicCustomizeStep = new TaskStep();
        basicCustomizeStep.setStepIndex(2);
        basicCustomizeStep.setStepRequirement("基础项目信息定制");
        basicCustomizeStep.setToolName("basic_customizer");
        basicCustomizeStep.setStatus("pending");
        taskPlan.addStep(basicCustomizeStep);

        return taskPlan;
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
