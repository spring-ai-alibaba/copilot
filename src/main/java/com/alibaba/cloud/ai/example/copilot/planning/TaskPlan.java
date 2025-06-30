package com.alibaba.cloud.ai.example.copilot.planning;



/**
 * 任务计划模型类
 * 存储任务的标题、描述、步骤列表
 */
public class TaskPlan {

    private String taskId; // 任务ID
    private String title;
    private String description;
    private TaskStep step;
    private String planStatus; // 全局计划状态
    private String extraParams; // 上下文信息
    private Boolean isCompleted; // 任务完成标识


    public TaskPlan() {
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
     * 获取任务步骤
     * @return 任务步骤
     */
    public TaskStep getStep() {
        return step;
    }

    /**
     * 设置任务步骤
     * @param step 任务步骤
     */
    public void setStep(TaskStep step) {
        this.step = step;
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

    /**
     * 获取任务完成标识
     * @return 任务完成标识
     */
    public Boolean getIsCompleted() {
        return isCompleted;
    }

    /**
     * 设置任务完成标识
     * @param isCompleted 任务完成标识
     */
    public void setIsCompleted(Boolean isCompleted) {
        this.isCompleted = isCompleted;
    }
}
