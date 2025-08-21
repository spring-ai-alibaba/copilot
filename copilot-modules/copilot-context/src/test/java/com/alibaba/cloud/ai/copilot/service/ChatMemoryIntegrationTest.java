package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chat Memory集成测试
 * 测试聊天记忆功能的存储和检索
 */
@SpringBootTest
@ActiveProfiles("test")
public class ChatMemoryIntegrationTest {

    @Autowired
    @Qualifier("chatMemoryConversationService")
    private ConversationService conversationService;

    @Autowired
    private SimpleChatMemoryService chatMemoryService;

    @Test
    public void testChatMemoryBasicOperations() {
        String userId = "test_user_001";

        // 测试添加用户消息
        chatMemoryService.addUserMessage(userId, "Hello, my name is Alice");

        // 测试添加助手回复
        chatMemoryService.addAssistantMessage(userId, "Hello Alice! Nice to meet you.");

        // 测试添加第二轮对话
        chatMemoryService.addUserMessage(userId, "What is my name?");
        chatMemoryService.addAssistantMessage(userId, "Your name is Alice.");

        // 检索对话历史
        List<ChatMessage> messages = chatMemoryService.getConversationHistory(userId);

        assertNotNull(messages);
        assertEquals(4, messages.size());

        // 验证消息顺序和内容
        assertEquals("user", messages.get(0).getRole());
        assertEquals("Hello, my name is Alice", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("Hello Alice! Nice to meet you.", messages.get(1).getContent());
        assertEquals("user", messages.get(2).getRole());
        assertEquals("What is my name?", messages.get(2).getContent());
        assertEquals("assistant", messages.get(3).getRole());
        assertEquals("Your name is Alice.", messages.get(3).getContent());

        // 清理测试数据
        chatMemoryService.clearConversationHistory(userId);
    }

    @Test
    public void testConversationService() {
        String userId = "test_user_002";

        // 测试创建新会话
        String conversationId1 = conversationService.createNewConversation(userId);
        assertNotNull(conversationId1);
        assertTrue(conversationId1.startsWith("conv_" + userId));

        // 测试获取或创建会话（应该返回现有会话）
        String conversationId2 = conversationService.getOrCreateConversationId(userId);
        assertEquals(conversationId1, conversationId2);

        // 测试获取用户会话列表
        List<String> conversations = conversationService.getUserConversations(userId);
        assertTrue(conversations.contains(conversationId1));

        // 测试删除会话
        conversationService.deleteConversation(conversationId1);
        conversations = conversationService.getUserConversations(userId);
        assertFalse(conversations.contains(conversationId1));
    }

    @Test
    public void testMemoryWindowLimit() {
        String userId = "test_user_003";

        // 添加超过窗口限制的消息（默认20条）
        for (int i = 1; i <= 25; i++) {
            chatMemoryService.addUserMessage(userId, "Message " + i);
            chatMemoryService.addAssistantMessage(userId, "Response " + i);
        }

        // 检索消息，应该只保留最近的20条
        List<ChatMessage> messages = chatMemoryService.getConversationHistory(userId);
        assertNotNull(messages);
        assertEquals(20, messages.size());

        // 验证保留的是最新的消息
        assertTrue(messages.get(messages.size() - 1).getContent().contains("Response 25"));
        assertTrue(messages.get(messages.size() - 2).getContent().contains("Message 25"));

        // 清理测试数据
        chatMemoryService.clearConversationHistory(userId);
    }

    @Test
    public void testMultipleConversations() {
        String userId1 = "test_user_004";
        String userId2 = "test_user_005";

        // 在不同会话中添加消息
        chatMemoryService.addUserMessage(userId1, "User 1 message");
        chatMemoryService.addUserMessage(userId2, "User 2 message");

        // 验证会话隔离
        List<ChatMessage> messages1 = chatMemoryService.getConversationHistory(userId1);
        List<ChatMessage> messages2 = chatMemoryService.getConversationHistory(userId2);

        assertEquals(1, messages1.size());
        assertEquals(1, messages2.size());
        assertEquals("User 1 message", messages1.get(0).getContent());
        assertEquals("User 2 message", messages2.get(0).getContent());

        // 清理测试数据
        chatMemoryService.clearConversationHistory(userId1);
        chatMemoryService.clearConversationHistory(userId2);
    }

    @Test
    public void testClearConversations() {
        String userId = "test_user_006";

        // 添加一些消息
        chatMemoryService.addUserMessage(userId, "Test message");

        // 验证消息存在
        List<ChatMessage> messages = chatMemoryService.getConversationHistory(userId);
        assertEquals(1, messages.size());

        // 清除用户会话历史
        chatMemoryService.clearConversationHistory(userId);

        // 验证消息已清除
        messages = chatMemoryService.getConversationHistory(userId);
        assertTrue(messages.isEmpty());
    }
}
