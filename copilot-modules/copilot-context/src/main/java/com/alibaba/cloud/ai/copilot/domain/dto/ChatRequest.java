package com.alibaba.cloud.ai.copilot.domain.dto;

import  lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    private Message message;

    /**
     * 用户配置的模型ID
     */
    private String modelConfigId;

    /**
     * 会话ID（可选，不传则创建新会话）
     */
    private String conversationId;

    /**
     * 是否在会话中使用偏好（默认true）
     */
    private Boolean enablePreferences;

    /**
     * 是否允许学习偏好（默认true）
     */
    private Boolean enablePreferenceLearning;

    /**
     * 是否启用 Plan Mode。启用后 Agent 只能只读探索并写计划，
     * 计划经过人工审批后才会进入执行阶段。
     */
    private Boolean planMode;

    /**
     * 对待审批计划的操作：APPROVE / REJECT。
     */
    private String planAction;

    /**
     * 驳回计划时的人类反馈。
     */
    private String planFeedback;

    private PromptExtra otherConfig;
    
    private List<ToolInfo> tools;
}
