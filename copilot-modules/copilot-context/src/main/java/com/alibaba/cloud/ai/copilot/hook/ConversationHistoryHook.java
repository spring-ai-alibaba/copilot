package com.alibaba.cloud.ai.copilot.hook;

import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话历史加载 Hook
 * 在模型调用前，从 chat_message 表加载历史消息
 *
 * @author better
 */
@Slf4j
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
@RequiredArgsConstructor
public class ConversationHistoryHook extends MessagesModelHook {

    private final ChatMessageMapper chatMessageMapper;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "conversation_history_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
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

        if (conversationId != null) {
            try {
                // 判断是否是首次用户请求（只包含 UserMessage 和 SystemMessage）
                // 如果包含 AssistantMessage 或 ToolResponseMessage，说明是 ReactAgent 内部的工具调用流程
                boolean isFirstUserRequest = previousMessages.stream()
                    .noneMatch(msg -> msg instanceof AssistantMessage || msg instanceof ToolResponseMessage);

                if (isFirstUserRequest) {
                    // 场景1：首次用户请求，加载历史消息
                    // 注意：用户消息已经在 ChatServiceImpl 中保存到数据库，所以历史消息已经包含最新的用户消息
                    int keep = appProperties.getConversation().getSummarization().getMessagesToKeep();
                    //至少保留 messagesToKeep 的若干倍，避免截断工具调用链路；同时避免全量拉取
                    int limit = Math.max(100, keep * 5);
                    List<ChatMessageEntity> entities = chatMessageMapper.selectByConversationIdWithPagination(conversationId, 0, limit);
                    if (!entities.isEmpty()) {
                        // 注意：分页接口按 created_time DESC 返回，这里反转成 ASC，保证还原顺序正确
                        java.util.Collections.reverse(entities);
                        List<Message> history = convertToMessages(entities);
                        // 直接用历史消息替换 previousMessages（历史消息已经包含最新的用户消息）
                        log.debug("加载会话历史: conversationId={}, historyCount={}, previousCount={}",
                            conversationId, history.size(), previousMessages.size());
                        return new AgentCommand(history, UpdatePolicy.REPLACE);
                    } else {
                        // 没有历史消息（理论上不应该发生，因为用户消息已经保存），直接返回当前消息
                        log.debug("首次会话，无历史消息: conversationId={}, messageCount={}",
                            conversationId, previousMessages.size());
                        return new AgentCommand(previousMessages);
                    }
                } else {
                    // 场景2：ReactAgent 内部的消息流（工具调用的中间状态）
                    // 不进行干预，让 ReactAgent 自己管理消息序列
                    // 这样可以确保工具调用的消息序列完整性（assistant with tool_calls -> tool -> assistant）
                    log.debug("ReactAgent 内部消息流，不加载历史: conversationId={}, messageCount={}",
                        conversationId, previousMessages.size());
                    return new AgentCommand(previousMessages);
                }
            } catch (Exception e) {
                log.error("加载会话历史失败: conversationId={}", conversationId, e);
            }
        }

        // 否则保持原样
        return new AgentCommand(previousMessages);
    }

    /**
     * 将数据库实体转换为 Spring AI Message 对象
     *
     */
    private List<Message> convertToMessages(List<ChatMessageEntity> entities) {
        List<Message> messages = new ArrayList<>();
        int restoredToolCount = 0;

        for (int i = 0; i < entities.size(); i++) {
            ChatMessageEntity entity = entities.get(i);
            try {
                Message message = switch (entity.getRole().toLowerCase()) {
                    case "user" -> new UserMessage(entity.getContent());
                    case "assistant" -> convertAssistantMessage(entity);
                    case "system" -> new SystemMessage(entity.getContent());
                    case "tool" -> {
                        Message toolMsg = convertToolMessage(entity);
                        if (toolMsg != null) {
                            restoredToolCount++;
                        }
                        yield toolMsg;
                    }
                    default -> {
                        log.warn("未知的消息角色: {}, 默认作为用户消息处理", entity.getRole());
                        yield new UserMessage(entity.getContent());
                    }
                };

                if (message != null) {
                    messages.add(message);
                }
            } catch (Exception e) {
                log.error("转换消息失败: messageId={}, role={}",
                    entity.getMessageId(), entity.getRole(), e);
            }
        }

        if (restoredToolCount > 0) {
            log.debug("历史消息恢复完成: 总消息数={}, 恢复消息数={}, 恢复 tool 消息={}",
                entities.size(), messages.size(), restoredToolCount);
        }

        // 验证并修复工具调用链的完整性
        return validateAndFixToolCallChain(messages);
    }

    /**
     * 验证并修复工具调用链的完整性
     * DeepSeek API 要求：如果助手消息包含 tool_calls，后面必须有对应的 tool 响应消息
     *
     * @param messages 消息列表
     * @return 修复后的消息列表
     */
    private List<Message> validateAndFixToolCallChain(List<Message> messages) {
        List<Message> fixedMessages = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            Message msg = messages.get(i);

            if (msg instanceof AssistantMessage assistantMsg
                && assistantMsg.getToolCalls() != null
                && !assistantMsg.getToolCalls().isEmpty()) {

                // 检查是否有关联的 tool 响应消息
                List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
                Set<String> expectedToolCallIds = new HashSet<>();
                for (AssistantMessage.ToolCall tc : toolCalls) {
                    expectedToolCallIds.add(tc.id());
                }

                // 查找对应的 tool 响应消息
                boolean hasToolResponse = false;
                int j = i + 1;
                while (j < messages.size()) {
                    Message nextMsg = messages.get(j);
                    if (nextMsg instanceof ToolResponseMessage toolRespMsg) {
                        for (ToolResponseMessage.ToolResponse resp : toolRespMsg.getResponses()) {
                            if (expectedToolCallIds.contains(resp.id())) {
                                hasToolResponse = true;
                                expectedToolCallIds.remove(resp.id());
                            }
                        }
                        if (expectedToolCallIds.isEmpty()) {
                            break;
                        }
                    }
                    j++;
                }

                if (hasToolResponse && expectedToolCallIds.isEmpty()) {
                    // 工具调用链完整，保留助手消息和后续的 tool 消息
                    fixedMessages.add(assistantMsg);
                    for (int k = i + 1; k <= j && k < messages.size(); k++) {
                        fixedMessages.add(messages.get(k));
                    }
                    i = j + 1;
                } else {
                    // 工具调用链不完整，移除 tool_calls 只保留内容
                    log.warn("检测到不完整的工具调用链，移除 tool_calls");
                    // 使用反射创建不带 tool_calls 的 AssistantMessage
                    // 构造函数签名: AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media)
                    try {
                        Constructor<AssistantMessage> constructor = AssistantMessage.class
                            .getDeclaredConstructor(String.class, Map.class, List.class, List.class);
                        constructor.setAccessible(true);

                        String content = assistantMsg.getText() != null ? assistantMsg.getText() : "";
                        Map<String, Object> properties = new HashMap<>();

                        AssistantMessage fixedAssistant = constructor.newInstance(content, properties, new ArrayList<>(), new ArrayList<>());
                        fixedMessages.add(fixedAssistant);
                    } catch (Exception e) {
                        log.error("使用反射创建修复的 AssistantMessage 失败，回退到普通消息", e);
                        fixedMessages.add(new AssistantMessage(
                            assistantMsg.getText() != null ? assistantMsg.getText() : ""));
                    }
                    i++;
                }
            } else {
                fixedMessages.add(msg);
                i++;
            }
        }
        return fixedMessages;
    }

    /**
     * 检查 entity 的 metadata 中是否包含 tool_calls
     */
    private boolean hasToolCalls(ChatMessageEntity entity) {
        if (entity.getMetadata() == null || entity.getMetadata().trim().isEmpty()) {
            return false;
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(
                entity.getMetadata(),
                new TypeReference<Map<String, Object>>() {}
            );
            return Boolean.TRUE.equals(metadata.get("hasToolCalls"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 转换 Assistant 消息，包括恢复 tool_calls
     */
    private AssistantMessage convertAssistantMessage(ChatMessageEntity entity) {
        String content = entity.getContent() != null ? entity.getContent() : "";

        // 检查 metadata 中是否包含 tool_calls
        if (entity.getMetadata() != null && !entity.getMetadata().trim().isEmpty()) {
            try {
                Map<String, Object> metadataMap = objectMapper.readValue(
                    entity.getMetadata(),
                    new TypeReference<Map<String, Object>>() {}
                );

                if (Boolean.TRUE.equals(metadataMap.get("hasToolCalls"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) metadataMap.get("toolCalls");
                    if (toolCallsData != null && !toolCallsData.isEmpty()) {
                        // 恢复 tool_calls
                        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
                        for (Map<String, Object> callData : toolCallsData) {
                            try {
                                String id = (String) callData.get("id");
                                String type = (String) callData.get("type");
                                String name = (String) callData.get("name");
                                String arguments = callData.get("arguments") != null
                                    ? callData.get("arguments").toString() : "{}";

                                AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                                    id != null ? id : "",
                                    type != null ? type : "function",
                                    name != null ? name : "",
                                    arguments
                                );
                                toolCalls.add(toolCall);
                            } catch (Exception e) {
                                log.warn("恢复 tool_call 失败: {}", callData, e);
                            }
                        }

                        if (!toolCalls.isEmpty()) {
                            log.debug("恢复 Assistant 消息，包含 {} 个 tool_calls", toolCalls.size());
                            // 使用反射创建带 tool_calls 的 AssistantMessage
                            // 构造函数签名: AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media)
                            // 注意：tool_calls 通过构造函数参数传递，不要放入 properties，避免序列化到 MetadataDTO 时出错
                            try {
                                Constructor<AssistantMessage> constructor = AssistantMessage.class
                                    .getDeclaredConstructor(String.class, Map.class, List.class, List.class);
                                constructor.setAccessible(true);

                                // properties 应该为空或只包含其他元数据，不要包含 tool_calls
                                Map<String, Object> properties = new HashMap<>();

                                return constructor.newInstance(content, properties, toolCalls, new ArrayList<>());
                            } catch (Exception e) {
                                log.error("使用反射创建带 tool_calls 的 AssistantMessage 失败，使用普通消息", e);
                                // 如果反射失败，回退到普通消息
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Assistant 消息 metadata 失败: messageId={}", entity.getMessageId(), e);
            }
        }

        // 普通 Assistant 消息（无 tool_calls）
        return new AssistantMessage(content);
    }

    /**
     * 转换 Tool 消息为 ToolResponseMessage
     * 使用反射创建 ToolResponseMessage（构造函数为 protected）
     */
    private ToolResponseMessage convertToolMessage(ChatMessageEntity entity) {
        if (entity.getMetadata() == null || entity.getMetadata().trim().isEmpty()) {
            log.warn("tool 消息缺少 metadata，无法恢复 ToolResponseMessage: messageId={}", entity.getMessageId());
            return null;
        }

        try {
            Map<String, Object> metadata = objectMapper.readValue(
                entity.getMetadata(),
                new TypeReference<Map<String, Object>>() {}
            );

            String toolCallId = (String) metadata.get("toolCallId");
            String toolName = (String) metadata.get("toolName");
            String content = entity.getContent() != null ? entity.getContent() : "";

            if (toolCallId == null || toolName == null) {
                log.warn("tool 消息 metadata 缺少必要字段: messageId={}, metadata={}",
                    entity.getMessageId(), metadata);
                return null;
            }

            // 使用反射创建 ToolResponseMessage.ToolResponse
            // ToolResponse 是一个 record，构造函数接受 (String id, String name, String responseData)
            Constructor<?> toolResponseConstructor = ToolResponseMessage.ToolResponse.class
                .getDeclaredConstructor(String.class, String.class, String.class);

            Object toolResponse = toolResponseConstructor.newInstance(toolCallId, toolName, content);
            List<ToolResponseMessage.ToolResponse> responses = List.of(
                (ToolResponseMessage.ToolResponse) toolResponse
            );

            // 使用反射创建 ToolResponseMessage
            // ToolResponseMessage 构造函数接受 (List<ToolResponse> responses, Map<String, Object> properties)
            Constructor<ToolResponseMessage> constructor = ToolResponseMessage.class
                .getDeclaredConstructor(List.class, Map.class);
            constructor.setAccessible(true);

            // properties 不要塞入自定义字段（如 toolCallId/toolName），避免在 state().data() 序列化/反序列化链路中污染 metadata
            Map<String, Object> properties = new HashMap<>();
            ToolResponseMessage toolResponseMessage = constructor.newInstance(responses, properties);

            log.debug("恢复 ToolResponseMessage: toolCallId={}, toolName={}", toolCallId, toolName);
            return toolResponseMessage;

        } catch (Exception e) {
            log.error("创建 ToolResponseMessage 失败: messageId={}", entity.getMessageId(), e);
            return null;
        }
    }

}

