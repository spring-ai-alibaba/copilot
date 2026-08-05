package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.SkillUsageLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 技能使用日志 Mapper
 */
@Mapper
public interface SkillUsageLogMapper extends BaseMapper<SkillUsageLogEntity> {

    /**
     * 按 skill_id 聚合技能加载次数与最近使用时间（仅统计真实加载，不含检索）。
     * skill_id 带来源后缀（如 _mysql-market），基名归并在 Java 侧完成。
     */
    @Select("SELECT skill_id AS skillId, COUNT(*) AS cnt, MAX(created_time) AS lastUsed "
            + "FROM skill_usage_log WHERE tool_name = 'load_skill_through_path' GROUP BY skill_id")
    List<Map<String, Object>> aggregateLoadUsage();

    /**
     * 最近的技能检索词（search_skills 的 query 记在 skill_id 列）。
     * 高频出现却始终没有技能命中的检索词，就是"该写什么新技能"的需求信号。
     */
    @Select("SELECT skill_id AS query, COUNT(*) AS cnt, MAX(created_time) AS lastAt "
            + "FROM skill_usage_log WHERE tool_name = 'search_skills' "
            + "GROUP BY skill_id ORDER BY MAX(created_time) DESC LIMIT 50")
    List<Map<String, Object>> recentSearchQueries();
}
