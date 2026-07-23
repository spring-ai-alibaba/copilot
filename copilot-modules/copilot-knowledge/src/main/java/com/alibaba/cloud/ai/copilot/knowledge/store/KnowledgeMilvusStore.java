package com.alibaba.cloud.ai.copilot.knowledge.store;

import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量存储封装
 *
 * <p>collection schema 设计
 * 对齐 agentscope：id(主键)/vector/doc_id/chunk_id/content + 动态字段（{@code enableDynamicField=true}），
 * payload 的每个键作为动态标量字段存储，使 {@code user_id == "xxx"} 这类过滤可走 Milvus 原生标量索引。</p>
 *
 * <p>多租户：检索时按 {@code payload.user_id} 服务端过滤，不再客户端放大候选集。</p>
 *
 * @author RobustH
 */
@Slf4j
public class KnowledgeMilvusStore implements AutoCloseable {

    private static final String FIELD_ID = "id";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_CONTENT = "content";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Gson GSON = new Gson();

    private final MilvusClientV2 client;
    private final String databaseName;
    private final String collectionName;
    private final int dimensions;
    private final IndexParam.MetricType metricType;

    public KnowledgeMilvusStore(String uri, String databaseName, String collectionName,
                                int dimensions, String username, String password) {
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.dimensions = dimensions;
        this.metricType = IndexParam.MetricType.COSINE;

        ConnectConfig.ConnectConfigBuilder b = ConnectConfig.builder().uri(uri);
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            b.username(username).password(password);
        } else if (password != null && !password.isBlank()) {
            b.token(password);
        }
        this.client = new MilvusClientV2(b.build());
        ensureCollection();
        log.info("KnowledgeMilvusStore 初始化: uri={}, collection={}, dim={}", uri, collectionName, dimensions);
    }

    private void ensureCollection() {
        try {
            boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .databaseName(databaseName)
                    .collectionName(collectionName)
                    .build());
            if (exists) {
                log.debug("Collection '{}' 已存在", collectionName);
                return;
            }
            createCollection();
            log.info("已创建 Milvus collection: {}, dim={}", collectionName, dimensions);
        } catch (Exception e) {
            throw new IllegalStateException("确保 collection 存在失败: " + collectionName, e);
        }
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
        schema.setEnableDynamicField(true);

        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_ID).dataType(DataType.VarChar).maxLength(64)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_VECTOR).dataType(DataType.FloatVector)
                .dimension(dimensions).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_DOC_ID).dataType(DataType.VarChar).maxLength(256).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CHUNK_ID).dataType(DataType.VarChar).maxLength(64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName(FIELD_CONTENT).dataType(DataType.VarChar).maxLength(65535).build());

        IndexParam indexParam = IndexParam.builder()
                .fieldName(FIELD_VECTOR)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(metricType)
                .build();

        client.createCollection(CreateCollectionReq.builder()
                .databaseName(databaseName)
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(Collections.singletonList(indexParam))
                .build());
    }

    /**
     * 批量插入。每个 chunk 一行，payload 各键作为动态字段写入。
     */
    public void add(List<KnowledgeDoc> docs) {
        if (docs == null || docs.isEmpty()) return;
        List<JsonObject> rows = new ArrayList<>(docs.size());
        for (KnowledgeDoc doc : docs) {
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_ID, doc.chunkId());
            row.addProperty(FIELD_DOC_ID, doc.docId());
            row.addProperty(FIELD_CHUNK_ID, doc.chunkId());
            row.addProperty(FIELD_CONTENT, doc.content());

            // 向量：double[] → List<Float>
            double[] emb = doc.embedding();
            List<Float> floatList = new ArrayList<>(emb.length);
            for (double d : emb) {
                floatList.add((float) d);
            }
            row.add(FIELD_VECTOR, GSON.toJsonTree(floatList));

            // payload 每个键作为动态字段（GSON 把 Map 序列化进 JsonObject 顶层属性）
            if (doc.payload() != null) {
                JsonObject payloadObj = GSON.toJsonTree(doc.payload()).getAsJsonObject();
                for (String key : payloadObj.keySet()) {
                    row.add(key, payloadObj.get(key));
                }
            }
            rows.add(row);
        }
        client.insert(InsertReq.builder()
                .databaseName(databaseName)
                .collectionName(collectionName)
                .data(rows)
                .build());
    }

    /**
     * 语义检索：按 filter 过滤，返回 topK。
     *
     * @param queryEmbedding 查询向量
     * @param filter         Milvus 标量过滤表达式（如 {@code user_id == "123"}）
     * @param topK           返回条数
     */
    public List<KnowledgeDoc> search(double[] queryEmbedding, String filter, int topK) {
        float[] floatArray = new float[queryEmbedding.length];
        for (int i = 0; i < queryEmbedding.length; i++) {
            floatArray[i] = (float) queryEmbedding[i];
        }
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .databaseName(databaseName)
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(floatArray)))
                .topK(topK)
                .metricType(metricType)
                .outputFields(List.of(FIELD_ID, FIELD_DOC_ID, FIELD_CHUNK_ID, FIELD_CONTENT));
        if (filter != null && !filter.isBlank()) {
            builder.filter(filter);
        }
        SearchResp resp = client.search(builder.build());

        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<KnowledgeDoc> docs = new ArrayList<>(results.get(0).size());
        for (SearchResp.SearchResult r : results.get(0)) {
            Map<String, Object> entity = r.getEntity();
            Map<String, Object> payload = new HashMap<>(entity);
            // 移除已知标量列，保留作为 payload
            payload.remove(FIELD_ID);
            payload.remove(FIELD_VECTOR);
            Object content = entity.get(FIELD_CONTENT);
            docs.add(new KnowledgeDoc(
                    String.valueOf(entity.get(FIELD_DOC_ID)),
                    String.valueOf(entity.get(FIELD_CHUNK_ID)),
                    content != null ? String.valueOf(content) : "",
                    null,
                    (double) r.getScore(),
                    payload));
        }
        return docs;
    }

    /**
     * 按 docId 删除（文件级，一次删该文件所有 chunk）。
     */
    public void deleteByDocId(String docId) {
        String filter = String.format("%s == \"%s\"", FIELD_DOC_ID, docId.replace("\"", "\\\""));
        client.delete(DeleteReq.builder()
                .databaseName(databaseName)
                .collectionName(collectionName)
                .filter(filter)
                .build());
    }

    /**
     * 按 payload 过滤删除（用于按 user 清理）。
     */
    public void deleteByFilter(String filter) {
        client.delete(DeleteReq.builder()
                .databaseName(databaseName)
                .collectionName(collectionName)
                .filter(filter)
                .build());
    }

    /**
     * 将 KnowledgeChunk 转 KnowledgeDoc（content 含跨语言增强头），由 service 填 embedding。
     */
    public static KnowledgeDoc fromChunk(String userId, KnowledgeChunk chunk, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", userId != null ? userId : "default");
        payload.put("file_path", chunk.getFilePath() != null ? chunk.getFilePath() : "unknown");
        payload.put("file_type", chunk.getFileType() != null ? chunk.getFileType().name() : "OTHER");
        payload.put("language", chunk.getLanguage() != null ? chunk.getLanguage() : "text");
        payload.put("start_line", chunk.getStartLine() != null ? chunk.getStartLine() : 0);
        payload.put("end_line", chunk.getEndLine() != null ? chunk.getEndLine() : 0);
        payload.put("created_at", chunk.getCreatedAt() != null ? chunk.getCreatedAt() : System.currentTimeMillis());
        payload.put("content_hash", chunk.getContentHash() != null ? chunk.getContentHash() : "");
        payload.put("chunk_index", chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);

        String docId = "u:" + (userId != null ? userId : "default") + ":f:" + (chunk.getFilePath() != null ? chunk.getFilePath() : "unknown");
        String chunkId = chunk.getId() != null ? chunk.getId() : java.util.UUID.randomUUID().toString();
        return KnowledgeDoc.of(docId, chunkId, content, null, payload);
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("关闭 MilvusClientV2 失败: {}", e.getMessage());
        }
    }
}
