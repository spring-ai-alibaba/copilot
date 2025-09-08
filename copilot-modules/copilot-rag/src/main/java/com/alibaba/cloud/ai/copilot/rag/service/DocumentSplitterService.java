package com.alibaba.cloud.ai.copilot.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 文档切割服务接口
 * 将长文档切割成适合向量化的小块
 */
public interface DocumentSplitterService {
    
    /**
     * 切割单个文档
     * @param document 待切割的文档
     * @param chunkSize 切块大小（字符数）
     * @param chunkOverlap 切块重叠大小（字符数）
     * @return 切割后的文档片段列表
     */
    List<TextSegment> splitDocument(Document document, int chunkSize, int chunkOverlap);

    /**
     * 使用默认配置切割文档
     * @param document 待切割的文档
     * @return 切割后的文档片段列表
     */
    List<TextSegment> splitDocument(Document document);

    /**
     * 批量切割文档
     * @param documents 待切割的文档列表
     * @param chunkSize 切块大小（字符数）
     * @param chunkOverlap 切块重叠大小（字符数）
     * @return 切割后的文档片段列表
     */
    List<TextSegment> splitDocuments(List<Document> documents, int chunkSize, int chunkOverlap);

    /**
     * 使用默认配置批量切割文档
     * @param documents 待切割的文档列表
     * @return 切割后的文档片段列表
     */
    List<TextSegment> splitDocuments(List<Document> documents);

    /**
     * 基于Token数量切割文档
     * @param document 待切割的文档
     * @param maxTokens 最大Token数
     * @param overlapTokens 重叠Token数
     * @return 切割后的文档片段列表
     */
    List<TextSegment> splitDocumentByTokens(Document document, int maxTokens, int overlapTokens);
}
