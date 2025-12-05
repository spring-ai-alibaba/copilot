package com.alibaba.cloud.ai.copilot.memory.shortterm;

import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CompressibleChatMemory 适配器
 * 实现 Spring AI 的 ChatMemory 接口，内部使用 CompressibleChatMemory
 *
 * @author better
 */
@Slf4j
@Component
public class CompressibleChatMemoryAdapter implements ChatMemory {

    private final CompressibleChatMemory compressibleChatMemory;

    public CompressibleChatMemoryAdapter(CompressibleChatMemory compressibleChatMemory) {
        this.compressibleChatMemory = compressibleChatMemory;
    }

    @Override
    public void add(String conversationId, org.springframework.ai.chat.messages.Message message) {
        // 转换为项目内部的 Message 格式
        Message internalMessage = convertToInternalMessage(message);
        compressibleChatMemory.add(conversationId, internalMessage);
    }

    @Override
    public void add(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
        // 批量添加消息
        for (org.springframework.ai.chat.messages.Message message : messages) {
            Message internalMessage = convertToInternalMessage(message);
            compressibleChatMemory.add(conversationId, internalMessage);
        }
    }

    @Override
    public List<org.springframework.ai.chat.messages.Message> get(String conversationId) {
        // 从 CompressibleChatMemory 获取消息
        List<Message> internalMessages = compressibleChatMemory.get(conversationId);

        // 转换为 Spring AI 的 Message 格式
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        for (Message internalMsg : internalMessages) {
            org.springframework.ai.chat.messages.Message springMsg = convertToSpringMessage(internalMsg);
            if (springMsg != null) {
                springMessages.add(springMsg);
            }
        }

        return springMessages;
    }

    @Override
    public void clear(String conversationId) {
        compressibleChatMemory.clear(conversationId);
    }

    /**
     * 将 Spring AI 的 Message 转换为项目内部的 Message
     */
    private Message convertToInternalMessage(org.springframework.ai.chat.messages.Message springMessage) {
        Message message = new Message();
        // Spring AI 的 Message 接口可能没有 getId() 方法，直接生成 UUID
        message.setId(String.valueOf(IdUtil.getSnowflake().nextId()));

        if (springMessage instanceof SystemMessage) {
            message.setRole("system");
            message.setContent(((SystemMessage) springMessage).getText());
        } else if (springMessage instanceof UserMessage) {
            message.setRole("user");
            message.setContent(((UserMessage) springMessage).getText());
        } else if (springMessage instanceof AssistantMessage) {
            message.setRole("assistant");
            message.setContent(((AssistantMessage) springMessage).getText());
        } else {
            // 尝试通过反射获取内容
            message.setRole("system");
            try {
                String text = (String) springMessage.getClass()
                    .getMethod("getText")
                    .invoke(springMessage);
                message.setContent(text);
            } catch (Exception e) {
                log.warn("Failed to extract content from message: " + springMessage.getClass(), e);
                message.setContent("");
            }
        }

        message.setTimestamp(java.time.LocalDateTime.now());
        return message;
    }

    /**
     * 将项目内部的 Message 转换为 Spring AI 的 Message
     */
    private org.springframework.ai.chat.messages.Message convertToSpringMessage(Message internalMessage) {
        String role = internalMessage.getRole();
        String content = internalMessage.getContent();

        if (content == null) {
            content = "";
        }

        switch (role) {
            case "system":
                return new SystemMessage(content);
            case "user":
                return new UserMessage(content);
            case "assistant":
                return new AssistantMessage(content);
            default:
                log.warn("Unknown message role: {}, treating as system message", role);
                return new SystemMessage(content);
        }
    }
}

