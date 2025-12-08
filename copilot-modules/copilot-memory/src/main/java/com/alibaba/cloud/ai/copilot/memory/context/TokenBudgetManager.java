package com.alibaba.cloud.ai.copilot.memory.context;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Token 预算管理器
 * 合理分配各部分的 Token 使用
 *
 * @author better
 */
@Slf4j
@Component
public class TokenBudgetManager {

    private final MemoryProperties memoryProperties;
    private final TokenCounterService tokenCounterService;

    public TokenBudgetManager(
            MemoryProperties memoryProperties,
            TokenCounterService tokenCounterService) {
        this.memoryProperties = memoryProperties;
        this.tokenCounterService = tokenCounterService;
    }

    /**
     * 计算 Token 预算
     *
     * @param modelName 模型名称
     * @return Token 预算
     */
    public TokenBudget calculateBudget(String modelName) {
        int maxTokens = tokenCounterService.getModelTokenLimit(modelName);

        MemoryProperties.TokenBudget budgetConfig = memoryProperties.getTokenBudget();

        TokenBudget budget = TokenBudget.builder()
                .systemPrompt((int) (maxTokens * budgetConfig.getSystemPromptRatio()))
                .longTermMemory((int) (maxTokens * budgetConfig.getLongTermMemoryRatio()))
                .compressedHistory((int) (maxTokens * budgetConfig.getCompressedHistoryRatio()))
                .preservedHistory((int) (maxTokens * budgetConfig.getPreservedHistoryRatio()))
                .responseBuffer((int) (maxTokens * budgetConfig.getResponseBufferRatio()))
                .total(maxTokens)
                .build();

        log.debug("Calculated token budget for model {}: {}", modelName, budget);
        return budget;
    }
}

