-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS spring_ai_copilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE spring_ai_copilot;

-- 创建模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    model_key VARCHAR(100) NOT NULL UNIQUE COMMENT '模型标识键',
    use_image TINYINT(1) DEFAULT 0 COMMENT '是否支持图像处理 0-不支持 1-支持',
    description TEXT COMMENT '模型描述',
    icon_url VARCHAR(500) COMMENT '图标URL',
    provider VARCHAR(50) NOT NULL COMMENT '模型提供商',
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

-- 插入初始数据
INSERT INTO model_config (model_name, model_key, use_image, description, icon_url, provider, api_key, api_url, function_call, enabled, sort_order) VALUES
('deepseek-R1', 'deepseek-reasoner', 0, 'Deepseek R1 model with reasoning and chain-of-thought capabilities', NULL, 'deepseek', NULL, NULL, 0, 1, 3),
('deepseek-v3', 'deepseek-chat', 0, 'Deepseek V3 model', NULL, 'deepseek', NULL, NULL, 1, 1, 4)
