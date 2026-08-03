package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能市场（skill_market 表）。
 *
 * <p>平台侧集中管理、只读分发的技能条目：运营上/下架靠 enabled 开关，
 * 下一轮推理即生效。SKILL.md 全文存在 content 列（含 frontmatter）。</p>
 */
@Data
@TableName("skill_market")
public class SkillMarketEntity {

    /** 技能名（frontmatter name），业务主键 */
    @TableId(type = IdType.INPUT)
    private String name;

    /** 触发条件式描述 */
    private String description;

    /** SKILL.md 全文（含 frontmatter） */
    private String content;

    /** 上架状态 */
    private Boolean enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
