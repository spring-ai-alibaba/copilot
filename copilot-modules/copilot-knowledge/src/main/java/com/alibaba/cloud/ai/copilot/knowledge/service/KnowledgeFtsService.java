package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.domain.entity.KnowledgeFts;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.mapper.KnowledgeFtsMapper;
import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FTS 全文检索服务
 *   - 用 MySQL ngram 解析器处理分词（ngram_token_size=2，默认）
 *   - 用户输入的中/英文关键词直接传入 BOOLEAN MODE 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeFtsService {

    private final KnowledgeFtsMapper ftsMapper;

    /**
     * 批量写入 FTS 记录（在索引时与向量写入同步调用）
     */
    public void addBatch(String userId, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;

        List<KnowledgeFts> records = chunks.stream()
                .map(chunk -> toFtsRecord(userId, chunk))
                .collect(Collectors.toList());

        ftsMapper.batchInsert(records);
        log.debug("FTS 写入: userId={}, count={}", userId, records.size());
    }

    /**
     * 全文检索
     *
     * @param userId 用户ID
     * @param query  原始用户查询
     * @param n      返回条数
     * @return 匹配的 Document 列表（按 BM25 评分排序）
     */
    public List<KnowledgeDoc> search(String userId, String query, int n) {
        if (query == null || query.trim().isEmpty()) return List.of();

        // 构建 MySQL BOOLEAN MODE 查询词
        // 对标 Continue 的 trigram OR 拼接：将查询词转为带引号的精确短语
        String booleanQuery = buildBooleanQuery(query);
        log.info("FTS 搜索: userId={}, booleanQuery={}, n={}", userId, booleanQuery, n);

        List<KnowledgeFts> results = ftsMapper.fullTextSearch(userId, booleanQuery, n);
        log.info("FTS 搜索结果: userId={}, 返回 {} 条", userId, results.size());

        return results.stream()
                .map(this::toKnowledgeDoc)
                .collect(Collectors.toList());
    }

    /**
     * 按文件路径删除 FTS 记录
     */
    public void deleteByFilePath(String userId, String filePath) {
        ftsMapper.deleteByFilePath(userId, filePath);
    }

    /**
     * 删除用户全部 FTS 记录
     */
    public void deleteByUserId(String userId) {
        ftsMapper.deleteByUserId(userId);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 MySQL BOOLEAN MODE 查询词。
     *
     * "StudentNotFoundException" +"异常" （关键词加号强制匹配）
     *
     * 简化策略：按空格分词，每个词加 + 号做 AND 必须包含搜索。
     * 若查询词只有1个，直接用词本身不加操作符（使用 ngram 分词）。
     */
    private String buildBooleanQuery(String query) {
        String[] terms = query.trim().split("[\\s　,，。？?！!、；;]+");
        if (terms.length == 0) return query;

        // 过滤停用词
        Set<String> stopWords = Set.of("是", "的", "了", "在", "有", "这", "那", "和", "与",
                "怎么", "如何", "什么", "哪些", "为什么");

        List<String> meaningful = Arrays.stream(terms)
                .map(String::trim)
                .filter(t -> t.length() >= 1 && !stopWords.contains(t))
                .collect(Collectors.toList());

        if (meaningful.isEmpty()) return query;

        // 每个词加 + 号（AND 模式），让召回结果更精确
        // 若两个词以上用 OR 模式提高召回率
        if (meaningful.size() == 1) {
            return meaningful.get(0);
        }
        return meaningful.stream().map(t -> "+" + t).collect(Collectors.joining(" "));
    }

    private KnowledgeFts toFtsRecord(String userId, KnowledgeChunk chunk) {
        KnowledgeFts record = new KnowledgeFts();
        record.setId(chunk.getId());
        record.setUserId(userId);
        record.setFilePath(chunk.getFilePath() != null ? chunk.getFilePath() : "");
        record.setStartLine(chunk.getStartLine() != null ? chunk.getStartLine() : 0);
        record.setEndLine(chunk.getEndLine() != null ? chunk.getEndLine() : 0);

        // 构建 FTS 内容 = 文件名 + 符号信息 + 代码内容（对标 Continue 的 path+content 拼接）
        String ftsContent = buildFtsContent(chunk);
        record.setContent(ftsContent);
        return record;
    }

    private String buildFtsContent(KnowledgeChunk chunk) {
        StringBuilder sb = new StringBuilder();

        // 文件名（对标 Continue FTS 的 path 字段，权重 10x）
        if (chunk.getFilePath() != null) {
            String fileName = chunk.getFilePath().replaceAll(".*[/\\\\]", "");
            sb.append(fileName).append("\n");
        }

        // 符号信息（对标 Continue 的 title + signature 字段）
        Map<String, Object> meta = chunk.getMetadata();
        if (meta != null) {
            Object symbolName = meta.get("symbolName");
            Object symbolType = meta.get("symbolType");
            Object parentSymbol = meta.get("parentSymbol");
            if (symbolName != null) sb.append(symbolName).append(" ");
            if (symbolType != null) sb.append(symbolType).append(" ");
            if (parentSymbol != null) sb.append(parentSymbol).append("\n");
        }

        // 原始代码内容
        if (chunk.getContent() != null) {
            sb.append(chunk.getContent());
        }

        return sb.toString();
    }

    private KnowledgeDoc toKnowledgeDoc(KnowledgeFts record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", record.getUserId());
        payload.put("file_path", record.getFilePath());
        payload.put("start_line", record.getStartLine());
        payload.put("end_line", record.getEndLine());
        payload.put("source", "fts");
        String id = record.getId() != null ? record.getId() : java.util.UUID.randomUUID().toString();
        return new KnowledgeDoc(id, id,
                record.getContent() != null ? record.getContent() : "",
                null, null, payload);
    }
}
