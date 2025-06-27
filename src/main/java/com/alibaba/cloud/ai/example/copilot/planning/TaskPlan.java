package com.alibaba.cloud.ai.example.copilot.planning;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务计划模型类
 * 存储任务的标题、描述、步骤列表
 */
public class TaskPlan {

    private String taskId; // 任务ID
    private String title;
    private String description;
    private List<TaskStep> steps;
    private String planStatus; // 全局计划状态
    private String extraParams; // 上下文信息


    public TaskPlan() {
        this.steps = new ArrayList<>();
    }

    /**
     * 获取任务标题
     * @return 任务标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置任务标题
     * @param title 任务标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取任务描述
     * @return 任务描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置任务描述
     * @param description 任务描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取任务步骤列表
     * @return 任务步骤列表
     */
    public List<TaskStep> getSteps() {
        return steps;
    }

    /**
     * 设置任务步骤列表
     * @param steps 任务步骤列表
     */
    public void setSteps(List<TaskStep> steps) {
        this.steps = steps;
    }

    /**
     * 添加单个任务步骤
     * @param step 任务步骤
     */
    public void addStep(TaskStep step) {
        if (this.steps == null) {
            this.steps = new ArrayList<>();
        }
        this.steps.add(step);
    }

    /**
     * 获取步骤数量
     * @return 步骤数量
     */
    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }

    /**
     * 获取全局计划状态
     * @return 全局计划状态
     */
    public String getPlanStatus() {
        return planStatus;
    }

    /**
     * 设置全局计划状态
     * @param planStatus 全局计划状态
     */
    public void setPlanStatus(String planStatus) {
        this.planStatus = planStatus;
    }

    /**
     * 获取上下文信息
     * @return 上下文信息
     */
    public String getExtraParams() {
        return extraParams;
    }

    /**
     * 设置上下文信息
     * @param extraParams 上下文信息
     */
    public void setExtraParams(String extraParams) {
        this.extraParams = extraParams;
    }

    /**
     * 获取任务ID
     * @return 任务ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 设置任务ID
     * @param taskId 任务ID
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
