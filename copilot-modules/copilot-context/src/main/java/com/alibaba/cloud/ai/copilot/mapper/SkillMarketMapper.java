package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.SkillMarketEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 技能市场 Mapper（skill_market 表的唯一数据访问入口）。
 */
@Mapper
public interface SkillMarketMapper extends BaseMapper<SkillMarketEntity> {

    /**
     * 建表兜底，保证开发/演示环境开箱即用。
     *
     * <p>正式变更脚本在 docs/scripts/sql/skill_usage_log.sql；生产库（DDL 受限、
     * 走正规迁移流程）请置 app.skills.mysql-market.create-table=false 关闭本方法。
     * 注意 IF NOT EXISTS 只负责首次建表，后续加列/改列仍需迁移脚本。</p>
     */
    @Update("CREATE TABLE IF NOT EXISTS skill_market ("
            + "name VARCHAR(128) NOT NULL COMMENT '技能名（frontmatter name）',"
            + "description VARCHAR(1000) NOT NULL COMMENT '触发条件式描述',"
            + "content MEDIUMTEXT NOT NULL COMMENT 'SKILL.md 全文（含 frontmatter）',"
            + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态',"
            + "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "PRIMARY KEY (name)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能市场'")
    void ensureTable();
}
