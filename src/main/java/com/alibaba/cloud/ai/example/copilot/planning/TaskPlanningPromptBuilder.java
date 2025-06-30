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
                
                ## 文件操作工具使用规范（最高优先级）
                1. **创建新文件**
                   - 使用 `write_file` 工具创建新文件
                   - 参数 path (文件路径), content (文件内容)
                   - 自动使用 UTF-8 编码
                   
                2. **修改现有文件**
                   - 优先使用 `edit_file` 工具进行精确编辑
                   - 支持基于内容匹配的选择性编辑
                   - 保留缩进和格式化
                   
                3. **创建目录结构**
                   - 使用 `create_directory` 工具
                   - 自动创建父目录
                   - 如果目录已存在则静默成功
                
                4. **文件操作顺序**
                   a. **验证阶段**
                      - 使用 `get_file_info` 检查文件/目录是否存在
                      - 使用 `list_directory` 查看目录内容
                      - 使用 `read_file` 或 `read_multiple_files` 读取现有内容
                   b. **执行阶段**
                      - 使用 `create_directory` 前确保目录存在
                      - 使用 `write_file` 创建新文件或 `edit_file` 修改现有文件
                      - 使用 `move_file` 重命名或移动文件（如需要）
                
                5. **强制目录结构**
                   - 项目模板目录: `project-template/`
                     - 后端代码: `project-template/backend/`
                     - 前端代码: `project-template/frontend/`
                   - 生成项目目录: `generated-projects/`
                
                6. **文件操作注意事项**
                   - 所有文件操作使用 UTF-8 编码
                   - 使用 `edit_file` 时启用 preserveIndentation: true
                   - 使用 `edit_file` 时启用 normalizeWhitespace: true
                   - 重要修改前先使用 dryRun 模式预览
                   - 使用 `search_files` 进行文件搜索和定位
                
                7. **Java包结构约束**
                   - 必须使用: `com.alibaba.cloud.ai.example.*`
                   - 禁止使用: `com.example.*` 或其他任意包名
                
                ## 文件操作错误处理流程
                1. **路径不存在错误**
                   - 如果父目录不存在，从根目录开始逐级创建
                   - 验证创建结果
                
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
                每次只返回一个步骤,前两步构建项目基本信息,索引从3开始
                
                ## 任务完成标识
                在返回的TaskPlan对象中，必须设置isCompleted字段：
                - 如果任务已完全完成，设置 isCompleted = true
                - 如果任务还需要继续执行，设置 isCompleted = false 或 null
                - 完成判断标准：用户的原始需求已经得到满足，所有必要步骤都已执行完毕
                
                ## 错误示例
                1. ❌ 不验证直接写入文件
                2. ❌ 跳过 dryRun 直接修改文件
                3. ❌ 使用错误的包名结构
                4. ❌ 在未经验证的路径创建文件
                5. ❌ 忽略文件编码设置
                
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
                
                ## 协作模式
                - 规划助理（你）：负责分析需求，制定下一步计划
                - 执行助理：负责执行具体步骤，返回执行结果
                - 通过这种协作模式实现复杂任务的分步完成
                """;
    }

}
