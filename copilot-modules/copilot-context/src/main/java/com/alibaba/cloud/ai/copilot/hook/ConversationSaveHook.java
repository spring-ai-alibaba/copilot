package com.alibaba.cloud.ai.copilot.hook;

import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话保存 Hook
 * 在模型调用后，保存 Assistant 的响应到 chat_message 表
 *
 * @author better
 */
@Slf4j
@Component
@HookPositions({HookPosition.AFTER_MODEL})
@RequiredArgsConstructor
public class ConversationSaveHook extends MessagesModelHook {

    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "conversation_save_hook";
    }

    @Override
    public AgentCommand afterModel(List<Message> messages, RunnableConfig config) {
        // 从 config 的 metadata 中获取 conversationId
        String conversationId = null;
        if (config != null) {
            Object conversationIdObj = config.getMetadata("conversationId");
            if (conversationIdObj instanceof java.util.Optional<?> optional) {
                conversationIdObj = optional.orElse(null);
            }
            if (conversationIdObj != null) {
                conversationId = conversationIdObj.toString();
            }
        }

        if (conversationId != null && !messages.isEmpty()) {
            try {

                AssistantMessage finalAssistantMessage = null;
                int finalAssistantIndex = -1;

                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message msg = messages.get(i);
                    if (msg instanceof AssistantMessage assistantMsg) {
                        String content = assistantMsg.getText();
                        if (content != null && !content.trim().isEmpty()) {
                            // 关键：只把“不包含 tool_calls 的 assistant”当作最终回复候选
                            // 否则在工具链路中间（assistant 刚发出 tool_calls，但 tool 还没追加到 messages）会被误判为“最终回复”，导致落库链路不完整
                            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                                continue;
                            }
                            // 检查后面是否还有 ToolResponseMessage
                            boolean hasToolAfter = false;
                            for (int j = i + 1; j < messages.size(); j++) {
                                if (messages.get(j) instanceof ToolResponseMessage) {
                                    hasToolAfter = true;
                                    break;
                                }
                            }

                            // 如果后面没有 tool 消息，说明这是最终响应
                            if (!hasToolAfter) {
                                finalAssistantMessage = assistantMsg;
                                finalAssistantIndex = i;
                                break;
                            }
                        }
                    }
                }

                if (finalAssistantMessage != null) {

                    int startIndex = -1;
                    for (int i = 0; i <= finalAssistantIndex; i++) {
                        if (messages.get(i) instanceof AssistantMessage) {
                            startIndex = i;
                            break;
                        }
                    }

                    if (startIndex >= 0) {
                        // 保存从 startIndex 到 finalAssistantIndex 的所有消息
                        int savedCount = 0;
                        for (int i = startIndex; i <= finalAssistantIndex; i++) {
                            Message msg = messages.get(i);

                            ChatMessageEntity entity = new ChatMessageEntity();
                            entity.setConversationId(conversationId);
                            entity.setMessageId(UUID.randomUUID().toString());
                            entity.setCreatedTime(LocalDateTime.now());
                            entity.setUpdatedTime(LocalDateTime.now());

                            if (msg instanceof AssistantMessage assistantMsg) {
                                entity.setRole("assistant");
                                String content = assistantMsg.getText();
                                entity.setContent(content != null ? content : "");

                                // 如果包含 tool_calls，保存到 metadata
                                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                                    try {
                                        Map<String, Object> metadata = new HashMap<>();
                                        metadata.put("hasToolCalls", true);
                                        metadata.put("toolCalls", assistantMsg.getToolCalls());

                                        entity.setMetadata(objectMapper.writeValueAsString(metadata));
                                    } catch (Exception e) {
                                        log.warn("保存 tool_calls 到 metadata 失败", e);
                                    }
                                }

                                chatMessageMapper.insert(entity);
                                savedCount++;
                                log.debug("保存 Assistant 消息: conversationId={}, hasToolCalls={}",
                                    conversationId, assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty());

                            } else if (msg instanceof ToolResponseMessage toolMsg) {
                                // 关键：ToolResponseMessage 可能包含多个 responses（一次 assistant 发起多个 tool_calls）
                                // 之前只保存第一个 response 会导致 tool 调用链不完整，历史恢复时会触发“移除 tool_calls”的修复逻辑，进而让模型重复发起工具调用
                                List<ToolResponseMessage.ToolResponse> responses = toolMsg.getResponses();
                                if (responses == null || responses.isEmpty()) {
                                    // 兜底：没有 response 也不应落库，否则会污染链路
                                    continue;
                                }

                                for (ToolResponseMessage.ToolResponse resp : responses) {
                                    ChatMessageEntity toolEntity = new ChatMessageEntity();
                                    toolEntity.setConversationId(conversationId);
                                    toolEntity.setMessageId(UUID.randomUUID().toString());
                                    toolEntity.setRole("tool");
                                    toolEntity.setCreatedTime(LocalDateTime.now());
                                    toolEntity.setUpdatedTime(LocalDateTime.now());

                                    String toolContent = resp.responseData() != null ? resp.responseData() : "";
                                    toolEntity.setContent(toolContent);

                                    String toolCallId = resp.id();
                                    String toolName = resp.name();
                                    try {
                                        Map<String, Object> metadata = new HashMap<>();
                                        if (toolCallId != null) {
                                            metadata.put("toolCallId", toolCallId);
                                        }
                                        if (toolName != null) {
                                            metadata.put("toolName", toolName);
                                        }
                                        toolEntity.setMetadata(objectMapper.writeValueAsString(metadata));
                                    } catch (Exception e) {
                                        log.warn("保存 tool_call_id 到 metadata 失败", e);
                                    }

                                    chatMessageMapper.insert(toolEntity);
                                    savedCount++;
                                    log.debug("保存 Tool 消息: conversationId={}, toolCallId={}", conversationId, toolCallId);
                                }
                            }
                        }

                        log.debug("保存完整交互序列: conversationId={}, savedCount={}",
                            conversationId, savedCount);
                    }
                } else {
                    log.debug("工具调用未完成，暂不保存: conversationId={}, messageCount={}",
                        conversationId, messages.size());
                }
            } catch (Exception e) {
                log.error("保存消息到数据库失败: conversationId={}", conversationId, e);
            }
        }

        return new AgentCommand(messages);
    }
}

