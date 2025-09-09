package com.alibaba.cloud.ai.copilot.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
// 移除Spring AI相关导入，只使用LangChain4j
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Embedding模型配置类
 * 配置OpenAI Embedding模型用于文本向量化
 */
@Slf4j
@Configuration
public class EmbeddingConfiguration {

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${copilot.rag.embedding.model:text-embedding-ada-002}")
    private String embeddingModel;

    @Value("${copilot.rag.embedding.timeout:60}")
    private int timeoutSeconds;

    @Value("${copilot.rag.embedding.max-retries:3}")
    private int maxRetries;

    /**
     * 配置LangChain4j的OpenAI Embedding模型
     */
    @Bean("langchain4jEmbeddingModel")
    @Primary
    @ConditionalOnProperty(name = "copilot.rag.embedding.provider", havingValue = "openai", matchIfMissing = true)
    public EmbeddingModel langchain4jOpenAiEmbeddingModel() {
        log.info("配置LangChain4j OpenAI Embedding模型: {}", embeddingModel);

        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI API Key未配置，请设置spring.ai.openai.api-key");
        }

        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .baseUrl(openAiBaseUrl)
                .modelName(embeddingModel)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(log.isDebugEnabled())
                .logResponses(log.isDebugEnabled())
                .build();
    }


    /**
     * 配置本地Embedding模型（备用方案）
     */
    @Bean("localEmbeddingModel")
    @ConditionalOnProperty(name = "copilot.rag.embedding.provider", havingValue = "local")
    public EmbeddingModel localEmbeddingModel() {
        log.info("配置本地Embedding模型");
        // 使用all-MiniLM-L6-v2模型作为本地备用方案
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
