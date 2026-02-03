package com.alibaba.cloud.ai.copilot.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用于观测“实际送入模型”的消息列表。
 * 放在 SummarizationHook 后面，可以清晰看到摘要是否触发（messageCount 是否下降、是否出现 summary/system 消息等）。
 */
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTraceHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "message_trace_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String conversationId = null;
        if (config != null) {
            Object conversationIdObj = config.getMetadata("conversationId");
            if (conversationIdObj instanceof Optional<?> optional) {
                conversationIdObj = optional.orElse(null);
            }
            if (conversationIdObj != null) {
                conversationId = conversationIdObj.toString();
            }
        }

        // 只在 debug 下打印，避免生产噪音
        if (log.isDebugEnabled()) {
            int totalChars = previousMessages.stream()
                .mapToInt(m -> safeText(m).length())
                .sum();

            String roleSeq = previousMessages.stream()
                .map(this::roleOf)
                .collect(Collectors.joining(" -> "));

            boolean hasToolCallAssistant = previousMessages.stream()
                .anyMatch(m -> (m instanceof AssistantMessage am) && am.getToolCalls() != null && !am.getToolCalls().isEmpty());

            boolean hasToolResponse = previousMessages.stream().anyMatch(m -> m instanceof ToolResponseMessage);

            boolean maybeHasSummary = previousMessages.stream()
                .filter(m -> m instanceof SystemMessage)
                .map(this::safeText)
                .anyMatch(t -> {
                    String s = t.toLowerCase();
                    return s.contains("summary") || s.contains("摘要") || s.contains("总结");
                });

            log.debug("MessageTraceHook: conversationId={}, messageCount={}, totalChars={}, roles=[{}], hasAssistantToolCalls={}, hasToolResponse={}, maybeHasSummarySystemMessage={}",
                conversationId,
                previousMessages.size(),
                totalChars,
                roleSeq,
                hasToolCallAssistant,
                hasToolResponse,
                maybeHasSummary);
        }

        return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
    }

    private String roleOf(Message m) {
        if (m instanceof SystemMessage) return "system";
        if (m instanceof UserMessage) return "user";
        if (m instanceof AssistantMessage) return "assistant";
        if (m instanceof ToolResponseMessage) return "tool";
        return "unknown";
    }

    private String safeText(Message m) {
        try {
            if (m instanceof AssistantMessage am) {
                return am.getText() != null ? am.getText() : "";
            }
            if (m instanceof UserMessage um) {
                return um.getText() != null ? um.getText() : "";
            }
            if (m instanceof SystemMessage sm) {
                return sm.getText() != null ? sm.getText() : "";
            }
            if (m instanceof ToolResponseMessage trm) {
                // tool response 可能较长，这里只用于统计长度，不做结构化解析
                if (trm.getResponses() != null && !trm.getResponses().isEmpty()) {
                    String d = trm.getResponses().get(0).responseData();
                    return d != null ? d : "";
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return "";
    }
}


