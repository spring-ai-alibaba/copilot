package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.utils.FileTypeClassifier;
import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Token 文档切割器
 * 基于 agentscope {@link TextChunker}（TOKEN 策略）实现
 *
 * @author RobustH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenDocumentSplitter implements DocumentSplitter {

    private final FileTypeClassifier fileTypeClassifier;

    @Value("${copilot.knowledge.splitter.chunk-size:2000}")
    private int chunkSize;

    @Value("${copilot.knowledge.splitter.chunk-overlap:400}")
    private int chunkOverlap;

    @Value("${copilot.knowledge.splitter.min-chunk-size:100}")
    private int minChunkSize;

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        KnowledgeCategory.FileType fileType = fileTypeClassifier.classifyFileType(filePath);
        String language = fileTypeClassifier.detectLanguage(filePath);

        log.debug("切割文档: 路径={}, 类型={}, 语言={}, 长度={}",
                filePath, fileType, language, content.length());

        List<String> chunks = TextChunker.chunkText(content, chunkSize, SplitStrategy.TOKEN, chunkOverlap);

        List<KnowledgeChunk> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i);
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .id(UUID.randomUUID().toString())
                    .content(text)
                    .filePath(filePath)
                    .fileType(fileType)
                    .language(language)
                    .startLine(1)
                    .endLine(1)
                    .createdAt(System.currentTimeMillis())
                    .contentHash(DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8)))
                    .chunkIndex(i)
                    .metadata(java.util.Collections.emptyMap())
                    .build();
            result.add(chunk);
        }

        log.debug("文档已切割为 {} 个知识块: {}", result.size(), filePath);
        return result;
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.TOKEN;
    }
}
