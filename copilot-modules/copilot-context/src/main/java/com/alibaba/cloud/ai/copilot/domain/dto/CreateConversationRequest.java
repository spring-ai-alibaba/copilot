package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求
 *
 * @author better
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    /**
     * 模型配置ID
     */
    private String modelConfigId;
}

