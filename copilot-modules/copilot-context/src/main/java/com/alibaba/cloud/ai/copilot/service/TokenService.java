package com.alibaba.cloud.ai.copilot.service;

/**
 * Token service interface
 */
public interface TokenService {

    /**
     * Estimate tokens for given text
     */
    int estimateTokens(String text);

    /**
     * Deduct tokens from user
     */
    void deductUserTokens(String userId, int tokens);
}
