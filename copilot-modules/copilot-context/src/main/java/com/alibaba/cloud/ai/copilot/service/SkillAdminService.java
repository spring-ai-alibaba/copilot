package com.alibaba.cloud.ai.copilot.service;

import java.util.List;
import java.util.Map;

/**
 * 技能管理服务（自学习闭环的审核入口）。
 *
 * <p>共享技能库（workspace/skills）与 MySQL 技能市场（skill_market 表）的
 * 列表、内容查看、启停，以及技能草稿（_drafts）的审核晋升/驳回。</p>
 */
public interface SkillAdminService {

    /** 技能列表：共享技能库（含已停用）+ 技能市场（含已下架），附使用统计 */
    List<Map<String, String>> listSkills();

    /** 查看技能的 SKILL.md 内容（含市场技能与已停用技能） */
    String skillContent(String name, String source);

    /** 技能启停：共享技能移动目录，市场技能改 enabled 字段 */
    void updateStatus(String name, boolean enabled, String source);

    /** 待审核草稿列表（含各会话沙箱中的草稿） */
    List<Map<String, String>> listDrafts();

    /** 查看草稿的 SKILL.md 内容（审核用） */
    String draftContent(String name);

    /** 编辑草稿 SKILL.md（审核中修改描述/内容后再晋升） */
    void updateDraftContent(String name, String content);

    /** 晋升草稿为正式技能（人工审核通过） */
    void promoteDraft(String name);

    /** 驳回并删除草稿 */
    void rejectDraft(String name);

    /** 最近技能检索词（写新技能的需求信号） */
    List<Map<String, Object>> recentSearchQueries();
}
