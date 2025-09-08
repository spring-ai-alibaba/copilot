package com.alibaba.cloud.ai.copilot.rag.service;

import dev.langchain4j.data.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * 文档解析服务接口
 * 支持多种文档格式的解析，包括PDF、Word、TXT等
 */
public interface DocumentParserService {
    
    /**
     * 解析上传的文件
     * @param file 上传的文件
     * @return 解析后的文档对象
     */
    Document parseDocument(MultipartFile file);
    
    /**
     * 解析本地文件
     * @param file 本地文件
     * @return 解析后的文档对象
     */
    Document parseDocument(File file);
    
    /**
     * 解析输入流
     * @param inputStream 输入流
     * @param fileName 文件名（用于确定文件类型）
     * @return 解析后的文档对象
     */
    Document parseDocument(InputStream inputStream, String fileName);
    
    /**
     * 批量解析文件
     * @param files 文件列表
     * @return 解析后的文档列表
     */
    List<Document> parseDocuments(List<MultipartFile> files);
    
    /**
     * 检查文件类型是否支持
     * @param fileName 文件名
     * @return 是否支持
     */
    boolean isSupportedFileType(String fileName);
    
    /**
     * 获取支持的文件扩展名列表
     * @return 支持的文件扩展名
     */
    List<String> getSupportedExtensions();
}
