package com.alibaba.cloud.ai.copilot.controller.skill;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 技能管理接口（自学习闭环的审核入口）。
 *
 * <p>agent 通过 propose_skill 起草的技能落在 workspace/skills/_drafts/，
 * 默认永远不会自动生效。本控制器提供草稿的查看 / 晋升 / 驳回：
 * 人工审核后晋升 = 把草稿目录移入 workspace/skills/（下一轮推理即生效），
 * 这一"人工确认"动作本身就是晋升闸门。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillAdminController {

    private static final String DRAFTS_DIR = "_drafts";

    private Path workspaceRoot() {
        return Paths.get(System.getProperty("user.dir"), "workspace");
    }

    private Path skillsRoot() {
        return workspaceRoot().resolve("skills");
    }

    /**
     * 草稿根列表：共享 skills/_drafts + 各会话沙箱内的 skills/_drafts。
     * agent 的 workspace 已收敛到会话目录，propose_skill 的草稿落在
     * workspace/&lt;conversationId&gt;/skills/_drafts/。
     */
    private List<Path> draftRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(skillsRoot().resolve(DRAFTS_DIR));
        Path ws = workspaceRoot();
        if (Files.isDirectory(ws)) {
            try (Stream<Path> dirs = Files.list(ws)) {
                dirs.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        return !n.equals("skills") && !n.equals("agents")
                                && !n.startsWith(".") && !n.startsWith("_");
                    })
                    .map(d -> d.resolve("skills").resolve(DRAFTS_DIR))
                    .filter(Files::isDirectory)
                    .forEach(roots::add);
            } catch (IOException e) {
                log.warn("扫描会话草稿目录失败: {}", e.getMessage());
            }
        }
        return roots;
    }

    /** 已生效技能列表 */
    @GetMapping
    public R<List<Map<String, String>>> listSkills() {
        return R.ok(scanSkillDirs(skillsRoot()));
    }

    /** 待审核草稿列表（含各会话沙箱中的草稿） */
    @GetMapping("/drafts")
    public R<List<Map<String, String>>> listDrafts() {
        List<Map<String, String>> result = new ArrayList<>();
        for (Path root : draftRoots()) {
            // root 形如 workspace/skills/_drafts（共享）或 workspace/<conv>/skills/_drafts
            String from = root.startsWith(skillsRoot())
                    ? "" : root.getParent().getParent().getFileName().toString();
            List<Map<String, String>> items = scanSkillDirs(root);
            items.forEach(i -> i.put("conversationId", from));
            result.addAll(items);
        }
        return R.ok(result);
    }

    /** 晋升草稿为正式技能（人工审核通过） */
    @PostMapping("/drafts/{name}/promote")
    public R<Void> promoteDraft(@PathVariable String name) {
        try {
            Path draft = findDraft(name);
            if (!Files.isRegularFile(draft.resolve("SKILL.md"))) {
                return R.fail("草稿缺少 SKILL.md，不能晋升");
            }
            Path target = skillsRoot().resolve(name);
            if (Files.exists(target)) {
                return R.fail("同名技能已存在: " + name);
            }
            Files.move(draft, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("技能草稿已晋升: {}", name);
            return R.ok();
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("晋升技能草稿失败: {}", name, e);
            return R.fail("晋升失败: " + e.getMessage());
        }
    }

    /** 驳回并删除草稿 */
    @DeleteMapping("/drafts/{name}")
    public R<Void> rejectDraft(@PathVariable String name) {
        try {
            Path draft = findDraft(name);
            try (Stream<Path> walk = Files.walk(draft)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> p.toFile().delete());
            }
            log.info("技能草稿已驳回删除: {}", name);
            return R.ok();
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("删除技能草稿失败: {}", name, e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }

    /** 目录名合法性 + 存在性校验，返回目录路径 */
    private Path requireSkillDir(Path parent, String name) {
        if (name == null || name.isBlank() || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            throw new SecurityException("非法技能名: " + name);
        }
        Path dir = parent.resolve(name);
        if (!Files.isDirectory(dir)) {
            throw new SecurityException("技能目录不存在: " + name);
        }
        return dir;
    }

    /** 在所有草稿根中按名查找草稿目录（名称先做合法性校验） */
    private Path findDraft(String name) {
        if (name == null || name.isBlank() || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            throw new SecurityException("非法技能名: " + name);
        }
        for (Path root : draftRoots()) {
            Path dir = root.resolve(name);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        throw new SecurityException("技能草稿不存在: " + name);
    }

    private List<Map<String, String>> scanSkillDirs(Path root) {
        List<Map<String, String>> result = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                .filter(d -> !d.getFileName().toString().startsWith("_")
                        && !d.getFileName().toString().startsWith("."))
                .forEach(d -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", d.getFileName().toString());
                    Path md = d.resolve("SKILL.md");
                    if (Files.isRegularFile(md)) {
                        try {
                            String content = Files.readString(md);
                            item.put("description", frontmatter(content, "description"));
                        } catch (IOException ignore) {
                            item.put("description", "");
                        }
                    } else {
                        item.put("description", "(缺少 SKILL.md)");
                    }
                    result.add(item);
                });
        } catch (IOException e) {
            log.warn("扫描技能目录失败: {}", e.getMessage());
        }
        return result;
    }

    private String frontmatter(String content, String key) {
        for (String line : content.split("\n", 60)) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return "";
    }
}
