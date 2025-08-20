package com.alibaba.cloud.ai.copilot.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Model configuration response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigResponse {
    
    private String label;
    
    private String value;
    
    private boolean useImage;
    
    private String description;
    
    private String icon;
    
    private String provider;
    
    private boolean functionCall;
}
