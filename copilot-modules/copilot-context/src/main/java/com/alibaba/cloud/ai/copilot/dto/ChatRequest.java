package com.alibaba.cloud.ai.copilot.dto;

import com.alibaba.cloud.ai.copilot.model.ChatMode;
import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.model.PromptExtra;
import com.alibaba.cloud.ai.copilot.model.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chat request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    private List<Message> messages;

    private String model;

    /**
     * 用户配置的模型ID
     */
    private String modelConfigId;
    
    private ChatMode mode = ChatMode.BUILDER;
    
    private PromptExtra otherConfig;
    
    private List<ToolInfo> tools;
}
