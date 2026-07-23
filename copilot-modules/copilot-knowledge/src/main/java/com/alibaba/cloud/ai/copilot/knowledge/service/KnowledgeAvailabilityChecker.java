package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeMilvusStore;
import io.agentscope.core.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 向量数据库（Milvus）可用性检查器。
 * KnowledgeMilvusStore / 嵌入模型 bean 为 null（配置缺失或初始化失败）时，知识库功能禁用。
 */
@Slf4j
@Component
public class KnowledgeAvailabilityChecker {

    @Autowired(required = false)
    private KnowledgeMilvusStore milvusStore;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        if (isAvailable()) {
            log.info("向量数据库连接正常，知识库功能已启用");
        } else {
            log.warn("向量数据库或嵌入模型不可用，知识库功能已禁用（不影响其他功能正常使用）");
        }
    }

    public boolean isAvailable() {
        return milvusStore != null && embeddingModel != null;
    }
}
