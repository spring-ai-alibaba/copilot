package com.alibaba.cloud.ai.example.copilot.planning;

import com.alibaba.cloud.ai.example.copilot.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务规划服务
 * 负责将用户输入的任务分解为可执行的步骤序列
 * 支持分步规划，每次只返回下一个步骤
 */
@Service
public class TaskPlanningService {

    private static final Logger logger = LoggerFactory.getLogger(TaskPlanningService.class);

    private final LlmService llmService;
    private final TaskPlanningPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public TaskPlanningService(LlmService llmService, TaskPlanningPromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建初始任务计划
     * 只生成第一个步骤，不生成完整计划
     * @param userRequest 用户请求
     * @param taskId 任务ID
     * @return 包含第一个步骤的任务计划
     */
    public TaskPlan createInitialPlan(String userRequest, String taskId) {
        logger.info("开始创建初始任务计划，任务ID: {}", taskId);

        try {
            // 构建提示词
            TaskPlanningPromptBuilder promptBuilder = new TaskPlanningPromptBuilder();
            String systemText = promptBuilder.generatePlanPrompt();

            Message userMessage = new UserMessage(userRequest);
            SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
            Message systemMessage = systemPromptTemplate.createMessage();

            Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

            // 调用LLM生成第一个步骤
            ChatClient chatClient = llmService.getChatClient();

            return chatClient.prompt(prompt).call().entity(TaskPlan.class);

        } catch (Exception e) {
            logger.error("创建初始任务计划失败，任务ID: {}", taskId, e);
            throw new RuntimeException("创建初始任务计划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据当前执行结果生成下一个步骤
     * @param taskPlan 当前任务计划
     * @param currentStepResult 当前步骤执行结果
     * @return 更新后的任务计划（包含下一个步骤）
     */
    public TaskPlan generateNextStep(TaskPlan taskPlan, String currentStepResult) {
        logger.info("开始生成下一个步骤，任务ID: {}", taskPlan.getTaskId());

        try {
            // 构建下一步规划提示
            String prompt = buildNextStepPrompt(taskPlan, currentStepResult);

            // 调用LLM生成下一个步骤
            ChatClient chatClient = llmService.getChatClient();
            String response = chatClient.prompt(prompt).call().content();

            // 解析响应并添加下一个步骤
            TaskStep nextStep = parseNextStepResponse(response, taskPlan.getSteps().size() + 1);

            if (nextStep != null) {
                taskPlan.addStep(nextStep);
                logger.info("成功生成下一个步骤，任务ID: {}, 步骤: {}",
                    taskPlan.getTaskId(), nextStep.getStepRequirement());
            } else {
                // 如果没有下一步，标记任务完成
                taskPlan.setPlanStatus("completed");
                logger.info("任务已完成，无需更多步骤，任务ID: {}", taskPlan.getTaskId());
            }

            return taskPlan;

        } catch (Exception e) {
            logger.error("生成下一个步骤失败，任务ID: {}", taskPlan.getTaskId(), e);
            throw new RuntimeException("生成下一个步骤失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建初始规划提示
     */
    private String buildInitialPlanningPrompt(String userRequest) {
        return String.format("""
            # 任务规划助手
            
            ## 角色定位
            你是一个专业的AI编码助手，负责将用户的编码需求分解为可执行的步骤。
            
            ## 重要约束
            - 你只需要生成第一个步骤，不要生成完整的计划
            - 第一个步骤应该是分析用户需求或准备工作
            - 每个步骤必须明确指定需要使用的工具
            
            ## 可用工具
            - file_operations: 文件操作（创建、读取、写入文件）
            - code_generation: 代码生成
            - project_template: 项目模板操作
            - web_search: 网络搜索
            - analysis: 需求分析
            
            ## 用户需求
            %s
            
            ## 输出格式
            请以JSON格式输出第一个步骤：
            {
                "stepIndex": 1,
                "stepRequirement": "具体的步骤描述",
                "toolName": "需要使用的工具名称"
            }
            
            只输出JSON，不要包含其他内容。
            """, userRequest);
    }

    /**
     * 构建下一步规划提示
     */
    private String buildNextStepPrompt(TaskPlan taskPlan, String currentStepResult) {
        StringBuilder context = new StringBuilder();
        context.append("## 任务背景\n");
        context.append("标题: ").append(taskPlan.getTitle()).append("\n");
        context.append("描述: ").append(taskPlan.getDescription()).append("\n\n");

        context.append("## 已执行步骤\n");
        for (TaskStep step : taskPlan.getSteps()) {
            context.append(String.format("步骤%d: %s (工具: %s)\n",
                step.getStepIndex(), step.getStepRequirement(), step.getToolName()));
            if (step.getResult() != null) {
                context.append("结果: ").append(step.getResult()).append("\n");
            }
        }

        context.append("\n## 当前步骤执行结果\n");
        context.append(currentStepResult).append("\n");

        return String.format("""
            # 任务规划助手
            
            %s
            
            ## 任务
            基于以上执行情况，判断是否需要下一个步骤。如果需要，生成下一个步骤；如果任务已完成，返回null。
            
            ## 可用工具
            - file_operations: 文件操作（创建、读取、写入文件）
            - code_generation: 代码生成
            - project_template: 项目模板操作
            - web_search: 网络搜索
            - analysis: 需求分析
            
            ## 输出格式
            如果需要下一个步骤，以JSON格式输出：
            {
                "stepIndex": %d,
                "stepRequirement": "具体的步骤描述",
                "toolName": "需要使用的工具名称"
            }
            
            如果任务已完成，只输出：null
            
            只输出JSON或null，不要包含其他内容。
            """, context.toString(), taskPlan.getSteps().size() + 1);
    }

    /**
     * 解析初始计划响应
     */
    private TaskPlan parseInitialPlanResponse(String response, String taskId, String userRequest) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response.trim());

            TaskPlan taskPlan = new TaskPlan();
            taskPlan.setTaskId(taskId);
            taskPlan.setTitle(extractTitle(userRequest));
            taskPlan.setDescription(userRequest);
            taskPlan.setPlanStatus("planning");

            TaskStep firstStep = new TaskStep();
            firstStep.setStepIndex(jsonNode.get("stepIndex").asInt());
            firstStep.setStepRequirement(jsonNode.get("stepRequirement").asText());
            firstStep.setToolName(jsonNode.get("toolName").asText());
            firstStep.setStatus("pending");

            taskPlan.addStep(firstStep);

            return taskPlan;

        } catch (Exception e) {
            logger.error("解析初始计划响应失败: {}", response, e);
            throw new RuntimeException("解析初始计划响应失败", e);
        }
    }

    /**
     * 解析下一步响应
     */
    private TaskStep parseNextStepResponse(String response, int stepIndex) {
        try {
            String trimmedResponse = response.trim();
            if ("null".equals(trimmedResponse)) {
                return null; // 任务完成
            }

            JsonNode jsonNode = objectMapper.readTree(trimmedResponse);

            TaskStep nextStep = new TaskStep();
            nextStep.setStepIndex(stepIndex);
            nextStep.setStepRequirement(jsonNode.get("stepRequirement").asText());
            nextStep.setToolName(jsonNode.get("toolName").asText());
            nextStep.setStatus("pending");

            return nextStep;

        } catch (Exception e) {
            logger.error("解析下一步响应失败: {}", response, e);
            throw new RuntimeException("解析下一步响应失败", e);
        }
    }

    /**
     * 从用户请求中提取标题
     */
    private String extractTitle(String userRequest) {
        // 简单的标题提取逻辑，可以后续优化
        if (userRequest.length() > 50) {
            return userRequest.substring(0, 47) + "...";
        }
        return userRequest;
    }
}
