package com.alibaba.cloud.ai.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Extra configuration for prompts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptExtra {
    
    private boolean isBackEnd;
    
    private String backendLanguage;
    
    private Map<String, Object> extra;
}
