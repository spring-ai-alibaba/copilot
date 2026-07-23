package com.alibaba.cloud.ai.copilot.knowledge.config;

import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeMilvusStore;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库向量存储配置（agentscope 2.0 + Milvus SDK）。
 *
 * <p>embedding 用 agentscope {@link OpenAITextEmbedding}
 * 向量存储用本地 {@link KnowledgeMilvusStore}</p>
 *
 * @author RobustH
 */
@Slf4j
@Configuration
public class MilvusVectorStoreConfig {

    @Value("${spring.ai.openai.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.embedding.base-url:https://api.siliconflow.cn}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.options.model:BAAI/bge-large-zh-v1.5}")
    private String embeddingModelName;

    @Value("${spring.ai.vectorstore.milvus.client.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port:19530}")
    private Integer port;

    @Value("${spring.ai.vectorstore.milvus.database-name:default}")
    private String databaseName;

    @Value("${spring.ai.vectorstore.milvus.collection-name:copilot_knowledge}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension:1024}")
    private Integer embeddingDimension;

    @Value("${spring.ai.vectorstore.milvus.client.username:}")
    private String username;

    @Value("${spring.ai.vectorstore.milvus.client.password:}")
    private String password;

    /**
     * OpenAI 兼容的文本嵌入模型（SiliconFlow bge-large-zh-v1.5，1024 维）。
     * apiKey 未配置时返回 null，知识库功能降级关闭，应用仍可启动。
     */
    @Bean(name = "openAiEmbeddingModel")
    public EmbeddingModel openAiEmbeddingModel() {
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            log.warn("嵌入模型 apiKey 未配置（spring.ai.openai.embedding.api-key），知识库功能已禁用");
            return null;
        }
        try {
            OpenAITextEmbedding model = OpenAITextEmbedding.builder()
                    .apiKey(embeddingApiKey)
                    .baseUrl(embeddingBaseUrl)
                    .modelName(embeddingModelName)
                    .dimensions(embeddingDimension)
                    .build();
            log.info("OpenAI 兼容嵌入模型已初始化: model={}, dim={}, baseUrl={}", embeddingModelName, embeddingDimension, embeddingBaseUrl);
            return model;
        } catch (Exception e) {
            log.warn("嵌入模型初始化失败，知识库功能已禁用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Milvus 向量存储。Milvus 不可用时返回 null，知识库功能降级关闭。
     */
    @Bean(destroyMethod = "close")
    public KnowledgeMilvusStore knowledgeMilvusStore() {
        String uri = String.format("http://%s:%d", host, port);
        try {
            KnowledgeMilvusStore store = new KnowledgeMilvusStore(
                    uri, databaseName, collectionName, embeddingDimension, username, password);
            log.info("Milvus 向量存储已初始化: uri={}, collection={}, dim={}", uri, collectionName, embeddingDimension);
            return store;
        } catch (Exception e) {
            log.warn("Milvus 不可用，知识库功能已禁用（应用继续正常启动）: {}", e.getMessage());
            return null;
        }
    }
}
