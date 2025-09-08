package com.alibaba.cloud.ai.copilot.rag.service.impl;

import com.alibaba.cloud.ai.copilot.rag.service.DocumentParserService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文档解析服务实现类
 * 基于LangChain4j实现多种文档格式的解析
 */
@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    // 支持的文档类型
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
        "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    // Office文档类型
    private static final List<String> POI_DOCUMENT_TYPES = Arrays.asList(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    @Override
    public Document parseDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (!isSupportedFileType(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + FilenameUtils.getExtension(fileName));
        }

        return parseDocument(file.getInputStream(), fileName);
    }

    @Override
    public Document parseDocument(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在");
        }

        String fileName = file.getName();
        if (!isSupportedFileType(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + FilenameUtils.getExtension(fileName));
        }

        try {
            String extension = FilenameUtils.getExtension(fileName).toLowerCase();
            Document document = parseWithLangChain4j(file.getAbsolutePath(), extension);

            // 添加文件名元数据
            if (document.metadata() != null) {
                document.metadata().put("fileName", fileName);
                document.metadata().put("fileExtension", extension);
            }

            return document;
        } catch (Exception e) {
            log.error("解析文档失败: {}", fileName, e);
            throw new RuntimeException("解析文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Document parseDocument(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }

        if (!isSupportedFileType(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + FilenameUtils.getExtension(fileName));
        }

        try {
            // 创建临时文件
            Path tempFile = Files.createTempFile("rag_temp_", "_" + fileName);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                Document result = parseDocument(tempFile.toFile());
                return result;
            } finally {
                // 清理临时文件
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            log.error("解析文档失败: {}", fileName, e);
            throw new RuntimeException("解析文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Document> parseDocuments(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<Document> documents = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                Document document = parseDocument(file);
                if (document != null) {
                    documents.add(document);
                }
            } catch (Exception e) {
                log.error("解析文档失败: {}", file.getOriginalFilename(), e);
                // 继续处理其他文件，不中断整个批处理
            }
        }

        return documents;
    }

    @Override
    public boolean isSupportedFileType(String fileName) {
        if (fileName == null) {
            return false;
        }

        String extension = FilenameUtils.getExtension(fileName).toLowerCase();
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public List<String> getSupportedExtensions() {
        return new ArrayList<>(SUPPORTED_EXTENSIONS);
    }

    /**
     * 使用LangChain4j解析文档
     */
    private Document parseWithLangChain4j(String filePath, String extension) {
        Document document = null;

        switch (extension) {
            case "txt":
                document = FileSystemDocumentLoader.loadDocument(filePath, new TextDocumentParser());
                break;
            case "pdf":
                document = FileSystemDocumentLoader.loadDocument(filePath, new ApachePdfBoxDocumentParser());
                break;
            default:
                if (POI_DOCUMENT_TYPES.contains(extension)) {
                    document = FileSystemDocumentLoader.loadDocument(filePath, new ApachePoiDocumentParser());
                }
                break;
        }

        if (document == null) {
            throw new RuntimeException("无法解析文档类型: " + extension);
        }

        return document;
    }

}
