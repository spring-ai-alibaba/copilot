package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.SkillUsageLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能使用日志 Mapper
 */
@Mapper
public interface SkillUsageLogMapper extends BaseMapper<SkillUsageLogEntity> {
}
