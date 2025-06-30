package com.alibaba.cloud.ai.example.copilot.planning;

import com.alibaba.cloud.ai.example.copilot.service.LlmService;
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


    public TaskPlanningService(LlmService llmService) {
        this.llmService = llmService;
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

            Prompt prompt = new Prompt(List.of(systemMessage,userMessage));

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
     * @param currentStepResult 当前步骤执行结果
     * @param taskId 当前任务计划id
     * @return 更新后的任务计划（包含下一个步骤），如果任务完成返回null
     */
    public TaskPlan generateNextStep(String currentStepResult,String taskId) {
        logger.info("开始生成下一个步骤，任务ID: {}", taskId);
        try {
            // 构建下一步规划提示
            TaskPlan nextPlan = createInitialPlan(currentStepResult,taskId);

            // 检查任务完成标识
            if (nextPlan != null && Boolean.TRUE.equals(nextPlan.getIsCompleted())) {
                logger.info("任务已完成，任务ID: {}", taskId);
                return null;
            }
            return nextPlan;
        } catch (Exception e) {
            logger.error("生成下一个步骤失败，任务ID: {}", taskId, e);
            throw new RuntimeException("生成下一个步骤失败: " + e.getMessage(), e);
        }
    }

}
