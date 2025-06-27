package com.alibaba.cloud.ai.example.copilot.planning;

import org.springframework.stereotype.Component;


/**
 * 任务计划提示词构建器
 * 负责构建用于任务拆分的提示词，引导大模型将复杂任务拆分为可执行的步骤列表
 */
@Component
public class TaskPlanningPromptBuilder {


    /**
     * 添加思考提示到消息列表中，构建智能体的思考链
     * <p>
     * 实现要求： 1. 根据当前上下文和状态生成合适的系统提示词 2. 提示词应该指导智能体如何思考和决策 3. 可以递归地构建提示链，形成层次化的思考过程 4.
     * 返回添加的系统提示消息对象
     * <p>
     * 子类实现参考： 1. ReActAgent: 实现基础的思考-行动循环提示 2. ToolCallAgent: 添加工具选择和执行相关的提示
     *
     * @param taskPlan 任务计划对象，包含全局计划状态和上下文信息
     * @param currentStepIndex 当前执行的步骤索引
     * @param stepText 当前步骤的文本描述
     * @return 添加的系统提示消息对象
     */
    public String buildTaskPlanningPrompt(TaskPlan taskPlan, int currentStepIndex, String stepText) {
        // 获取操作系统信息
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");

        // 获取当前日期时间
        String currentDateTime = java.time.LocalDate.now().toString();

        // 工具使用指南
        String toolUsageGuide = """
                1. 使用工具调用前，必须分析当前任务需求并选择最合适的工具
                2. 每次工具调用必须提供清晰的理由和预期结果
                3. 如遇工具调用错误，尝试不同参数或替代工具
                4. 保持工具调用的简洁性，避免不必要的调用
                """;

        // 步骤执行指南
        String stepExecutionGuide = """
                1. 严格按照当前步骤要求执行，不要超出范围
                2. 在执行前，分析步骤与全局目标的关系
                3. 简要总结已完成步骤的成果和当前进度
                """;

        // 错误处理指南
        String errorHandlingGuide = """
                1. 遇到错误时，先分析错误原因和类型
                2. 对于工具错误，检查参数和使用方法
                3. 对于逻辑错误，重新评估当前步骤要求
                4. 如无法解决，明确说明问题并请求进一步指导
                """;

        // 获取全局计划状态和上下文信息
        String planStatus = taskPlan.getPlanStatus() != null ? taskPlan.getPlanStatus() : "";
        String extraParams = taskPlan.getExtraParams() != null ? taskPlan.getExtraParams() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("# 系统环境信息\n");
        sb.append("操作系统: ").append(osName).append(" ").append(osVersion).append(" (").append(osArch).append(")\n");
        sb.append("当前日期: ").append(currentDateTime).append("\n\n");

        sb.append("# 任务上下文\n");
        sb.append("## 全局计划概要\n");
        sb.append(planStatus).append("\n\n");

        sb.append("## 当前步骤详情\n");
        sb.append("步骤编号: ").append(currentStepIndex).append("\n");
        sb.append("步骤要求: ").append(stepText).append("\n\n");

        sb.append("## 相关上下文信息\n");
        sb.append(extraParams).append("\n\n");

        sb.append("# 执行指南\n");
        sb.append("## 工具使用指南\n");
        sb.append(toolUsageGuide).append("\n");

        sb.append("## 步骤执行指南\n");
        sb.append(stepExecutionGuide).append("\n");

        sb.append("## 错误处理指南\n");
        sb.append(errorHandlingGuide).append("\n");

        sb.append("# 重要约束\n");
        sb.append("1. 严格遵循当前步骤要求，不要尝试提前完成后续步骤\n");
        sb.append("2. 全局目标仅作参考，专注于完成当前步骤\n");
        sb.append("3. 每个工具调用必须有明确目的和合理性\n");
        sb.append("5. 保持响应简洁明了，避免冗余信息\n");

        return sb.toString();
    }

    /**
     * 生成计划提示
     *
     * @return 格式化的提示字符串
     */
    public String generatePlanPrompt() {
        return """
                # 任务规划指南
                
                ## 角色定位
                你是一个专业的任务规划助手，负责将复杂任务分解为清晰、可执行的步骤序列。
                你的主要职责是分析用户需求，制定详细的执行计划，而非直接执行任务。
                
                ## 核心规划原则
                1. **分步执行** - 每次只规划并返回下一个需要执行的步骤
                2. **渐进式规划** - 基于前一步的执行结果来规划下一步
                3. **动态调整** - 根据执行反馈调整后续计划
                4. **单步聚焦** - 专注于当前步骤的准确性和可执行性
                5. **上下文感知** - 充分利用历史执行结果进行决策
                
                ## 工作模式
                ### 初始规划模式
                - 当接收到新任务时，分析任务需求和复杂度
                - 制定整体思路和方向
                - **只返回第一个具体的执行步骤**
                - 不要一次性列出所有步骤
                
                ### 渐进规划模式
                - 当接收到前一步执行结果时，分析执行状态
                - 基于当前进度和结果，规划下一个最合适的步骤
                - 如果遇到问题，提供解决方案或调整策略
                - 判断任务是否已完成，如完成则明确说明
                
                ## 步骤输出格式
                每次只返回一个步骤
                ```
                ## 任务完成判断
                当满足以下条件时，明确说明任务已完成：
                1. 用户的原始需求已经得到满足
                2. 所有必要的步骤都已执行完毕
                3. 没有遗留的问题或错误需要处理
                
                ## 工具选择指南
                1. 仅选择工具列表中存在的工具
                2. 根据当前步骤的具体需求选择最合适的工具
                3. 考虑工具的功能边界和适用场景
                4. 优先选择能够直接解决当前问题的工具
                
                ## 错误处理策略
                1. 如果前一步执行失败，分析失败原因
                2. 提供替代方案或修正步骤
                3. 必要时回退到安全状态重新规划
                4. 确保每个步骤都有明确的成功标准
                
                ## 重要约束
                1. **严禁一次性输出完整计划** - 每次只返回一个步骤
                2. **基于反馈调整** - 必须根据执行结果来规划下一步
                3. **保持灵活性** - 允许根据实际情况调整原计划
                4. **明确边界** - 你只负责规划，不执行任何实际操作
                5. **简洁明了** - 步骤描述要具体可执行，避免模糊表述
                
                ## 协作模式
                - 规划助理（你）：负责分析需求，制定下一步计划
                - 执行助理：负责执行具体步骤，返回执行结果
                - 通过这种协作模式实现复杂任务的分步完成
                """;
    }

}
