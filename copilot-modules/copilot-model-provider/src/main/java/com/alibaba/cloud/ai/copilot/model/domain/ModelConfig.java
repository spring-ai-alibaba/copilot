package com.alibaba.cloud.ai.copilot.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model configuration entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

    private String modelName;

    private String modelKey;

    private boolean useImage;

    private String description;

    private String iconUrl;

    private String provider;

    private String apiKey;

    private String apiUrl;

    private boolean functionCall;
}
