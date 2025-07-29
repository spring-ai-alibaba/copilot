package com.alibaba.cloud.ai.copilot.service;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * AI分析过程事件类
 * 继承自LogEvent，添加分析过程相关的字段
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisEvent extends LogEvent {

    private String stepName;        // 分析步骤名称
    private String description;     // 步骤描述
    private String icon;           // 步骤图标
    private String status;         // 步骤状态: ANALYZING, COMPLETED, ERROR
    private Long executionTime;    // 执行时间(毫秒)
    private String details;        // 详细信息

    // Constructors
    public AnalysisEvent() {
        super();
    }

    public AnalysisEvent(String type, String taskId, String stepName, String description,
                        String message, String timestamp, String icon, String status) {
        super(type, taskId, message, timestamp);
        this.stepName = stepName;
        this.description = description;
        this.icon = icon;
        this.status = status;
    }

    // Getters and Setters
    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public String toString() {
        return "AnalysisEvent{" +
                "stepName='" + stepName + '\'' +
                ", description='" + description + '\'' +
                ", icon='" + icon + '\'' +
                ", status='" + status + '\'' +
                ", executionTime=" + executionTime +
                ", details='" + details + '\'' +
                "} " + super.toString();
    }
}
