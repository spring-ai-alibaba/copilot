package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能使用日志。
 *
 * <p>agentscope 2.0.0 内置的 skills/.usage.json 只统计 agent 自建技能，
 * 人工编写的工作区/市场技能的使用归因（哪个会话加载了哪个技能）落在本表，
 * 供匹配准确率评测与技能治理使用。</p>
 */
@Data
@TableName("skill_usage_log")
public class SkillUsageLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String conversationId;

    /** 用户ID */
    private Long userId;

    /** 技能ID（如 frontend-style_filesystem-workspace_skills） */
    private String skillId;

    /** 触发工具名（load_skill_through_path / search_skills） */
    private String toolName;

    private LocalDateTime createdTime;
}
