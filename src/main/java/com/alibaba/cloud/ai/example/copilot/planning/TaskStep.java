package com.alibaba.cloud.ai.example.copilot.planning;

/**
 * 任务步骤模型类
 * 存储单个任务步骤的索引和要求描述
 */
public class TaskStep {
    /**
     * 执行步骤
     */
    private int stepIndex;

    /**
     * 执行要求/执行内容详情
     */
    private String stepRequirement;

    /**
     * 执行需要调用的方法
     */
    private String toolName;

    /**
     * 执行需要返回的内容
     */
    private String result;

    /**
     * 执行状态,默认执行中
     */
    private String status;

    /**
     * 步骤开始执行的时间戳
     */
    private long startTime;

    /**
     * 步骤结束执行的时间戳
     */
    private long endTime;

    /**
     * 获取步骤索引
     * @return 步骤索引
     */
    public int getStepIndex() {
        return stepIndex;
    }

    /**
     * 设置步骤索引
     * @param stepIndex 步骤索引
     */
    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    /**
     * 获取步骤要求描述
     * @return 步骤要求描述
     */
    public String getStepRequirement() {
        return stepRequirement;
    }

    /**
     * 设置步骤要求描述
     * @param stepRequirement 步骤要求描述
     */
    public void setStepRequirement(String stepRequirement) {
        this.stepRequirement = stepRequirement;
    }

    /**
     * 获取步骤执行结果
     * @return 步骤执行结果
     */
    public String getResult() {
        return result;
    }

    /**
     * 设置步骤执行结果
     * @param result 步骤执行结果
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * 获取步骤执行状态
     * @return 步骤执行状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置步骤执行状态
     * @param status 步骤执行状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * 获取步骤开始时间
     * @return 步骤开始时间戳
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * 设置步骤开始时间
     * @param startTime 步骤开始时间戳
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * 获取步骤结束时间
     * @return 步骤结束时间戳
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * 设置步骤结束时间
     * @param endTime 步骤结束时间戳
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "TaskStep{" +
                "stepIndex=" + stepIndex +
                ", stepRequirement='" + stepRequirement + '\'' +
                ", toolName='" + toolName + '\'' +
                ", result='" + result + '\'' +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
