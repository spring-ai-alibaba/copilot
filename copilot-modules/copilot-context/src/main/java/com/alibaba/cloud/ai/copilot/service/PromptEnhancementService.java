package com.alibaba.cloud.ai.copilot.service;

/**
 * Service for enhancing user prompts using AI
 */
public interface PromptEnhancementService {
    
    /**
     * Enhance a user prompt to make it more effective for AI interaction
     * 
     * @param originalPrompt The original user prompt
     * @return Enhanced prompt with better structure and clarity
     */
    String enhancePrompt(String originalPrompt);
}
