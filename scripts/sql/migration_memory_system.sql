-- ===================================================================
-- AI Copilot 记忆系统数据库迁移脚本
-- ===================================================================

-- 扩展 chat_messages 表（如果表不存在则创建）
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

-- 如果表已存在，添加新字段
ALTER TABLE `chat_messages` 
ADD COLUMN IF NOT EXISTS `is_compressed` TINYINT(1) DEFAULT 0 COMMENT '是否为压缩消息',
ADD COLUMN IF NOT EXISTS `original_count` INT DEFAULT NULL COMMENT '原始消息数量（压缩消息使用）',
ADD COLUMN IF NOT EXISTS `compression_timestamp` DATETIME DEFAULT NULL COMMENT '压缩时间',
ADD COLUMN IF NOT EXISTS `metadata` JSON DEFAULT NULL COMMENT '扩展元数据（JSON格式）';

-- 创建索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_conversation_compressed` ON `chat_messages`(`conversation_id`, `is_compressed`);

