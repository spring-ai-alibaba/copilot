package com.alibaba.cloud.ai.copilot.rag.service.impl;

import com.alibaba.cloud.ai.copilot.rag.config.RagProperties;
import com.alibaba.cloud.ai.copilot.rag.service.DocumentSplitterService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切割服务实现类
 * 基于LangChain4j的DocumentSplitters实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSplitterServiceImpl implements DocumentSplitterService {

    private final RagProperties ragProperties;

    @Override
    public List<TextSegment> splitDocument(Document document, int chunkSize, int chunkOverlap) {
        if (document == null || document.text() == null || document.text().trim().isEmpty()) {
            log.warn("文档内容为空，跳过切割");
            return new ArrayList<>();
        }

        try {
            // 创建LangChain4j的Document
            Document langchainDoc = Document.from(document.text());
            // 创建文档切割器
            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            // 执行切割
            List<TextSegment> segments = splitter.split(langchainDoc);
            // 为每个片段添加原始文档的元数据
            if (document.metadata() != null) {
                for (TextSegment segment : segments) {
                    if (segment.metadata() != null) {
                        segment.metadata().putAll(document.metadata().toMap());
                    }
                }
            }
            return segments;

        } catch (Exception e) {
            log.error("文档切割失败", e);
            throw new RuntimeException("文档切割失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TextSegment> splitDocument(Document document) {
        RagProperties.DocumentProcessing processing = ragProperties.getDocumentProcessing();
        return splitDocument(document, processing.getChunkSize(), processing.getChunkOverlap());
    }

    @Override
    public List<TextSegment> splitDocuments(List<Document> documents, int chunkSize, int chunkOverlap) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        List<TextSegment> allChunks = new ArrayList<>();
        for (Document document : documents) {
            try {
                List<TextSegment> chunks = splitDocument(document, chunkSize, chunkOverlap);
                allChunks.addAll(chunks);
            } catch (Exception e) {
                log.error("切割文档失败，跳过该文档:", e);
            }
        }

        return allChunks;
    }

    @Override
    public List<TextSegment> splitDocuments(List<Document> documents) {
        RagProperties.DocumentProcessing processing = ragProperties.getDocumentProcessing();
        return splitDocuments(documents, processing.getChunkSize(), processing.getChunkOverlap());
    }

    @Override
    public List<TextSegment> splitDocumentByTokens(Document document, int maxTokens, int overlapTokens) {
        if (document == null || document.text() == null || document.text().trim().isEmpty()) {
            log.warn("文档内容为空，跳过切割");
            return new ArrayList<>();
        }

        try {
            // 创建文档切割器
            DocumentSplitter splitter = DocumentSplitters.recursive(maxTokens, overlapTokens);

            // 执行切割
            List<TextSegment> segments = splitter.split(document);

            return segments;

        } catch (Exception e) {
            log.error("基于Token的文档切割失败", e);
            throw new RuntimeException("基于Token的文档切割失败: " + e.getMessage(), e);
        }
    }


    /**
     * 获取推荐的切块大小（基于Token数量）
     */
    public int getRecommendedChunkSizeInTokens() {
        return ragProperties.getDocumentProcessing().getMaxTokensPerChunk();
    }

    /**
     * 获取推荐的重叠大小（基于Token数量）
     */
    public int getRecommendedOverlapInTokens() {
        return ragProperties.getDocumentProcessing().getOverlapTokens();
    }
}
