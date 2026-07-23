package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeDoc;
import com.alibaba.cloud.ai.copilot.knowledge.store.KnowledgeMilvusStore;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 *
 * <p>向量读写通过 {@link KnowledgeMilvusStore}
 * embedding 用 agentscope {@link EmbeddingModel}
 * 多租户隔离：payload.user_id 作为 Milvus 动态标量字段，检索时服务端 filter 过滤。</p>
 *
 * @author RobustH
 */
@Slf4j
@Service
public class KnowledgeVectorStoreService {

    private final KnowledgeFtsService ftsService;

    @Autowired(required = false)
    private KnowledgeMilvusStore milvusStore;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    public KnowledgeVectorStoreService(KnowledgeFtsService ftsService) {
        this.ftsService = ftsService;
    }

    public void addKnowledge(String userId, KnowledgeChunk chunk) {
        addKnowledgeBatch(userId, List.of(chunk));
    }

    public void addKnowledgeBatch(String userId, List<KnowledgeChunk> chunks) {
        if (milvusStore == null || embeddingModel == null) {
            log.warn("向量库或嵌入模型未就绪，跳过向量写入（FTS 仍写入）");
            ftsService.addBatch(userId, chunks);
            return;
        }
        try {
            List<KnowledgeDoc> docs = chunks.stream()
                    .map(chunk -> {
                        String enriched = buildEnrichedContent(chunk,
                                chunk.getContent() != null ? chunk.getContent() : "");
                        KnowledgeDoc doc = KnowledgeMilvusStore.fromChunk(userId, chunk, enriched);
                        double[] emb = embeddingModel
                                .embed(TextBlock.builder().text(enriched).build()).block();
                        return new KnowledgeDoc(doc.docId(), doc.chunkId(), doc.content(), emb, null, doc.payload());
                    })
                    .collect(Collectors.toList());
            milvusStore.add(docs);
            ftsService.addBatch(userId, chunks);
            log.info("已批量添加 {} 个知识块（向量+FTS）, 用户: {}", chunks.size(), userId);
        } catch (Exception e) {
            log.error("向量写入失败，仅写入 FTS: 用户={}, 块数={}", userId, chunks.size(), e);
            ftsService.addBatch(userId, chunks);
        }
    }

    public List<KnowledgeDoc> searchKnowledge(String userId, String query, int topK) {
        if (milvusStore == null || embeddingModel == null) {
            return List.of();
        }
        try {
            double[] emb = embedQuery(query);
            if (emb == null) return List.of();
            String filter = String.format("user_id == \"%s\"", userId);
            List<KnowledgeDoc> results = milvusStore.search(emb, filter, topK);
            log.info("向量搜索结束: userId={}, 命中={}", userId, results.size());
            return results;
        } catch (Exception e) {
            log.warn("向量搜索失败: userId={}, {}", userId, e.getMessage());
            return List.of();
        }
    }

    public List<KnowledgeDoc> searchKnowledgeByFileType(
            String userId,
            String query,
            KnowledgeCategory.FileType fileType,
            int topK) {
        if (milvusStore == null || embeddingModel == null) {
            return List.of();
        }
        try {
            double[] emb = embedQuery(query);
            if (emb == null) return List.of();
            String expectedType = fileType != null ? fileType.name() : null;
            String filter = expectedType != null
                    ? String.format("user_id == \"%s\" && file_type == \"%s\"", userId, expectedType)
                    : String.format("user_id == \"%s\"", userId);
            return milvusStore.search(emb, filter, topK);
        } catch (Exception e) {
            log.warn("按类型向量搜索失败: userId={}, {}", userId, e.getMessage());
            return List.of();
        }
    }

    public void deleteKnowledgeByFilePath(String userId, String filePath) {
        if (milvusStore == null) {
            return;
        }
        try {
            String docId = "u:" + userId + ":f:" + filePath;
            milvusStore.deleteByDocId(docId);
            ftsService.deleteByFilePath(userId, filePath);
            log.info("已清理旧文件向量数据: 用户={}, 文件={}", userId, filePath);
        } catch (Exception e) {
            log.warn("清理旧文件向量数据失败 (可能是首次添加): 用户={}, 文件={}, {}", userId, filePath, e.getMessage());
        }
    }

    public void deleteUserKnowledge(String userId) {
        ftsService.deleteByUserId(userId);
        if (milvusStore == null) {
            return;
        }
        try {
            milvusStore.deleteByFilter(String.format("user_id == \"%s\"", userId));
            log.info("已清理用户向量知识: 用户={}", userId);
        } catch (Exception e) {
            log.warn("清理用户向量知识失败: 用户={}, {}", userId, e.getMessage());
        }
    }

    /**
     * 在原始代码内容前拼接自然语言描述头，提升跨语言召回率。
     */
    private String buildEnrichedContent(KnowledgeChunk chunk, String rawContent) {
        StringBuilder prefix = new StringBuilder();
        if (chunk.getFilePath() != null) {
            String fileName = chunk.getFilePath().replaceAll(".*[/\\\\]", "");
            prefix.append("文件: ").append(fileName);
        }
        Map<String, Object> meta = chunk.getMetadata();
        if (meta != null) {
            Object symbolType = meta.get("symbolType");
            Object symbolName = meta.get("symbolName");
            Object parentSymbol = meta.get("parentSymbol");
            if (symbolType != null) {
                prefix.append(" | 类型: ").append(toChineseSymbolType(symbolType.toString()));
            }
            if (symbolName != null) {
                prefix.append(" | 符号: ").append(symbolName);
            }
            if (parentSymbol != null) {
                prefix.append(" | 所属: ").append(parentSymbol);
            }
        }
        return prefix.length() > 0 ? prefix + "\n" + rawContent : rawContent;
    }

    private String toChineseSymbolType(String symbolType) {
        return switch (symbolType.toUpperCase()) {
            case "CLASS" -> "类";
            case "INTERFACE" -> "接口";
            case "METHOD" -> "方法";
            case "FIELD" -> "字段";
            case "ENUM" -> "枚举";
            case "ANNOTATION" -> "注解";
            default -> symbolType;
        };
    }

    private double[] embedQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        return embeddingModel.embed(TextBlock.builder().text(query).build()).block();
    }
}
