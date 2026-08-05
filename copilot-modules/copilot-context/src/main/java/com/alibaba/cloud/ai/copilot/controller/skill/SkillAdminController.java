package com.alibaba.cloud.ai.copilot.controller.skill;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.service.SkillAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 技能管理接口（自学习闭环的审核入口）。
 *
 * <p>只做参数接收与结果包装，业务逻辑见 {@link SkillAdminService}；
 * 业务异常（ServiceException）由全局异常处理器统一转为 R.fail。</p>
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillAdminController {

    private final SkillAdminService skillAdminService;

    /** 技能列表：共享技能库（含已停用）+ MySQL 技能市场（含已下架），附使用统计 */
    @GetMapping
    public R<List<Map<String, String>>> listSkills() {
        return R.ok(skillAdminService.listSkills());
    }

    /** 查看技能的 SKILL.md 内容（含市场技能与已停用技能） */
    @GetMapping("/{name}/content")
    public R<String> skillContent(@PathVariable String name,
                                  @RequestParam(defaultValue = "workspace") String source) {
        // 注意：R.ok(String) 会命中 ok(String msg) 重载导致 data 为 null，必须用双参重载
        return R.ok("操作成功", skillAdminService.skillContent(name, source));
    }

    /** 技能启停：共享技能移动目录，市场技能改 enabled 字段。下一次 agent 构建即生效 */
    @PutMapping("/{name}/status")
    public R<Void> updateStatus(@PathVariable String name,
                                @RequestParam boolean enabled,
                                @RequestParam(defaultValue = "workspace") String source) {
        skillAdminService.updateStatus(name, enabled, source);
        return R.ok();
    }

    /** 待审核草稿列表（含各会话沙箱中的草稿） */
    @GetMapping("/drafts")
    public R<List<Map<String, String>>> listDrafts() {
        return R.ok(skillAdminService.listDrafts());
    }

    /** 查看草稿的 SKILL.md 内容（审核用） */
    @GetMapping("/drafts/{name}/content")
    public R<String> draftContent(@PathVariable String name) {
        return R.ok("操作成功", skillAdminService.draftContent(name));
    }

    /** 编辑草稿 SKILL.md（审核中修改描述/内容后再晋升） */
    @PutMapping("/drafts/{name}/content")
    public R<Void> updateDraftContent(@PathVariable String name, @RequestBody Map<String, String> body) {
        skillAdminService.updateDraftContent(name, body.get("content"));
        return R.ok();
    }

    /** 晋升草稿为正式技能（人工审核通过） */
    @PostMapping("/drafts/{name}/promote")
    public R<Void> promoteDraft(@PathVariable String name) {
        skillAdminService.promoteDraft(name);
        return R.ok();
    }

    /** 驳回并删除草稿 */
    @DeleteMapping("/drafts/{name}")
    public R<Void> rejectDraft(@PathVariable String name) {
        skillAdminService.rejectDraft(name);
        return R.ok();
    }

    /** 最近技能检索词（写新技能的需求信号） */
    @GetMapping("/search-queries")
    public R<List<Map<String, Object>>> searchQueries() {
        return R.ok(skillAdminService.recentSearchQueries());
    }
}
