package com.alibaba.cloud.ai.copilot.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统提示词记忆测试
 * 验证系统提示词只在第一次对话时添加到记忆中
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class SystemPromptMemoryTest {

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    @Qualifier("chatMemoryConversationService")
    private ConversationService conversationService;

    @Test
    public void testSystemPromptOnlyAddedOnce() {
        String userId = "test_system_prompt_001";
        String conversationId = conversationService.createNewConversation(userId);

        try {
            // 模拟第一次对话 - 应该包含系统提示词
            SystemMessage systemMessage = new SystemMessage("You are a helpful AI assistant.");
            chatMemory.add(conversationId, systemMessage);
            
            UserMessage userMessage1 = new UserMessage("Hello, my name is Alice");
            chatMemory.add(conversationId, userMessage1);
            
            AssistantMessage assistantMessage1 = new AssistantMessage("Hello Alice! How can I help you today?");
            chatMemory.add(conversationId, assistantMessage1);

            // 验证第一次对话后的记忆内容
            List<Message> messages1 = chatMemory.get(conversationId);
            assertEquals(3, messages1.size());
            assertTrue(messages1.get(0) instanceof SystemMessage);
            assertTrue(messages1.get(1) instanceof UserMessage);
            assertTrue(messages1.get(2) instanceof AssistantMessage);
            
            log.info("First conversation - Messages count: {}", messages1.size());

            // 模拟第二次对话 - 不应该再添加系统提示词
            UserMessage userMessage2 = new UserMessage("What is my name?");
            chatMemory.add(conversationId, userMessage2);
            
            AssistantMessage assistantMessage2 = new AssistantMessage("Your name is Alice.");
            chatMemory.add(conversationId, assistantMessage2);

            // 验证第二次对话后的记忆内容
            List<Message> messages2 = chatMemory.get(conversationId);
            assertEquals(5, messages2.size());
            
            // 验证只有一个系统消息（在开头）
            long systemMessageCount = messages2.stream()
                .filter(msg -> msg instanceof SystemMessage)
                .count();
            assertEquals(1, systemMessageCount, "Should only have one system message");
            
            // 验证系统消息仍然在第一位
            assertTrue(messages2.get(0) instanceof SystemMessage);
            assertEquals("You are a helpful AI assistant.", messages2.get(0).getText());
            
            log.info("Second conversation - Messages count: {}, System messages: {}", 
                    messages2.size(), systemMessageCount);

            // 模拟第三次对话
            UserMessage userMessage3 = new UserMessage("Tell me a joke");
            chatMemory.add(conversationId, userMessage3);
            
            AssistantMessage assistantMessage3 = new AssistantMessage("Why did the AI go to therapy? Because it had too many deep learning issues!");
            chatMemory.add(conversationId, assistantMessage3);

            // 验证第三次对话后的记忆内容
            List<Message> messages3 = chatMemory.get(conversationId);
            assertEquals(7, messages3.size());
            
            // 再次验证只有一个系统消息
            long systemMessageCount3 = messages3.stream()
                .filter(msg -> msg instanceof SystemMessage)
                .count();
            assertEquals(1, systemMessageCount3, "Should still only have one system message");
            
            log.info("Third conversation - Messages count: {}, System messages: {}", 
                    messages3.size(), systemMessageCount3);

        } finally {
            // 清理测试数据
            conversationService.clearUserConversations(userId);
        }
    }

    @Test
    public void testMultipleConversationsSystemPromptIsolation() {
        String userId1 = "test_system_prompt_002";
        String userId2 = "test_system_prompt_003";
        
        String conversationId1 = conversationService.createNewConversation(userId1);
        String conversationId2 = conversationService.createNewConversation(userId2);

        try {
            // 用户1的对话 - 使用系统提示词A
            SystemMessage systemMessage1 = new SystemMessage("You are a coding assistant.");
            chatMemory.add(conversationId1, systemMessage1);
            UserMessage userMessage1 = new UserMessage("Help me with Java");
            chatMemory.add(conversationId1, userMessage1);

            // 用户2的对话 - 使用系统提示词B
            SystemMessage systemMessage2 = new SystemMessage("You are a creative writer.");
            chatMemory.add(conversationId2, systemMessage2);
            UserMessage userMessage2 = new UserMessage("Write a story");
            chatMemory.add(conversationId2, userMessage2);

            // 验证会话隔离
            List<Message> conversation1 = chatMemory.get(conversationId1);
            List<Message> conversation2 = chatMemory.get(conversationId2);

            assertEquals(2, conversation1.size());
            assertEquals(2, conversation2.size());
            
            // 验证不同的系统提示词
            assertTrue(conversation1.get(0).getText().contains("coding assistant"));
            assertTrue(conversation2.get(0).getText().contains("creative writer"));
            
            log.info("Conversation isolation test passed");

        } finally {
            // 清理测试数据
            conversationService.clearUserConversations(userId1);
            conversationService.clearUserConversations(userId2);
        }
    }

    @Test
    public void testMemoryWindowWithSystemPrompt() {
        String userId = "test_system_prompt_004";
        String conversationId = conversationService.createNewConversation(userId);

        try {
            // 添加系统提示词
            SystemMessage systemMessage = new SystemMessage("You are a helpful assistant.");
            chatMemory.add(conversationId, systemMessage);

            // 添加大量对话，测试消息窗口限制
            for (int i = 1; i <= 25; i++) {
                UserMessage userMessage = new UserMessage("Question " + i);
                chatMemory.add(conversationId, userMessage);
                
                AssistantMessage assistantMessage = new AssistantMessage("Answer " + i);
                chatMemory.add(conversationId, assistantMessage);
            }

            // 验证消息窗口限制（默认20条）
            List<Message> messages = chatMemory.get(conversationId);
            assertEquals(20, messages.size());
            
            // 验证系统消息是否被保留（应该在窗口策略中被保留）
            // 注意：这取决于具体的MessageWindowChatMemory实现
            log.info("Memory window test - Final message count: {}", messages.size());
            
            // 检查最新的消息是否是最近的对话
            Message lastMessage = messages.get(messages.size() - 1);
            assertTrue(lastMessage.getText().contains("Answer"));

        } finally {
            // 清理测试数据
            conversationService.clearUserConversations(userId);
        }
    }
}
