package com.alibaba.cloud.ai.copilot.memory.compression.impl;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressedSummary;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressionService;
import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 压缩服务实现
 * 使用 AI 模型将对话历史压缩成结构化摘要
 *
 * @author better
 */
@Slf4j
@Service
public class CompressionServiceImpl implements CompressionService {

    private final MemoryProperties memoryProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final TokenCounterService tokenCounterService;
    private volatile ChatModel cachedChatModel;
    private volatile String cachedBeanName;

    public CompressionServiceImpl(
            MemoryProperties memoryProperties,
            ObjectMapper objectMapper,
            ApplicationContext applicationContext,
            TokenCounterService tokenCounterService) {
        this.memoryProperties = memoryProperties;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
        this.tokenCounterService = tokenCounterService;
    }

    @Override
    public CompressedSummary compressMessages(List<Message> messages, String modelName) {
        if (messages == null || messages.isEmpty()) {
            return CompressedSummary.builder()
                    .originalMessageCount(0)
                    .build();
        }

        try {
            // 获取压缩模型的 Token 限制
            String compressionModel = memoryProperties.getShortTerm().getCompression().getCompressionModel();
            ChatModel chatModel = resolveChatModel();
            
            // 计算压缩提示词的基础 Token 数（系统提示词 + 格式说明）
            String basePrompt = buildBaseCompressionPrompt();
            int baseTokens = tokenCounterService.countTokens(basePrompt, compressionModel);
            
            // 获取模型的 Token 限制（留出响应空间，使用 80% 作为安全边界）
            int modelLimit = tokenCounterService.getModelTokenLimit(compressionModel);
            int availableTokens = (int) (modelLimit * 0.8) - baseTokens; // 保留 20% 给响应
            
            // 计算要压缩的消息的 Token 数
            int messagesTokens = tokenCounterService.countTokens(messages, compressionModel);
            
            log.info("Compressing {} messages ({} tokens) using model {} (limit: {}, available: {})", 
                    messages.size(), messagesTokens, compressionModel, modelLimit, availableTokens);
            
            // 如果消息 Token 数超过可用限制，使用分块压缩
            if (messagesTokens > availableTokens) {
                log.info("Messages exceed token limit, using chunked compression strategy");
                return compressMessagesInChunks(messages, compressionModel, chatModel, availableTokens);
            }
            
            // 单次压缩
            String compressionPrompt = buildCompressionPrompt(messages);
            
            // 创建 ChatOptions 指定模型名称
            org.springframework.ai.openai.OpenAiChatOptions chatOptions = 
                    org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(compressionModel)
                            .temperature(0.3)  // 压缩任务使用较低温度，更稳定
                            .build();
            
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("你是一个对话历史压缩专家。请将对话历史压缩成结构化摘要。"),
                    new UserMessage(compressionPrompt)
            ), chatOptions);

            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();

            // 解析响应为 CompressedSummary
            CompressedSummary summary = parseCompressionResponse(content);
            summary.setOriginalMessageCount(messages.size());

            log.info("Successfully compressed {} messages into summary", messages.size());
            return summary;

        } catch (Exception e) {
            log.error("Failed to compress messages: {}", e.getMessage(), e);
            // 检查是否是 API Key 问题
            if (e.getMessage() != null && e.getMessage().contains("InvalidApiKey")) {
                log.error("Invalid API Key for compression model. Please check your API key configuration.");
            }
            // 返回一个标记失败的摘要
            return CompressedSummary.builder()
                    .mainTopics(List.of("对话历史压缩失败"))
                    .originalMessageCount(messages.size())
                    .build();
        }
    }

    /**
     * 根据配置解析压缩所使用的 ChatModel
     */
    private ChatModel resolveChatModel() {
        String beanName = memoryProperties.getShortTerm()
                .getCompression()
                .getChatModelBeanName();

        if (cachedChatModel != null && Objects.equals(beanName, cachedBeanName)) {
            return cachedChatModel;
        }

        synchronized (this) {
            if (cachedChatModel == null || !Objects.equals(beanName, cachedBeanName)) {
                cachedChatModel = applicationContext.getBean(beanName, ChatModel.class);
                cachedBeanName = beanName;
                log.info("Compression service resolved ChatModel bean: {}", beanName);
            }
            return cachedChatModel;
        }
    }

    /**
     * 分块压缩消息列表
     * 当消息总 Token 数超过模型限制时，将消息分成多个块分别压缩，然后合并结果
     */
    private CompressedSummary compressMessagesInChunks(
            List<Message> messages, 
            String compressionModel, 
            ChatModel chatModel, 
            int availableTokens) {
        
        List<CompressedSummary> chunkSummaries = new ArrayList<>();
        List<Message> currentChunk = new ArrayList<>();
        int currentChunkTokens = 0;
        
        // 将消息分成多个块
        for (Message message : messages) {
            int messageTokens = tokenCounterService.countTokens(message, compressionModel);
            
            // 如果当前块加上新消息会超过限制，先压缩当前块
            if (currentChunkTokens + messageTokens > availableTokens && !currentChunk.isEmpty()) {
                log.debug("Compressing chunk with {} messages ({} tokens)", 
                        currentChunk.size(), currentChunkTokens);
                CompressedSummary chunkSummary = compressSingleChunk(currentChunk, chatModel, compressionModel);
                chunkSummaries.add(chunkSummary);
                
                // 重置当前块
                currentChunk = new ArrayList<>();
                currentChunkTokens = 0;
            }
            
            // 如果单个消息就超过限制，跳过（这种情况很少见）
            if (messageTokens > availableTokens) {
                log.warn("Message exceeds token limit ({} > {}), skipping", 
                        messageTokens, availableTokens);
                continue;
            }
            
            currentChunk.add(message);
            currentChunkTokens += messageTokens;
        }
        
        // 压缩最后一个块
        if (!currentChunk.isEmpty()) {
            log.debug("Compressing final chunk with {} messages ({} tokens)", 
                    currentChunk.size(), currentChunkTokens);
            CompressedSummary chunkSummary = compressSingleChunk(currentChunk, chatModel, compressionModel);
            chunkSummaries.add(chunkSummary);
        }
        
        // 合并所有块的摘要
        return mergeChunkSummaries(chunkSummaries, messages.size());
    }
    
    /**
     * 压缩单个块
     */
    private CompressedSummary compressSingleChunk(List<Message> chunk, ChatModel chatModel, String modelName) {
        try {
            String compressionPrompt = buildCompressionPrompt(chunk);
            
            // 创建 ChatOptions 指定模型名称
            org.springframework.ai.openai.OpenAiChatOptions chatOptions = 
                    org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(0.3)  // 压缩任务使用较低温度，更稳定
                            .build();
            
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("你是一个对话历史压缩专家。请将对话历史压缩成结构化摘要。"),
                    new UserMessage(compressionPrompt)
            ), chatOptions);
            
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
            return parseCompressionResponse(content);
        } catch (Exception e) {
            log.error("Failed to compress chunk", e);
            return CompressedSummary.builder()
                    .mainTopics(List.of("分块压缩失败"))
                    .originalMessageCount(chunk.size())
                    .build();
        }
    }
    
    /**
     * 合并多个块的摘要
     */
    private CompressedSummary mergeChunkSummaries(List<CompressedSummary> chunkSummaries, int totalMessageCount) {
        CompressedSummary.CompressedSummaryBuilder builder = CompressedSummary.builder();
        
        List<String> allMainTopics = new ArrayList<>();
        List<String> allKeyDecisions = new ArrayList<>();
        List<CompressedSummary.CodeContext> allCodeContexts = new ArrayList<>();
        List<String> allUserRequirements = new ArrayList<>();
        List<String> allPendingTasks = new ArrayList<>();
        List<String> allTechnicalDetails = new ArrayList<>();
        
        for (CompressedSummary summary : chunkSummaries) {
            if (summary.getMainTopics() != null) {
                allMainTopics.addAll(summary.getMainTopics());
            }
            if (summary.getKeyDecisions() != null) {
                allKeyDecisions.addAll(summary.getKeyDecisions());
            }
            if (summary.getCodeContexts() != null) {
                allCodeContexts.addAll(summary.getCodeContexts());
            }
            if (summary.getUserRequirements() != null) {
                allUserRequirements.addAll(summary.getUserRequirements());
            }
            if (summary.getPendingTasks() != null) {
                allPendingTasks.addAll(summary.getPendingTasks());
            }
            if (summary.getTechnicalDetails() != null) {
                allTechnicalDetails.addAll(summary.getTechnicalDetails());
            }
        }
        
        builder.mainTopics(allMainTopics)
                .keyDecisions(allKeyDecisions)
                .codeContexts(allCodeContexts)
                .userRequirements(allUserRequirements)
                .pendingTasks(allPendingTasks)
                .technicalDetails(allTechnicalDetails)
                .originalMessageCount(totalMessageCount);
        
        log.info("Merged {} chunk summaries into final summary", chunkSummaries.size());
        return builder.build();
    }
    
    /**
     * 构建基础压缩提示词（不包含消息内容）
     */
    private String buildBaseCompressionPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下对话历史压缩成结构化摘要。\n\n");
        prompt.append("要求：\n");
        prompt.append("1. 保留所有关键信息（决策、代码逻辑、用户需求）\n");
        prompt.append("2. 省略重复和冗余内容\n");
        prompt.append("3. 使用 XML 格式输出\n\n");
        prompt.append("输出格式：\n");
        prompt.append("<conversation_summary>\n");
        prompt.append("  <main_topics>主要讨论的话题列表</main_topics>\n");
        prompt.append("  <key_decisions>\n");
        prompt.append("    <decision>做出的关键决策</decision>\n");
        prompt.append("  </key_decisions>\n");
        prompt.append("  <code_context>\n");
        prompt.append("    <file path=\"文件路径\">功能描述</file>\n");
        prompt.append("  </code_context>\n");
        prompt.append("  <user_requirements>\n");
        prompt.append("    <requirement>用户的需求和偏好</requirement>\n");
        prompt.append("  </user_requirements>\n");
        prompt.append("  <pending_tasks>\n");
        prompt.append("    <task>未完成的任务</task>\n");
        prompt.append("  </pending_tasks>\n");
        prompt.append("  <technical_details>\n");
        prompt.append("    <detail>技术细节和配置信息</detail>\n");
        prompt.append("  </technical_details>\n");
        prompt.append("</conversation_summary>\n\n");
        prompt.append("原始对话：\n");
        return prompt.toString();
    }

    /**
     * 构建压缩提示词
     */
    private String buildCompressionPrompt(List<Message> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下对话历史压缩成结构化摘要。\n\n");
        prompt.append("要求：\n");
        prompt.append("1. 保留所有关键信息（决策、代码逻辑、用户需求）\n");
        prompt.append("2. 省略重复和冗余内容\n");
        prompt.append("3. 使用 XML 格式输出\n\n");
        prompt.append("输出格式：\n");
        prompt.append("<conversation_summary>\n");
        prompt.append("  <main_topics>主要讨论的话题列表</main_topics>\n");
        prompt.append("  <key_decisions>\n");
        prompt.append("    <decision>做出的关键决策</decision>\n");
        prompt.append("  </key_decisions>\n");
        prompt.append("  <code_context>\n");
        prompt.append("    <file path=\"文件路径\">功能描述</file>\n");
        prompt.append("  </code_context>\n");
        prompt.append("  <user_requirements>\n");
        prompt.append("    <requirement>用户的需求和偏好</requirement>\n");
        prompt.append("  </user_requirements>\n");
        prompt.append("  <pending_tasks>\n");
        prompt.append("    <task>未完成的任务</task>\n");
        prompt.append("  </pending_tasks>\n");
        prompt.append("  <technical_details>\n");
        prompt.append("    <detail>技术细节和配置信息</detail>\n");
        prompt.append("  </technical_details>\n");
        prompt.append("</conversation_summary>\n\n");
        prompt.append("原始对话：\n");
        prompt.append(formatMessages(messages));

        return prompt.toString();
    }

    /**
     * 格式化消息列表
     */
    private String formatMessages(List<Message> messages) {
        StringBuilder formatted = new StringBuilder();
        for (Message message : messages) {
            formatted.append("[").append(message.getRole()).append("]: ");
            if (message.getContent() != null) {
                formatted.append(message.getContent());
            }
            formatted.append("\n");
        }
        return formatted.toString();
    }

    /**
     * 解析压缩响应
     */
    private CompressedSummary parseCompressionResponse(String response) {
        CompressedSummary.CompressedSummaryBuilder builder = CompressedSummary.builder();

        try {
            // 解析主要话题
            List<String> mainTopics = extractList(response, "<main_topics>(.*?)</main_topics>");
            builder.mainTopics(mainTopics);

            // 解析关键决策
            List<String> keyDecisions = extractList(response, "<decision>(.*?)</decision>");
            builder.keyDecisions(keyDecisions);

            // 解析代码上下文
            List<CompressedSummary.CodeContext> codeContexts = extractCodeContexts(response);
            builder.codeContexts(codeContexts);

            // 解析用户需求
            List<String> userRequirements = extractList(response, "<requirement>(.*?)</requirement>");
            builder.userRequirements(userRequirements);

            // 解析未完成任务
            List<String> pendingTasks = extractList(response, "<task>(.*?)</task>");
            builder.pendingTasks(pendingTasks);

            // 解析技术细节
            List<String> technicalDetails = extractList(response, "<detail>(.*?)</detail>");
            builder.technicalDetails(technicalDetails);

        } catch (Exception e) {
            log.warn("Failed to parse compression response, using fallback", e);
            // 如果解析失败，将整个响应作为主要话题
            builder.mainTopics(List.of(response));
        }

        return builder.build();
    }

    /**
     * 提取列表项
     */
    private List<String> extractList(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(text);
        return m.results()
                .map(mr -> mr.group(1).trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 提取代码上下文
     */
    private List<CompressedSummary.CodeContext> extractCodeContexts(String text) {
        Pattern p = Pattern.compile("<file path=\"(.*?)\">(.*?)</file>", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        return m.results()
                .map(mr -> CompressedSummary.CodeContext.builder()
                        .filePath(mr.group(1).trim())
                        .description(mr.group(2).trim())
                        .build())
                .toList();
    }
}

