-- 创建厂商表
CREATE TABLE `llm_factories`
(
    `id`            BIGINT AUTO_INCREMENT COMMENT 'LLM厂商ID',
    `name`          varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'LLM厂商名称',
    `provider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NOT NULL COMMENT '厂商代码',
    `logo`          text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '厂商logo base64字符串',
    `tags`          varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型类型标签: LLM, Text Embedding, Image2Text, ASR',
    `sort_order`    int NULL DEFAULT 0 COMMENT '排序权重',
    `status`        tinyint NULL DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
    `created_time`  timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`  timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `name`(`name` ASC) USING BTREE,
    UNIQUE INDEX `provider_code`(`provider_code` ASC) USING BTREE,
    INDEX           `idx_status`(`status` ASC) USING BTREE,
    INDEX           `idx_tags`(`tags` ASC) USING BTREE,
    INDEX           `idx_code`(`provider_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 创建模型表
CREATE TABLE `llm`
(
    `id`           BIGINT AUTO_INCREMENT COMMENT 'LLM模型ID',
    `fid`          varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '厂商ID（引用 llm_factories.name）',
    `llm_name`     varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称',
    `model_type`   varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型类型: LLM, Text Embedding, Image2Text, ASR',
    `max_tokens`   int NULL DEFAULT 0 COMMENT '最大token数',
    `tags`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '功能标签: LLM, Text Embedding, Image2Text, Chat, 32k...',
    `is_tools`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否支持工具调用',
    `status`       tinyint NULL DEFAULT 0 COMMENT '状态: 0-正常, 1-禁用',
    `created_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_fid_llm_name`(`fid` ASC, `llm_name` ASC) USING BTREE,
    INDEX          `idx_model_type`(`model_type` ASC) USING BTREE,
    INDEX          `idx_llm_name`(`llm_name` ASC) USING BTREE,
    INDEX          `idx_status`(`status` ASC) USING BTREE,
    INDEX          `idx_tags`(`tags` ASC) USING BTREE,
    CONSTRAINT `llm_ibfk_1` FOREIGN KEY (`fid`) REFERENCES `llm_factories` (`provider_code`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 67 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 插入厂商数据
INSERT INTO llm_factories (name, provider_code, logo, tags, sort_order, status)
VALUES ('DeeSeek', 'DeepSeek', NULL, 'LLM,Chat', 10, 0),
       ('阿里百炼', 'ALiBaiLian', NULL, 'LLM,Text Embedding,Chat', 20, 0),
       ('OpenAI', 'OpenAI', NULL, 'LLM,Text Embedding,Image2Text,ASR,Chat', 30, 0),
       ('硅基流动', 'SILICONFLOW', NULL, 'LLM,Text Embedding,Image2Text,ASR,Chat', 40, 0),
       ('自定义供应商', 'OpenAiCompatible', NULL, 'LLM,Text Embedding,Image2Text,ASR,Chat', 40, 0);

-- 插入模型数据
INSERT INTO `llm` (
    `fid`, `llm_name`, `model_type`, `max_tokens`, `tags`,
    `is_tools`, `status`, `created_time`, `updated_time`
) VALUES
-- OpenAI 模型
('OpenAI', 'gpt-3.5-turbo', 'CHAT', 4096, 'LLM,CHAT,4K', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-3.5-turbo-16k-0613', 'CHAT', 16385, 'LLM,CHAT,16k', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4', 'CHAT', 8191, 'LLM,CHAT,8K', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4-32k', 'CHAT', 32768, 'LLM,CHAT,32K', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4-turbo', 'CHAT', 8191, 'LLM,CHAT,8K', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4.1', 'CHAT', 1047576, 'LLM,CHAT,1M,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4.1-mini', 'CHAT', 1047576, 'LLM,CHAT,1M,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4.1-nano', 'CHAT', 1047576, 'LLM,CHAT,1M,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4.5-preview', 'CHAT', 128000, 'LLM,CHAT,128K', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4o', 'CHAT', 128000, 'LLM,CHAT,128K,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-4o-mini', 'CHAT', 128000, 'LLM,CHAT,128K,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-5', 'CHAT', 400000, 'LLM,CHAT,400k,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-5-chat-latest', 'CHAT', 400000, 'LLM,CHAT,400k,IMAGE2TEXT', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-5-mini', 'CHAT', 400000, 'LLM,CHAT,400k,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'gpt-5-nano', 'CHAT', 400000, 'LLM,CHAT,400k,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'o3', 'CHAT', 200000, 'LLM,CHAT,200K,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'o4-mini', 'CHAT', 200000, 'LLM,CHAT,200K,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('OpenAI', 'o4-mini-high', 'CHAT', 200000, 'LLM,CHAT,200K,IMAGE2TEXT', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
-- SILICONFLOW 模型
('SILICONFLOW', 'deepseek-ai/DeepSeek-R1', 'CHAT', 64000, 'LLM,CHAT,64k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-R1-Distill-Qwen-14B', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-R1-Distill-Qwen-32B', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-R1-Distill-Qwen-7B', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-V2.5', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-V3', 'CHAT', 64000, 'LLM,CHAT,64k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'deepseek-ai/DeepSeek-V3.1', 'CHAT', 160000, 'LLM,CHAT,160', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'internlm/internlm2_5-7b-chat', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/deepseek-ai/DeepSeek-R1', 'CHAT', 64000, 'LLM,CHAT,64k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/deepseek-ai/DeepSeek-V3', 'CHAT', 64000, 'LLM,CHAT,64k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/deepseek-ai/DeepSeek-V3.1', 'CHAT', 160000, 'LLM,CHAT,160k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/Qwen/Qwen2-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/Qwen/Qwen2.5-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/Qwen/Qwen2.5-Coder-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Pro/THUDM/glm-4-9b-chat', 'CHAT', 128000, 'LLM,CHAT,128k', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-14B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-32B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-72B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-Coder-32B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 0, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen2.5-Coder-7B-Instruct', 'CHAT', 32000, 'LLM,CHAT,32k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen3-14B', 'CHAT', 128000, 'LLM,CHAT,128k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen3-235B-A22B', 'CHAT', 128000, 'LLM,CHAT,128k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen3-30B-A3B', 'CHAT', 128000, 'LLM,CHAT,128k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen3-32B', 'CHAT', 128000, 'LLM,CHAT,128k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
('SILICONFLOW', 'Qwen/Qwen3-8B', 'CHAT', 64000, 'LLM,CHAT,64k', 1, 1, '2025-12-03 16:37:44', '2025-12-03 16:37:44'),
-- DeepSeek 模型
('DeepSeek', 'deepseek-chat', 'CHAT', 8191, 'LLM,CHAT', 1, 1, '2025-12-05 17:25:47', '2025-12-05 17:25:50'),
('DeepSeek', 'deepseek-reasoner', 'CHAT', 64000, 'LLM,CHAT', 1, 1, '2025-12-05 17:26:44', '2025-12-05 17:26:47'),
-- 阿里百炼模型
('ALiBaiLian', 'qwen3-max-preview', 'CHAT', 64000, 'LLM,CHAT,128k', 1, 1, '2025-12-08 09:14:14', '2025-12-08 13:59:28');

ALTER TABLE model_config
    ADD COLUMN user_id bigint NOT NULL COMMENT '用户ID' AFTER id,
    ADD COLUMN max_token INT NOT NULL DEFAULT 4096 COMMENT '模型最大token数' AFTER provider,
    ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性/权限: PUBLIC(公开), ORGANIZATION(组织), PRIVATE(个人)' AFTER user_id,
    ADD COLUMN model_type VARCHAR(32) NOT NULL DEFAULT 'llm' COMMENT '模型类型: llm(大模型), embedding(文本向量), image2text(图像转文本), asr(语音识别), chat(聊天)' AFTER model_name;
