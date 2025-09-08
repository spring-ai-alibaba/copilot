package com.alibaba.cloud.ai.copilot.rag.service;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析服务测试类
 */
public class DocumentParserServiceTest {
    
    @Test
    public void testDocumentCreation() {
        // 测试LangChain4j Document创建
        String content = "这是一个测试文档的内容。它包含了一些示例文本，用于测试RAG系统的文档处理功能。";
        
        Document document = Document.from(content);
        
        assertNotNull(document);
        assertEquals(content, document.text());
        assertNotNull(document.metadata());
        
        System.out.println("文档创建测试通过");
        System.out.println("文档内容长度: " + document.text().length());
    }
    
    @Test
    public void testMockFileCreation() {
        // 创建模拟文件用于测试
        String content = "这是一个测试PDF文件的内容。\n\n" +
                        "第一章：介绍\n" +
                        "这是第一章的内容，介绍了系统的基本概念。\n\n" +
                        "第二章：实现\n" +
                        "这是第二章的内容，详细描述了系统的实现方法。\n\n" +
                        "第三章：总结\n" +
                        "这是第三章的内容，总结了整个系统的特点和优势。";
        
        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test-document.txt",
            "text/plain",
            content.getBytes()
        );
        
        assertNotNull(mockFile);
        assertEquals("test-document.txt", mockFile.getOriginalFilename());
        assertEquals("text/plain", mockFile.getContentType());
        assertTrue(mockFile.getSize() > 0);
        
        System.out.println("模拟文件创建成功: " + mockFile.getOriginalFilename());
        System.out.println("文件大小: " + mockFile.getSize() + " bytes");
    }
}
