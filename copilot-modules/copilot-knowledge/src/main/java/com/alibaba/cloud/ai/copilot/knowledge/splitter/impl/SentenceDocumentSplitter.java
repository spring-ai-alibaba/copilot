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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 基于段落的文档切割器
 *
 * <p>使用 agentscope {@link TextChunker}（PARAGRAPH 策略）保留按语义边界聚合、保持语义完整性的目标，
 * 适合长文本文档与 RAG 场景。</p>
 *
 * @author RobustH
 */
@Slf4j
@Component
public class SentenceDocumentSplitter implements DocumentSplitter {

    private final int chunkSize;

    public SentenceDocumentSplitter(
            @Value("${copilot.knowledge.splitter.chunk-size:500}") int chunkSize) {
        this.chunkSize = chunkSize;
        log.info("初始化 SentenceDocumentSplitter: chunkSize={}", chunkSize);
    }

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        try {
            List<String> chunks = TextChunker.chunkText(content, chunkSize, SplitStrategy.PARAGRAPH, 0);

            log.debug("文件 {} 使用 PARAGRAPH 策略切割为 {} 个 chunks", filePath, chunks.size());

            List<KnowledgeChunk> result = new ArrayList<>();
            int index = 0;
            for (String text : chunks) {
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                result.add(createKnowledgeChunk(text, filePath, index++));
            }
            return result;
        } catch (Exception e) {
            log.error("段落切割失败: {}", filePath, e);
            return List.of(createKnowledgeChunk(content, filePath, 0));
        }
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.SENTENCE;
    }

    private KnowledgeChunk createKnowledgeChunk(String content, String filePath, int index) {
        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .filePath(filePath)
                .fileType(KnowledgeCategory.FileType.DOCUMENT)
                .language(detectLanguage(content))
                .createdAt(System.currentTimeMillis())
                .contentHash(DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8)))
                .chunkIndex(index)
                .metadata(Collections.emptyMap())
                .build();
    }

    /**
     * 简单的语言检测：根据内容是否包含中文字符判断。
     */
    private String detectLanguage(String content) {
        if (content == null || content.isEmpty()) {
            return "unknown";
        }
        boolean hasChinese = content.chars()
                .anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
        return hasChinese ? "zh" : "en";
    }
}
