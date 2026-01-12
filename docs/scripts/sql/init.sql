-- ============================================
-- Alibaba Copilot 数据库初始化脚本
-- ============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS spring_ai_copilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE spring_ai_copilot;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 表结构：model_config (模型配置表)
-- ============================================
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id bigint NOT NULL COMMENT '用户ID',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性/权限: PUBLIC(公开), ORGANIZATION(组织), PRIVATE(个人)',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    model_key VARCHAR(100) NOT NULL UNIQUE COMMENT '模型标识键',
    model_type VARCHAR(32) NOT NULL DEFAULT 'llm' COMMENT '模型类型: llm(大模型), embedding(文本向量), image2text(图像转文本), asr(语音识别), chat(聊天)',
    use_image TINYINT(1) DEFAULT 0 COMMENT '是否支持图像处理 0-不支持 1-支持',
    description TEXT COMMENT '模型描述',
    icon_url VARCHAR(500) COMMENT '图标URL',
    provider VARCHAR(50) NOT NULL COMMENT '模型提供商',
    max_token INT NOT NULL DEFAULT 4096 COMMENT '模型最大token数',
    api_key VARCHAR(500) COMMENT 'API密钥',
    api_url VARCHAR(500) COMMENT 'API地址',
    function_call TINYINT(1) DEFAULT 1 COMMENT '是否支持函数调用 0-不支持 1-支持',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_provider (provider),
    INDEX idx_enabled (enabled),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- ============================================
-- 表结构：sys_user (用户信息表)
-- ============================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信用户标识',
  `user_balance` double(20, 2) NULL DEFAULT 0.00 COMMENT '账户余额',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'sys_user' COMMENT '用户类型（sys_user系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '用户邮箱',
  `phone_number` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像地址',
  `wx_avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `domain_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '注册域名',
  `create_time` datetime NULL DEFAULT (curtime()) COMMENT '创建时间',
  `update_by` bigint NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT (curtime()) COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户信息表' ROW_FORMAT = DYNAMIC;

-- 创建 chat_messages 表结构完整
CREATE TABLE IF NOT EXISTS `chat_messages` (
                                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                               `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息ID',
    `role` VARCHAR(20) NOT NULL COMMENT '消息角色：user, assistant, system, tool',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `is_compressed` TINYINT(1) DEFAULT 0 COMMENT '是否为压缩消息',
    `original_count` INT DEFAULT NULL COMMENT '原始消息数量（压缩消息使用）',
    `compression_timestamp` DATETIME DEFAULT NULL COMMENT '压缩时间',
    `metadata` JSON DEFAULT NULL COMMENT '扩展元数据（JSON格式）',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_conversation_compressed` (`conversation_id`, `is_compressed`),
    KEY `idx_created_time` (`created_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- 创建厂商表
CREATE TABLE IF NOT EXISTS `llm_factories`
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
CREATE TABLE IF NOT EXISTS `llm`
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

-- ============================================
-- 表结构：mcp_tool_info (MCP 工具表)
-- ============================================
CREATE TABLE IF NOT EXISTS `mcp_tool_info` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '工具名称',
    `description` TEXT COMMENT '工具描述',
    `type` VARCHAR(20) DEFAULT 'LOCAL' COMMENT '工具类型：LOCAL-本地, REMOTE-远程',
    `status` VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    `config_json` TEXT COMMENT '配置信息（JSON格式）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具表';

-- ============================================
-- 表结构：mcp_market_info (MCP 市场表)
-- ============================================
CREATE TABLE IF NOT EXISTS `mcp_market_info` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '市场名称',
    `url` VARCHAR(500) NOT NULL COMMENT '市场URL',
    `description` TEXT COMMENT '市场描述',
    `auth_config` TEXT COMMENT '认证配置（JSON格式）',
    `status` VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP市场表';

-- ============================================
-- 表结构：mcp_market_tool (MCP 市场工具关联表)
-- ============================================
CREATE TABLE IF NOT EXISTS `mcp_market_tool` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `market_id` BIGINT NOT NULL COMMENT '市场ID',
    `tool_name` VARCHAR(200) NOT NULL COMMENT '工具名称',
    `tool_description` TEXT COMMENT '工具描述',
    `tool_version` VARCHAR(50) COMMENT '工具版本',
    `tool_metadata` JSON COMMENT '工具元数据（JSON格式）',
    `is_loaded` TINYINT(1) DEFAULT 0 COMMENT '是否已加载到本地：0-未加载, 1-已加载',
    `local_tool_id` BIGINT COMMENT '关联的本地工具ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP市场工具关联表';

-- ============================================
-- 插入 MCP 测试数据
-- ============================================

-- 插入一个示例 MCP 市场
INSERT INTO `mcp_market_info` (`name`, `url`, `description`, `status`) VALUES
('MCP Servers 官方市场', 'https://mcpservers.cn/api/servers/list', '官方 MCP 服务器市场，提供丰富的 MCP 工具', 'ENABLED');

-- ============================================
-- 恢复外键检查
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 初始化完成
-- ============================================

