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

-- ============================================
-- 初始数据：model_config
-- ============================================
INSERT INTO model_config (model_name, model_key, use_image, description, icon_url, provider, api_key, api_url, function_call, enabled, sort_order) VALUES
('deepseek-reasoner', 'deepseek-reasoner', 0, 'Deepseek R1 model with reasoning and chain-of-thought capabilities', NULL, 'deepseek', NULL, NULL, 0, 1, 3),
('deepseek-chat', 'deepseek-chat', 0, 'Deepseek V3 model', NULL, 'deepseek', NULL, NULL, 1, 1, 4);

-- ============================================
-- 恢复外键检查
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 初始化完成
-- ============================================

