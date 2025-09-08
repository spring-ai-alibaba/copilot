-- RAG知识库管理相关表结构
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS spring_ai_copilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE spring_ai_copilot;

-- 1. 向量库配置表
CREATE TABLE IF NOT EXISTS vector_store_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_name VARCHAR(100) NOT NULL COMMENT '配置名称',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置标识键',
    store_type VARCHAR(50) NOT NULL COMMENT '向量库类型：milvus, pgvector, elasticsearch等',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口号',
    username VARCHAR(100) COMMENT '用户名',
    password VARCHAR(255) COMMENT '密码',
    database_name VARCHAR(100) COMMENT '数据库名',
    collection_name VARCHAR(100) COMMENT '集合/表名',
    embedding_dimension INT DEFAULT 1536 COMMENT '向量维度',
    index_type VARCHAR(50) DEFAULT 'IVF_FLAT' COMMENT '索引类型',
    metric_type VARCHAR(50) DEFAULT 'COSINE' COMMENT '距离度量类型',
    connection_params JSON COMMENT '其他连接参数（JSON格式）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    is_default TINYINT(1) DEFAULT 0 COMMENT '是否为默认配置 0-否 1-是',
    description TEXT COMMENT '配置描述',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key),
    INDEX idx_store_type (store_type),
    INDEX idx_enabled (enabled),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量库配置表';

-- 2. 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    kb_name VARCHAR(200) NOT NULL COMMENT '知识库名称',
    kb_key VARCHAR(100) NOT NULL UNIQUE COMMENT '知识库标识键',
    description TEXT COMMENT '知识库描述',
    vector_store_config_id BIGINT NOT NULL COMMENT '关联的向量库配置ID',
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-v3' COMMENT '嵌入模型',
    chunk_size INT DEFAULT 500 COMMENT '文档分块大小',
    chunk_overlap INT DEFAULT 50 COMMENT '分块重叠大小',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    created_by VARCHAR(100) COMMENT '创建者',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_kb_key (kb_key),
    INDEX idx_vector_store_config_id (vector_store_config_id),
    INDEX idx_enabled (enabled),
    INDEX idx_created_by (created_by),
    FOREIGN KEY (vector_store_config_id) REFERENCES vector_store_config(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 3. 知识附件表
CREATE TABLE IF NOT EXISTS knowledge_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    kb_id BIGINT NOT NULL COMMENT '所属知识库ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    file_type VARCHAR(50) NOT NULL COMMENT '文件类型：pdf, docx, txt, md等',
    mime_type VARCHAR(100) COMMENT 'MIME类型',
    file_hash VARCHAR(64) COMMENT '文件MD5哈希值',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '处理状态：PENDING, PROCESSING, COMPLETED, FAILED',
    process_message TEXT COMMENT '处理消息',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    uploaded_by VARCHAR(100) COMMENT '上传者',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_kb_id (kb_id),
    INDEX idx_file_hash (file_hash),
    INDEX idx_status (status),
    INDEX idx_uploaded_by (uploaded_by),
    FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识附件表';

-- 4. 知识片段表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    kb_id BIGINT NOT NULL COMMENT '所属知识库ID',
    attachment_id BIGINT COMMENT '来源附件ID（可为空，表示手动添加的片段）',
    chunk_text TEXT NOT NULL COMMENT '片段文本内容',
    chunk_hash VARCHAR(64) NOT NULL COMMENT '片段内容哈希值',
    chunk_index INT COMMENT '在原文档中的序号',
    vector_id VARCHAR(100) COMMENT '在向量库中的ID',
    metadata JSON COMMENT '元数据（JSON格式）',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    created_by VARCHAR(100) COMMENT '创建者',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_kb_id (kb_id),
    INDEX idx_attachment_id (attachment_id),
    INDEX idx_chunk_hash (chunk_hash),
    INDEX idx_vector_id (vector_id),
    INDEX idx_enabled (enabled),
    FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE,
    FOREIGN KEY (attachment_id) REFERENCES knowledge_attachment(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识片段表';

-- 插入默认的向量库配置
INSERT INTO vector_store_config (config_name, config_key, store_type, host, port, username, password, database_name, collection_name, embedding_dimension, index_type, metric_type, enabled, is_default, description) VALUES
('默认Milvus配置', 'default_milvus', 'milvus', 'localhost', 19530, 'root', 'milvus', 'default', 'vector_store', 1536, 'IVF_FLAT', 'COSINE', 1, 1, '默认的Milvus向量数据库配置');

-- 插入示例知识库
INSERT INTO knowledge_base (kb_name, kb_key, description, vector_store_config_id, embedding_model, chunk_size, chunk_overlap, enabled, created_by) VALUES
('Spring AI Alibaba知识库', 'spring_ai_alibaba_kb', 'Spring AI Alibaba框架相关的知识库', 1, 'text-embedding-v3', 500, 50, 1, 'system');
