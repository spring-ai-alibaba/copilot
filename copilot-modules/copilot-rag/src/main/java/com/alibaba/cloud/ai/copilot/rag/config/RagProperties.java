package com.alibaba.cloud.ai.copilot.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * RAG模块配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "copilot.rag")
public class RagProperties {

    /**
     * 文件上传配置
     */
    private FileUpload fileUpload = new FileUpload();

    /**
     * 文档处理配置
     */
    private DocumentProcessing documentProcessing = new DocumentProcessing();

    /**
     * 向量化配置
     */
    private Vectorization vectorization = new Vectorization();

    /**
     * 检索配置
     */
    private Retrieval retrieval = new Retrieval();

    /**
     * Embedding模型配置
     */
    private Embedding embedding = new Embedding();

    /**
     * PgVector向量库配置
     */
    private PgVector pgvector = new PgVector();

    /**
     * 向量存储配置
     */
    private VectorStore vectorStore = new VectorStore();

    @Data
    public static class FileUpload {
        /**
         * 文件存储根目录
         */
        private String storageRoot = "./data/rag/files";

        /**
         * 最大文件大小（字节）
         */
        private long maxFileSize = 10 * 1024 * 1024; // 10MB

        /**
         * 支持的文件类型
         */
        private List<String> supportedTypes = Arrays.asList(
            "pdf", "docx", "doc", "txt", "md", "html", "rtf"
        );

        /**
         * 是否启用文件去重（基于哈希值）
         */
        private boolean enableDeduplication = true;
    }

    @Data
    public static class DocumentProcessing {
        /**
         * 默认分块大小
         */
        private int chunkSize = 500;

        /**
         * 默认分块重叠
         */
        private int chunkOverlap = 50;

        /**
         * 最大Token数每块
         */
        private int maxTokensPerChunk = 512;

        /**
         * 重叠Token数
         */
        private int overlapTokens = 50;

        /**
         * 是否启用异步处理
         */
        private boolean enableAsyncProcessing = true;

        /**
         * 处理超时时间（秒）
         */
        private int processingTimeoutSeconds = 300;
    }

    @Data
    public static class Vectorization {
        /**
         * 默认嵌入模型
         */
        private String defaultEmbeddingModel = "text-embedding-ada-002";

        /**
         * 向量维度
         */
        private int embeddingDimension = 1536;

        /**
         * 批量向量化大小
         */
        private int batchSize = 100;
    }

    @Data
    public static class Retrieval {
        /**
         * 默认检索数量
         */
        private int defaultTopK = 5;

        /**
         * 最大检索数量
         */
        private int maxTopK = 20;

        /**
         * 相似度阈值
         */
        private double similarityThreshold = 0.7;

        /**
         * 是否启用重排序
         */
        private boolean enableReranking = false;
    }

    @Data
    public static class Embedding {
        /**
         * Embedding提供商
         */
        private String provider = "openai";

        /**
         * 模型名称
         */
        private String model = "text-embedding-ada-002";

        /**
         * 请求超时时间（秒）
         */
        private int timeout = 60;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
    }

    @Data
    public static class PgVector {
        /**
         * PostgreSQL服务器地址
         */
        private String host = "localhost";

        /**
         * PostgreSQL服务器端口
         */
        private int port = 5432;

        /**
         * 数据库名
         */
        private String database = "copilot_rag";

        /**
         * 用户名
         */
        private String username = "postgres";

        /**
         * 密码
         */
        private String password = "password";

        /**
         * 表名前缀
         */
        private String table = "copilot_embeddings";

        /**
         * 向量维度
         */
        private int dimension = 1536;
    }

    @Data
    public static class VectorStore {
        /**
         * 向量存储提供商
         */
        private String provider = "pgvector";

        /**
         * 是否在启动时初始化
         */
        private boolean initializeOnStartup = true;

        /**
         * 连接池大小
         */
        private int connectionPoolSize = 10;
    }
}
