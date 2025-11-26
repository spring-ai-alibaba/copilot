-- 创建厂商表
CREATE TABLE llm_factories
(
    id           INT AUTO_INCREMENT PRIMARY KEY COMMENT 'LLM厂商ID',
    name         VARCHAR(128) NOT NULL UNIQUE COMMENT 'LLM厂商名称',
    logo         TEXT NULL COMMENT '厂商logo base64字符串',
    tags         VARCHAR(255) NOT NULL COMMENT '模型类型标签: LLM, Text Embedding, Image2Text, ASR',
    sort_order    INTEGER   DEFAULT 0 COMMENT '排序权重',
    status       TINYINT   DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX        idx_status (status),
    INDEX        idx_tags (tags)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 创建模型表
CREATE TABLE llm
(
    id           INT AUTO_INCREMENT PRIMARY KEY COMMENT 'LLM模型ID',
    fid          VARCHAR(128) NOT NULL COMMENT '厂商ID（引用 llm_factories.name）',
    llm_name     VARCHAR(128) NOT NULL COMMENT '模型名称',
    model_type   VARCHAR(128) NOT NULL COMMENT '模型类型: LLM, Text Embedding, Image2Text, ASR',
    max_tokens   INTEGER               DEFAULT 0 COMMENT '最大token数',
    tags         VARCHAR(255) NOT NULL COMMENT '功能标签: LLM, Text Embedding, Image2Text, Chat, 32k...',
    is_tools     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否支持工具调用',
    status       TINYINT               DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
    created_time TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_fid_llm_name (fid, llm_name), -- 联合唯一约束替代主键
    INDEX        idx_model_type (model_type),
    INDEX        idx_llm_name (llm_name),
    INDEX        idx_status (status),
    INDEX        idx_tags (tags),

    FOREIGN KEY (fid) REFERENCES llm_factories (name) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入厂商数据
INSERT INTO llm_factories (name, logo, tags, sort_order, status)
VALUES ('DeeSeek', NULL, 'LLM,Chat', 10, 0),
       ('百炼', NULL, 'LLM,Text Embedding,Chat', 20, 0),
       ('OpenAI', NULL, 'LLM,Text Embedding,Image2Text,ASR,Chat', 30, 0),
       ('SILICONFLOW', NULL, 'LLM,Text Embedding,Image2Text,ASR,Chat', 40, 0);

ALTER TABLE model_config
    ADD COLUMN max_token INT NOT NULL DEFAULT 4096 COMMENT '模型最大token数' AFTER provider;

ALTER TABLE model_config
    ADD COLUMN user_id bigint NOT NULL COMMENT '用户ID' AFTER id;