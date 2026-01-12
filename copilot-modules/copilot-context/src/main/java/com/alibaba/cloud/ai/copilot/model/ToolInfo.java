package com.alibaba.cloud.ai.copilot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool information for AI function calling
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolInfo {
    
    private String id;
    
    private String name;
    
    private String description;
    
    private ParametersSchema parameters;

    private Map<String,Object> params;

    /**
     * Parameters schema for tool
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParametersSchema {
        private String type;
        private String title;
        private String description;
        private String[] required;
        private Map<String, Object> properties;
    }
}
