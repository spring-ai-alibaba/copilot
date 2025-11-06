package com.alibaba.cloud.ai.copilot.rag.service.impl;

import com.alibaba.cloud.ai.copilot.rag.config.RagProperties;
import com.alibaba.cloud.ai.copilot.rag.config.VectorStoreConfiguration;
import com.alibaba.cloud.ai.copilot.rag.service.DocumentParserService;
import com.alibaba.cloud.ai.copilot.rag.service.DocumentSplitterService;
import com.alibaba.cloud.ai.copilot.rag.service.RagProcessingService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG处理服务实现类
 * 整合文档解析、切割、向量化、存储的完整流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagProcessingServiceImpl implements RagProcessingService {

    private final DocumentParserService documentParserService;
    private final DocumentSplitterService documentSplitterService;
    private final VectorStoreConfiguration vectorStoreConfiguration;
    private final RagProperties ragProperties;

    @Qualifier("langchain4jEmbeddingModel")
    private final EmbeddingModel embeddingModel;

    // 缓存EmbeddingStore实例
    private final Map<String, EmbeddingStore<TextSegment>> embeddingStoreCache = new ConcurrentHashMap<>();

    // 异步任务状态缓存
    private final Map<String, ProcessingTaskStatus> taskStatusCache = new ConcurrentHashMap<>();

    @Override
    public ProcessingResult processFile(String kbKey, MultipartFile file, String uploadedBy) {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();

        try {
            log.info("开始处理文件: {} - {}", kbKey, fileName);

            // 2. 解析文档
            Document document = documentParserService.parseDocument(file);
            if (document == null || document.text().trim().isEmpty()) {
                return ProcessingResult.failure(fileName, "文档解析失败或内容为空");
            }

            // 3. 切割文档
            List<TextSegment> segments = documentSplitterService.splitDocument(document);
            if (segments.isEmpty()) {
                return ProcessingResult.failure(fileName, "文档切割失败，未生成任何片段");
            }

            // 4. 添加元数据
            addMetadataToSegments(segments, kbKey, fileName, uploadedBy);

            // 5. 存储到向量库
            EmbeddingStore<TextSegment> embeddingStore = getOrCreateEmbeddingStore(kbKey);

            // 6. 向量化并存储
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("文件处理完成: {} - {} 个片段，耗时: {}ms", fileName, segments.size(), processingTime);

            return ProcessingResult.success(fileName, segments.size(), processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("文件处理失败: {} - {}", fileName, e.getMessage(), e);
            return ProcessingResult.failure(fileName, e.getMessage());
        }
    }

    @Override
    public BatchProcessingResult processFiles(String kbKey, List<MultipartFile> files, String uploadedBy) {
        log.info("开始批量处理文件: {} - {} 个文件", kbKey, files.size());

        List<ProcessingResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                ProcessingResult result = processFile(kbKey, file, uploadedBy);
                results.add(result);
            } catch (Exception e) {
                String fileName = file.getOriginalFilename();
                log.error("批量处理文件失败: {}", fileName, e);
                results.add(ProcessingResult.failure(fileName, e.getMessage()));
            }
        }

        return new BatchProcessingResult(files.size(), results);
    }

    @Override
    public ProcessingResult processTextContent(String kbKey, String content, String title, String createdBy) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始处理文本内容: {} - {}", kbKey, title);

            // 2. 创建文档
            Document document = Document.from(content);
            if (document.metadata() != null) {
                document.metadata().put("title", title);
                document.metadata().put("source", "text_input");
            }

            // 3. 切割文档
            List<TextSegment> segments = documentSplitterService.splitDocument(document);
            if (segments.isEmpty()) {
                return ProcessingResult.failure(title, "文本切割失败，未生成任何片段");
            }

            // 4. 添加元数据
            addMetadataToSegments(segments, kbKey, title, createdBy);

            // 5. 存储到向量库
            EmbeddingStore<TextSegment> embeddingStore = getOrCreateEmbeddingStore(kbKey);

            // 6. 向量化并存储
            for (TextSegment segment : segments) {
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("文本内容处理完成: {} - {} 个片段，耗时: {}ms", title, segments.size(), processingTime);

            return ProcessingResult.success(title, segments.size(), processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("文本内容处理失败: {} - {}", title, e.getMessage(), e);
            return ProcessingResult.failure(title, e.getMessage());
        }
    }

    @Override
    @Async
    public String processFileAsync(String kbKey, MultipartFile file, String uploadedBy) {
        String taskId = generateTaskId();
        taskStatusCache.put(taskId, ProcessingTaskStatus.PENDING);

        CompletableFuture.runAsync(() -> {
            try {
                taskStatusCache.put(taskId, ProcessingTaskStatus.PROCESSING);
                ProcessingResult result = processFile(kbKey, file, uploadedBy);

                if (result.isSuccess()) {
                    taskStatusCache.put(taskId, ProcessingTaskStatus.COMPLETED);
                } else {
                    taskStatusCache.put(taskId, ProcessingTaskStatus.FAILED);
                }
            } catch (Exception e) {
                log.error("异步处理文件失败: {}", file.getOriginalFilename(), e);
                taskStatusCache.put(taskId, ProcessingTaskStatus.FAILED);
            }
        });

        return taskId;
    }

    @Override
    public ProcessingTaskStatus getTaskStatus(String taskId) {
        return taskStatusCache.getOrDefault(taskId, ProcessingTaskStatus.PENDING);
    }

    /**
     * 获取或创建向量存储
     */
    private EmbeddingStore<TextSegment> getOrCreateEmbeddingStore(String kbKey) {
        return embeddingStoreCache.computeIfAbsent(kbKey, key -> {
            log.info("为知识库 {} 创建向量存储", key);
            return vectorStoreConfiguration.createEmbeddingStoreForKnowledgeBase(key, embeddingModel);
        });
    }

    /**
     * 为文档片段添加元数据
     */
    private void addMetadataToSegments(List<TextSegment> segments, String kbKey, String fileName, String createdBy) {
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            if (segment.metadata() != null) {
                segment.metadata().put("kbKey", kbKey);
                segment.metadata().put("fileName", fileName);
                segment.metadata().put("createdBy", createdBy);
                segment.metadata().put("chunkIndex", i);
                segment.metadata().put("totalChunks", segments.size());
                segment.metadata().put("createdTime", timestamp);
            }
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
    }
}
