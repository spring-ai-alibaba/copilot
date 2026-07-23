package com.alibaba.cloud.ai.copilot.knowledge.store;

import java.util.Map;

/**
 * 知识库文档记录（本地 POJO）。
 *
 * <p>对应 Milvus collection 的一行：向量化后的内容 + 元数据（payload，作为动态字段存储）。
 * 多租户隔离靠 {@code payload.user_id} 作为标量字段过滤。</p>
 *
 * @param docId       文件级 ID（同一文件所有 chunk 共享，用于按文件整体删除）
 * @param chunkId     chunk 级 ID（主键，唯一）
 * @param content     原始文本内容（含跨语言语义增强头）
 * @param embedding   向量
 * @param score       检索相似度分数（写入时为 null，检索时回填）
 * @param payload     元数据（user_id / file_path / file_type / language / 行号 / content_hash 等）
 * @author RobustH
 */
public record KnowledgeDoc(
        String docId,
        String chunkId,
        String content,
        double[] embedding,
        Double score,
        Map<String, Object> payload) {

    public static KnowledgeDoc of(String docId, String chunkId, String content,
                                  double[] embedding, Map<String, Object> payload) {
        return new KnowledgeDoc(docId, chunkId, content, embedding, null, payload);
    }

    public Object payloadValue(String key) {
        return payload != null ? payload.get(key) : null;
    }
}
