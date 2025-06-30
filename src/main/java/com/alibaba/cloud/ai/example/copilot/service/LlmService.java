package com.alibaba.cloud.ai.example.copilot.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM服务
 * 提供统一的LLM访问接口
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private List<io.modelcontextprotocol.client.McpSyncClient> mcpSyncClients;

    private ChatClient chatClient;

    /**
     * 初始化ChatClient
     */
    @PostConstruct
    public void initChatClient() {
        logger.info("初始化LlmService的ChatClient实例");
        chatClient = chatClientBuilder
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClients))
                .build();
    }

    /**
     * 获取ChatClient实例
     * @return ChatClient实例
     */
    public ChatClient getChatClient() {
        return chatClient;
    }
}
