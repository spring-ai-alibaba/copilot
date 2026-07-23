package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.SplitterFactory;
import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库核心服务
 *
 * 职责:
 * 1. 文件/目录处理: 读取、切割、转换为知识块
 * 2. 知识库操作: 添加、搜索、删除
 * 3. 结果格式化: 提取内容、格式化上下文
 *
 * 这是知识库模块的统一入口,供其他模块调用
 *
 * @author RobustH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final SplitterFactory splitterFactory;
    private final KnowledgeVectorStoreService vectorStoreService;
    private final KnowledgeFtsService ftsService;

    // ==================== 文件处理 ====================

    public int addFile(String userId, String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            log.warn("文件不存在或不是文件: {}", filePath);
            return 0;
        }

        List<KnowledgeChunk> chunks = processFile(file);

        // 增量更新：先删该文件旧 chunks，再插新 chunks（文件级全量替换）
        vectorStoreService.deleteKnowledgeByFilePath(userId, filePath);
        return saveChunks(userId, chunks);
    }

    public int addDirectory(String userId, String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log.warn("目录不存在或不是目录: {}", directoryPath);
            return 0;
        }

        try (var paths = Files.walk(directory.toPath())) {
            List<KnowledgeChunk> chunks = paths
                    .filter(Files::isRegularFile)
                    .map(path -> processFile(path.toFile()))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            return saveChunks(userId, chunks);
        } catch (IOException e) {
            log.error("处理目录失败: {}", directoryPath, e);
            return 0;
        }
    }

    public int addKnowledge(String userId, String content) {
        return addKnowledge(userId, content, SplitterStrategy.TOKEN);
    }

    public int addKnowledge(String userId, String content, SplitterStrategy strategy) {
        if (content == null || content.trim().isEmpty()) return 0;
        String virtualPath = "dynamic-" + UUID.randomUUID();
        DocumentSplitter splitter = splitterFactory.getSplitter(strategy);
        List<KnowledgeChunk> chunks = splitter.split(content, virtualPath);
        return saveChunks(userId, chunks);
    }

    public int addKnowledge(String userId, String content, String filePath) {
        if (content == null || content.trim().isEmpty()) return 0;
        DocumentSplitter splitter = splitterFactory.getSplitterByPath(filePath);
        List<KnowledgeChunk> chunks = splitter.split(content, filePath);
        return saveChunks(userId, chunks);
    }

    // ==================== 内部处理方法 ====================

    private List<KnowledgeChunk> processFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            String filePath = file.getAbsolutePath();
            return splitterFactory.getSplitterByPath(filePath).split(content, filePath);
        } catch (IOException e) {
            log.error("读取文件失败: {}", file.getAbsolutePath(), e);
            return new ArrayList<>();
        }
    }

    private int saveChunks(String userId, List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) return 0;
        try {
            vectorStoreService.addKnowledgeBatch(userId, chunks);
            log.info("已存储知识: 用户={}, 块数={}", userId, chunks.size());
            return chunks.size();
        } catch (Exception e) {
            log.error("存储知识失败: 用户={}", userId, e);
            return 0;
        }
    }

    // ==================== 知识库搜索 ====================

    /**
     * 三路合并语义检索
     *
     * 权重：50% 向量语义 / 25% FTS 全文 / 25% 最近索引（暂未实现）
     * 去重：按 filePath+startLine+endLine
     *
     * @param nFinal 最终返回 chunk 数
     */
    public List<KnowledgeDoc> search(String userId, String query, int nFinal) {
        int embeddingsN = Math.max(1, (int)(nFinal * 0.50));
        int ftsN        = Math.max(1, (int)(nFinal * 0.25));

        List<KnowledgeDoc> merged = new ArrayList<>();

        try {
            List<KnowledgeDoc> vecResults = vectorStoreService.searchKnowledge(userId, query, embeddingsN);
            log.info("[向量] userId={}, 返回 {} 条", userId, vecResults.size());
            merged.addAll(vecResults);
        } catch (Exception e) {
            log.warn("向量搜索失败: {}", e.getMessage());
        }

        try {
            List<KnowledgeDoc> ftsResults = ftsService.search(userId, query, ftsN);
            log.info("[FTS] userId={}, 返回 {} 条", userId, ftsResults.size());
            merged.addAll(ftsResults);
        } catch (Exception e) {
            log.warn("FTS 搜索失败: {}", e.getMessage());
        }

        List<KnowledgeDoc> deduped = deduplicateChunks(merged);
        log.info("[合并去重] query={}, 合并前={}, 去重后={}", query, merged.size(), deduped.size());

        return deduped.size() > nFinal ? deduped.subList(0, nFinal) : deduped;
    }

    private List<KnowledgeDoc> deduplicateChunks(List<KnowledgeDoc> docs) {
        Map<String, KnowledgeDoc> seen = new LinkedHashMap<>();
        for (KnowledgeDoc doc : docs) {
            String fp    = String.valueOf(doc.payloadValue("file_path") != null ? doc.payloadValue("file_path") : "");
            String start = String.valueOf(doc.payloadValue("start_line") != null ? doc.payloadValue("start_line") : "");
            String end   = String.valueOf(doc.payloadValue("end_line") != null ? doc.payloadValue("end_line") : "");
            String key   = fp + "#" + start + "-" + end;
            seen.putIfAbsent(key, doc);
        }
        return new ArrayList<>(seen.values());
    }

    public List<KnowledgeDoc> searchByFileType(String userId, String query, KnowledgeCategory.FileType fileType, int topK) {
        return vectorStoreService.searchKnowledgeByFileType(userId, query, fileType, topK);
    }

    public List<KnowledgeDoc> searchCode(String userId, String query, int topK) {
        return searchByFileType(userId, query, KnowledgeCategory.FileType.CODE, topK);
    }

    public List<KnowledgeDoc> searchDocuments(String userId, String query, int topK) {
        return searchByFileType(userId, query, KnowledgeCategory.FileType.DOCUMENT, topK);
    }

    public List<KnowledgeDoc> searchConfig(String userId, String query, int topK) {
        return searchByFileType(userId, query, KnowledgeCategory.FileType.CONFIG, topK);
    }

    // ==================== 辅助方法 ====================

    public List<String> extractContents(List<KnowledgeDoc> docs) {
        return docs.stream().map(KnowledgeDoc::content).collect(Collectors.toList());
    }

    public String formatAsContext(List<KnowledgeDoc> docs) {
        return docs.stream()
                .map(doc -> String.format("文件: %s\n内容:\n%s",
                        doc.payloadValue("file_path") != null ? doc.payloadValue("file_path") : "unknown",
                        doc.content()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public void deleteUserKnowledge(String userId) {
        vectorStoreService.deleteUserKnowledge(userId);
        log.info("已删除用户知识: {}", userId);
    }
}
