package com.alibaba.cloud.ai.copilot.domain.state;

import io.agentscope.core.state.State;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Non-sensitive metadata stored beside AgentState in the same AgentScope session. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContextMeta implements State {

    private int revision = 1;
    private String resetAt;
    private String updatedAt;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer cachedTokens;
    private Integer totalTokens;
}
