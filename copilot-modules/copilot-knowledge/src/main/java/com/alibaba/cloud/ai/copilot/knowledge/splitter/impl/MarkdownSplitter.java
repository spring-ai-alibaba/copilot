package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Markdown 文档切割器
 *
 * <p>使用 agentscope {@link TextChunker}（CHARACTER 策略 + overlap）
 * 保留语义完整性，适合中文语境的 Markdown。</p>
 *
 * @author RobustH
 */
@Slf4j
@Component
public class MarkdownSplitter implements DocumentSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    public MarkdownSplitter(
            @Value("${copilot.knowledge.splitter.chunk-size:500}") int chunkSize,
            @Value("${copilot.knowledge.splitter.chunk-overlap:50}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        log.info("初始化 MarkdownSplitter: chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);
    }

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        try {
            List<String> chunks = TextChunker.chunkText(content, chunkSize, SplitStrategy.CHARACTER, chunkOverlap);

            log.debug("Markdown 文件 {} 切割为 {} 个 chunks", filePath, chunks.size());

            AtomicInteger index = new AtomicInteger(0);
            return chunks.stream()
                    .filter(chunk -> !chunk.trim().isEmpty())
                    .map(chunk -> createKnowledgeChunk(chunk, filePath, index.getAndIncrement()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Markdown 切割失败: {}", filePath, e);
            return List.of(createKnowledgeChunk(content, filePath, 0));
        }
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.RECURSIVE_CHARACTER;
    }

    private KnowledgeChunk createKnowledgeChunk(String content, String filePath, int index) {
        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .filePath(filePath)
                .fileType(KnowledgeCategory.FileType.DOCUMENT)
                .language("markdown")
                .createdAt(System.currentTimeMillis())
                .contentHash(DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8)))
                .chunkIndex(index)
                .metadata(Collections.emptyMap())
                .build();
    }

}
