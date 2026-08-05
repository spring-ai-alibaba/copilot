-- 技能使用日志：记录每次 load_skill_through_path / search_skills 调用
-- 是"注入了没被 load / load 了但失败"等匹配准确率归因分析的数据源
-- （agentscope 2.0.0 自带的 .usage.json 只统计 agent 自建技能，人工技能需要本表）
CREATE TABLE IF NOT EXISTS skill_usage_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id VARCHAR(64)     NOT NULL COMMENT '会话ID',
    user_id         BIGINT          NULL     COMMENT '用户ID',
    skill_id        VARCHAR(128)    NOT NULL COMMENT '技能ID',
    tool_name       VARCHAR(64)     NOT NULL COMMENT '触发工具名（load_skill_through_path / search_skills）',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_skill_id (skill_id),
    KEY idx_conversation_id (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='技能使用日志';

-- 技能市场表（本脚本为正式变更入口；SkillMarketMapper.ensureTable() 仅做开发环境
-- 启动兜底，可用 app.skills.mysql-market.create-table=false 关闭）
CREATE TABLE IF NOT EXISTS skill_market (
    name         VARCHAR(128)  NOT NULL COMMENT '技能名（frontmatter name）',
    description  VARCHAR(1000) NOT NULL COMMENT '触发条件式描述',
    content      MEDIUMTEXT    NOT NULL COMMENT 'SKILL.md 全文（含 frontmatter）',
    enabled      TINYINT       NOT NULL DEFAULT 1 COMMENT '上架状态',
    created_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='技能市场';
