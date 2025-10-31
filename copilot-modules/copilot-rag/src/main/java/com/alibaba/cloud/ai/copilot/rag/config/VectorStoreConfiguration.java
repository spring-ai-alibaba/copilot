package com.alibaba.cloud.ai.copilot.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量存储配置类
 * 配置PgVector向量数据库
 */
@Slf4j
@Configuration
public class VectorStoreConfiguration {

    @Value("${copilot.rag.pgvector.host:localhost}")
    private String pgHost;

    @Value("${copilot.rag.pgvector.port:5432}")
    private int pgPort;

    @Value("${copilot.rag.pgvector.database:copilot_rag}")
    private String pgDatabase;

    @Value("${copilot.rag.pgvector.username:postgres}")
    private String pgUsername;

    @Value("${copilot.rag.pgvector.password:password}")
    private String pgPassword;

    @Value("${copilot.rag.pgvector.table:copilot_embeddings}")
    private String tableName;

    @Value("${copilot.rag.pgvector.dimension:1536}")
    private int dimension;

    /**
     * 配置PgVector向量存储
     */
    //@Bean("pgVectorEmbeddingStore")
    @Primary
    @ConditionalOnProperty(name = "copilot.rag.vector-store.provider", havingValue = "pgvector", matchIfMissing = true)
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(@Qualifier("langchain4jEmbeddingModel") EmbeddingModel embeddingModel) {
        log.info("配置PgVector向量存储: {}:{}/{}, 表: {}", pgHost, pgPort, pgDatabase, tableName);

        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUsername)
                .password(pgPassword)
                .table(tableName)
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    /**
     * 为不同知识库创建独立的向量存储
     */
    public EmbeddingStore<TextSegment> createEmbeddingStoreForKnowledgeBase(String kbKey, EmbeddingModel embeddingModel) {
        String kbTableName = tableName + "_" + kbKey.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        log.info("为知识库 {} 创建向量存储，表名: {}", kbKey, kbTableName);

        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUsername)
                .password(pgPassword)
                .table(kbTableName)
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }
}
