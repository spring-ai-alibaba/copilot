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

    private PromptExtra otherConfig;
    
    private List<ToolInfo> tools;
}
