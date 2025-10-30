package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.model.ChatMessage;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.SimpleChatMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat Memory管理控制器
 * 提供会话记忆的管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/memory")
public class ChatMemoryController {

    private final ConversationService conversationService;
    private final SimpleChatMemoryService chatMemoryService;

    public ChatMemoryController(
            @Qualifier("chatMemoryConversationService") ConversationService conversationService,
            SimpleChatMemoryService chatMemoryService) {
        this.conversationService = conversationService;
        this.chatMemoryService = chatMemoryService;
    }

    /**
     * 获取用户的所有会话
     */
    @GetMapping("/conversations/{userId}")
    public List<String> getUserConversations(@PathVariable String userId) {
        return conversationService.getUserConversations(userId);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/conversations/{userId}")
    public Map<String, String> createNewConversation(@PathVariable String userId) {
        String conversationId = conversationService.createNewConversation(userId);
        return Map.of("conversationId", conversationId);
    }

    /**
     * 获取用户的会话历史
     */
    @GetMapping("/users/{userId}/messages")
    public List<ChatMessage> getConversationHistory(@PathVariable String userId) {
        return chatMemoryService.getConversationHistory(userId);
    }

    /**
     * 获取用户的会话历史（限制数量）
     */
    @GetMapping("/users/{userId}/messages/{limit}")
    public List<ChatMessage> getConversationHistory(@PathVariable String userId, @PathVariable int limit) {
        return chatMemoryService.getConversationHistory(userId, limit);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Map<String, String> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return Map.of("status", "deleted");
    }

    /**
     * 清除用户的所有会话
     */
    @DeleteMapping("/users/{userId}/conversations")
    public Map<String, String> clearUserConversations(@PathVariable String userId) {
        conversationService.clearUserConversations(userId);
        return Map.of("status", "cleared");
    }

    /**
     * 清除用户的会话历史
     */
    @DeleteMapping("/users/{userId}/messages")
    public Map<String, String> clearConversationHistory(@PathVariable String userId) {
        chatMemoryService.clearConversationHistory(userId);
        return Map.of("status", "cleared");
    }

    /**
     * 获取用户的消息数量
     */
    @GetMapping("/users/{userId}/message-count")
    public Map<String, Integer> getMessageCount(@PathVariable String userId) {
        int count = chatMemoryService.getMessageCount(userId);
        return Map.of("count", count);
    }

    /**
     * 获取当前会话ID
     */
    @GetMapping("/users/{userId}/current-conversation")
    public Map<String, String> getCurrentConversation(@PathVariable String userId) {
        String conversationId = conversationService.getOrCreateConversationId(userId);
        return Map.of("conversationId", conversationId);
    }
}
