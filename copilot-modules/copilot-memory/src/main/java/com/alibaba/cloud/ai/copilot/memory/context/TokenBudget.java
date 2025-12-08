package com.alibaba.cloud.ai.copilot.memory.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 预算
 *
 * @author better
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBudget {
    /**
     * 系统提示词预算
     */
    private int systemPrompt;

    /**
     * 长期记忆预算
     */
    private int longTermMemory;

    /**
     * 压缩历史预算
     */
    private int compressedHistory;

    /**
     * 保留历史预算
     */
    private int preservedHistory;

    /**
     * 响应缓冲预算
     */
    private int responseBuffer;

    /**
     * 总预算
     */
    private int total;
}

