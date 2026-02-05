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

    private PromptExtra otherConfig;
    
    private List<ToolInfo> tools;
}
