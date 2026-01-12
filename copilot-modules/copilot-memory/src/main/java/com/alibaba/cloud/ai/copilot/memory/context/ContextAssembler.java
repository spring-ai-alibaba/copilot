package com.alibaba.cloud.ai.copilot.memory.context;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.longterm.MemoryContentLoader;
import com.alibaba.cloud.ai.copilot.memory.longterm.ProjectMemoryLoader;
import com.alibaba.cloud.ai.copilot.memory.shortterm.CompressibleChatMemory;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的上下文组装器
 * 将长期记忆、短期记忆、工作记忆整合
 *
 * @author better
 */
@Slf4j
@Service
public class ContextAssembler {

    private final MemoryContentLoader memoryLoader;
    private final CompressibleChatMemory chatMemory;
    private final TokenCounterService tokenCounterService;
    private final ProjectMemoryLoader projectMemoryLoader;

    public ContextAssembler(
            MemoryContentLoader memoryLoader,
            CompressibleChatMemory chatMemory,
            TokenCounterService tokenCounterService,
            ProjectMemoryLoader projectMemoryLoader) {
        this.memoryLoader = memoryLoader;
        this.chatMemory = chatMemory;
        this.tokenCounterService = tokenCounterService;
        this.projectMemoryLoader = projectMemoryLoader;
    }

    /**
     * 组装完整上下文
     *
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param userMessage 当前用户消息
     * @param workingDirectory 工作目录
     * @param modelName 模型名称
     * @return 组装后的消息列表
     */
    public List<Message> assembleContext(
            String conversationId,
            String userId,
            Message userMessage,
            Path workingDirectory,
            String modelName) {
        List<Message> context = new ArrayList<>();

        // 1. 加载长期记忆（项目知识）
        Path projectRoot = projectMemoryLoader.findProjectRoot(workingDirectory);
        String longTermMemory = memoryLoader.loadAndMergeMemories(workingDirectory, projectRoot);

        // 2. 构建系统提示词（包含长期记忆）
        String systemPrompt = buildSystemPrompt(longTermMemory);
        Message systemMessage = new Message();
        systemMessage.setId("system_" + System.currentTimeMillis());
        systemMessage.setRole("system");
        systemMessage.setContent(systemPrompt);
        context.add(systemMessage);

        // 3. 加载短期记忆（历史对话）
        List<Message> shortTermMemory = chatMemory.get(conversationId);
        if (shortTermMemory != null && !shortTermMemory.isEmpty()) {
            context.addAll(shortTermMemory);
        }

        // 4. 添加当前用户消息（工作记忆）
        context.add(userMessage);

        // 5. 计算总 Token 数
        int totalTokens = tokenCounterService.countTokens(context, modelName);
        log.info("Assembled context: {} messages, {} tokens for conversation {}",
                context.size(), totalTokens, conversationId);

        return context;
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String longTermMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个智能编程助手。\n\n");

        // 添加长期记忆
        if (longTermMemory != null && !longTermMemory.isEmpty()) {
            prompt.append("以下是你需要了解的项目上下文和用户偏好：\n\n");
            prompt.append(longTermMemory);
            prompt.append("\n\n");
        }

        prompt.append("请根据以上信息，为用户提供准确、有针对性的帮助。");
        return prompt.toString();
    }
}

