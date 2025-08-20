package com.alibaba.cloud.ai.copilot.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Application information response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppInfoResponse {
    
    private String name;
    
    private String version;
    
    private String description;
    
    private List<String> features;
    
    private String language;
    
    private String status;
}
