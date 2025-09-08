package com.alibaba.cloud.ai.copilot.rag.service;

import com.alibaba.cloud.ai.copilot.rag.config.RagProperties;
import com.alibaba.cloud.ai.copilot.rag.service.impl.DocumentSplitterServiceImpl;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 文档切割服务测试类
 */
public class DocumentSplitterServiceTest {
    
    @Mock
    private RagProperties ragProperties;
    
    @Mock
    private RagProperties.DocumentProcessing documentProcessing;
    
    private DocumentSplitterService documentSplitterService;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 设置默认配置
        when(ragProperties.getDocumentProcessing()).thenReturn(documentProcessing);
        when(documentProcessing.getChunkSize()).thenReturn(500);
        when(documentProcessing.getChunkOverlap()).thenReturn(50);
        when(documentProcessing.getMaxTokensPerChunk()).thenReturn(1000);
        when(documentProcessing.getOverlapTokens()).thenReturn(100);
        
        documentSplitterService = new DocumentSplitterServiceImpl(ragProperties);
    }
    
    @Test
    public void testSplitDocument() {
        // 创建测试文档
        String content = "这是一个测试文档的内容。它包含了一些示例文本，用于测试RAG系统的文档处理功能。" +
                        "文档切割功能应该能够将长文档分割成适合向量化的小块。每个小块都应该保持语义的完整性。" +
                        "这样可以提高检索的准确性和相关性。";
        
        Document document = Document.from(content);
        
        // 执行切割
        List<TextSegment> segments = documentSplitterService.splitDocument(document, 100, 20);
        
        // 验证结果
        assertNotNull(segments);
        assertFalse(segments.isEmpty());
        
        // 验证每个片段都有内容
        for (TextSegment segment : segments) {
            assertNotNull(segment.text());
            assertFalse(segment.text().trim().isEmpty());
        }
        
        System.out.println("文档切割测试通过");
        System.out.println("原文档长度: " + content.length());
        System.out.println("切割后片段数: " + segments.size());
        
        for (int i = 0; i < segments.size(); i++) {
            System.out.println("片段 " + (i + 1) + ": " + segments.get(i).text());
        }
    }
    
    @Test
    public void testSplitDocumentWithNullContent() {
        // 测试空文档
        Document document = Document.from("");
        
        List<TextSegment> segments = documentSplitterService.splitDocument(document, 100, 20);
        
        assertNotNull(segments);
        assertTrue(segments.isEmpty());
        
        System.out.println("空文档测试通过");
    }
    
    @Test
    public void testSplitDocumentWithDefaultConfig() {
        // 创建测试文档
        String content = "这是使用默认配置的测试文档。";
        Document document = Document.from(content);
        
        // 使用默认配置切割
        List<TextSegment> segments = documentSplitterService.splitDocument(document);
        
        assertNotNull(segments);
        
        System.out.println("默认配置测试通过");
        System.out.println("切割后片段数: " + segments.size());
    }
}
