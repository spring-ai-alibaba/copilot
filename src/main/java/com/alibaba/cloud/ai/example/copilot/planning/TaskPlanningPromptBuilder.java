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
     * @return 添加的系统提示消息对象
     */
    public String buildTaskPlanningPrompt(TaskPlan taskPlan, String projectInfo) {
        // 获取系统信息
        String osInfo = String.format("%s %s (%s)",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"));
        String currentDate = java.time.LocalDate.now().toString();

        // 核心指南
        String coreGuides = """
                ## 工具使用指南
                1. 直接使用最合适的工具执行任务，无需询问确认
                2. 遇到多个工具选择时，选择最高效的工具直接执行
                
                ## 步骤执行指南
                1. 直接执行当前步骤要求，不要询问用户
                2. 严格按照规划执行具体操作，不要添加额外步骤
                3. 执行过程中遇到问题时才反馈，否则直接继续执行

                """;

        // 文件操作规范（简化版）
        String fileOpGuide = """
                ## 文件操作规范
                1. **文件创建与修改**
                   - 创建文件：使用`write_file`工具，参数path和content
                   - 修改文件：使用`edit_file`工具进行精确编辑
                   - 创建目录：使用`create_directory`工具
                   - 一次只能操作一个文件
                
                2. **操作流程**
                   - 验证：先用`get_file_info`、`list_directory`或`read_file`检查
                   - 执行：确认后再创建或修改文件
                
                3. **注意事项**
                   - 使用UTF-8编码
                   - Java包必须使用：`com.alibaba.cloud.ai.example.*`
                   - 一次只能操作一个文件
                """;

        // 重要约束（简化版）
        String constraints = """
                ## 重要约束
                1. 优先级最高: 直接执行任务，不要询问用户,不需要用户确认
                2. 创建文件前先验证目录是否存在
                5. 保持响应简洁，只报告结果或问题
                6. 不询问是否继续，直接执行下一步
                """;

        // 组装提示词
        return String.format("# 系统环境：%s | 日期：%s\n\n", osInfo, currentDate) +
                String.format("# 任务：%s\n\n", taskPlan.getTitle()) +
                String.format("## 任务描述\n%s\n\n", taskPlan.getDescription()) +
                "# 执行指南\n" + coreGuides + "\n" +
                fileOpGuide + "\n" +
                constraints + "\n" +
                "## 项目信息\n" + projectInfo;
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
                
                ## 文件操作工具使用规范（最高优先级）
                1. **创建新文件**
                   - 使用 `write_file` 工具创建新文件
                   - 参数 path (文件路径), content (文件内容)
                   - 自动使用 UTF-8 编码
                   - 一次只能操作一个文件
             
                2. **修改现有文件**
                   - 优先使用 `edit_file` 工具进行精确编辑
                   - 支持基于内容匹配的选择性编辑
                   - 保留缩进和格式化
                   - 一次只能操作一个文件
                
                3. **创建目录结构**
                   - 使用 `create_directory` 工具
                   - 自动创建父目录
                   - 如果目录已存在则静默成功
                   - 一次只能操作一个目录

                ## 工作模式
                ### 初始规划模式
                - 当接收到新任务时，分析任务需求和复杂度
                - 制定整体思路和方向
                - **只返回第一个具体的执行步骤**
                - 不要一次性列出所有步骤
               
            
                ## 任务完成标识
                在返回的TaskPlan对象中，必须设置isCompleted字段：
                - 如果任务已完全完成，设置 isCompleted = true
                - 如果任务还需要继续执行，设置 isCompleted = false 或 null
                - 完成判断标准：用户的原始需求已经得到满足，所有必要步骤都已执行完毕
                

                ## 任务完成判断
                当满足以下条件时，明确说明任务已完成：
                1. 用户的原始需求已经得到满足
                2. 所有必要的步骤都已执行完毕
                3. 没有遗留的问题或错误需要处理
                4. 所有文件操作都已经过验证
                
                ## 重要约束
                1. **严禁一次性输出完整计划** - 每次只返回一个步骤
                2. **基于反馈调整** - 必须根据执行结果来规划下一步
                3. **保持灵活性** - 允许根据实际情况调整原计划
                4. **明确边界** - 你只负责规划，不执行任何实际操作
                5. **简洁明了** - 步骤描述要具体可执行，避免模糊表述
      
                """;
    }

}
