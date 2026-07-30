package com.alibaba.cloud.ai.copilot.controller.skill;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.mapper.SkillUsageLogMapper;
import com.alibaba.cloud.ai.copilot.skill.MysqlSkillRepository;
import io.agentscope.core.skill.AgentSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequiredArgsConstructor
public class SkillAdminController {

    private static final String DRAFTS_DIR = "_drafts";

    /** usage 日志里 skillId 携带的来源后缀（如 _mysql-market / _workspace-writable） */
    private static final java.util.regex.Pattern SOURCE_SUFFIX =
            java.util.regex.Pattern.compile("_(workspace|mysql|filesystem)[\\w-]*$");

    /**
     * 停用技能的存放目录（workspace/skills-disabled）。
     * 注意不能用改名前缀方案：框架 FileSystemSkillRepository 会扫描 skills/ 下
     * 所有子目录且技能名取自 SKILL.md frontmatter，目录名无关（实测复现），
     * 必须把目录移出扫描根才能真正停用。
     */
    private static final String DISABLED_DIR = "skills-disabled";

    private final ObjectProvider<MysqlSkillRepository> skillMarketProvider;
    private final SkillUsageLogMapper skillUsageLogMapper;
    private final JdbcTemplate jdbcTemplate;

    private Path workspaceRoot() {
        return Paths.get(System.getProperty("user.dir"), "workspace");
    }

    private Path skillsRoot() {
        return workspaceRoot().resolve("skills");
    }

    private Path disabledRoot() {
        return workspaceRoot().resolve(DISABLED_DIR);
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
                        return !n.equals("skills") && !n.equals(DISABLED_DIR) && !n.equals("agents")
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

    /** 技能列表：共享技能库（含已停用）+ MySQL 技能市场（含已下架），附使用统计 */
    @GetMapping
    public R<List<Map<String, String>>> listSkills() {
        List<Map<String, String>> result = scanSkillDirs(skillsRoot());
        result.forEach(i -> {
            i.put("source", "workspace");
            i.put("enabled", "true");
        });
        // 已停用的共享技能（在 workspace/skills-disabled/，不在框架扫描根内）
        result.addAll(scanSkillDirs(disabledRoot()).stream()
                .peek(i -> {
                    i.put("source", "workspace");
                    i.put("enabled", "false");
                })
                .toList());
        // 市场技能直接查表（含 enabled=0 的下架技能）
        if (skillMarketProvider.getIfAvailable() != null) {
            try {
                jdbcTemplate.query("SELECT name, description, enabled FROM skill_market", rs -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", rs.getString("name"));
                    item.put("description", rs.getString("description"));
                    item.put("source", "market");
                    item.put("enabled", String.valueOf(rs.getBoolean("enabled")));
                    result.add(item);
                });
            } catch (Exception e) {
                log.warn("读取技能市场失败: {}", e.getMessage());
            }
        }
        // 使用统计：skill_id 去掉来源后缀后按基名归并
        Map<String, long[]> usage = new LinkedHashMap<>(); // name -> [count]
        Map<String, String> lastUsed = new LinkedHashMap<>();
        try {
            for (Map<String, Object> row : skillUsageLogMapper.aggregateLoadUsage()) {
                String base = SOURCE_SUFFIX.matcher(String.valueOf(row.get("skillId"))).replaceAll("");
                long cnt = ((Number) row.get("cnt")).longValue();
                usage.computeIfAbsent(base, k -> new long[1])[0] += cnt;
                String last = String.valueOf(row.get("lastUsed"));
                if (!lastUsed.containsKey(base) || last.compareTo(lastUsed.get(base)) > 0) {
                    lastUsed.put(base, last);
                }
            }
        } catch (Exception e) {
            log.warn("读取技能使用统计失败: {}", e.getMessage());
        }
        for (Map<String, String> item : result) {
            String name = item.get("name");
            item.put("usageCount", String.valueOf(usage.containsKey(name) ? usage.get(name)[0] : 0));
            item.put("lastUsed", lastUsed.getOrDefault(name, ""));
        }
        return R.ok(result);
    }

    /** 查看技能的 SKILL.md 内容（含市场技能与已停用技能） */
    @GetMapping("/{name}/content")
    public R<String> skillContent(@PathVariable String name,
                                  @RequestParam(defaultValue = "workspace") String source) {
        try {
            if ("market".equals(source)) {
                requireSafeName(name);
                String content = null;
                MysqlSkillRepository market = skillMarketProvider.getIfAvailable();
                AgentSkill skill = market != null ? market.getSkill(name) : null;
                if (skill != null) {
                    content = skill.getSkillContent();
                } else if (market != null) {
                    // 已下架技能 repo 查不到（enabled=1 过滤），直接查表
                    List<String> rows = jdbcTemplate.queryForList(
                            "SELECT content FROM skill_market WHERE name = ?", String.class, name);
                    content = rows.isEmpty() ? null : rows.get(0);
                }
                if (content == null) {
                    return R.fail("市场技能不存在: " + name);
                }
                return R.ok("操作成功", content);
            }
            Path dir = resolveWorkspaceSkillDir(name);
            Path md = dir.resolve("SKILL.md");
            if (!Files.isRegularFile(md)) {
                return R.fail("技能缺少 SKILL.md");
            }
            return R.ok("操作成功", Files.readString(md));
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("读取技能内容失败: {}", name, e);
            return R.fail("读取失败: " + e.getMessage());
        }
    }

    /**
     * 技能启停。共享技能通过目录改名实现（加/去 _disabled_ 前缀，
     * 框架发现器天然跳过下划线目录）；市场技能直接改表里的 enabled 字段。
     * 下一次 agent 构建即生效。
     */
    @PutMapping("/{name}/status")
    public R<Void> updateStatus(@PathVariable String name,
                                @RequestParam boolean enabled,
                                @RequestParam(defaultValue = "workspace") String source) {
        try {
            requireSafeName(name);
            if ("market".equals(source)) {
                int n = jdbcTemplate.update(
                        "UPDATE skill_market SET enabled = ? WHERE name = ?", enabled ? 1 : 0, name);
                return n > 0 ? R.ok() : R.fail("市场技能不存在: " + name);
            }
            Path active = skillsRoot().resolve(name);
            Path disabled = disabledRoot().resolve(name);
            if (enabled) {
                if (Files.isDirectory(active)) {
                    return R.ok(); // 已是启用态
                }
                if (!Files.isDirectory(disabled)) {
                    return R.fail("技能不存在: " + name);
                }
                Files.move(disabled, active, StandardCopyOption.ATOMIC_MOVE);
            } else {
                if (Files.isDirectory(disabled)) {
                    return R.ok(); // 已是停用态
                }
                if (!Files.isDirectory(active)) {
                    return R.fail("技能不存在: " + name);
                }
                Files.createDirectories(disabledRoot());
                Files.move(active, disabled, StandardCopyOption.ATOMIC_MOVE);
            }
            log.info("技能[{}]已{}", name, enabled ? "启用" : "停用");
            return R.ok();
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("技能启停失败: {}", name, e);
            return R.fail("操作失败: " + e.getMessage());
        }
    }

    /** 编辑草稿 SKILL.md（审核中修改描述/内容后再晋升） */
    @PutMapping("/drafts/{name}/content")
    public R<Void> updateDraftContent(@PathVariable String name, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return R.fail("内容不能为空");
        }
        try {
            Path draft = findDraft(name);
            Files.writeString(draft.resolve("SKILL.md"), content);
            log.info("技能草稿已更新: {}", name);
            return R.ok();
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("更新技能草稿失败: {}", name, e);
            return R.fail("保存失败: " + e.getMessage());
        }
    }

    /** 最近技能检索词（写新技能的需求信号） */
    @GetMapping("/search-queries")
    public R<List<Map<String, Object>>> searchQueries() {
        try {
            return R.ok(skillUsageLogMapper.recentSearchQueries());
        } catch (Exception e) {
            log.warn("读取检索词失败: {}", e.getMessage());
            return R.ok(new ArrayList<>());
        }
    }

    /** 名称合法性校验（不校验目录存在性） */
    private void requireSafeName(String name) {
        if (name == null || name.isBlank() || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            throw new SecurityException("非法技能名: " + name);
        }
    }

    /** 解析共享技能目录（兼容已停用技能） */
    private Path resolveWorkspaceSkillDir(String name) {
        requireSafeName(name);
        Path active = skillsRoot().resolve(name);
        if (Files.isDirectory(active)) {
            return active;
        }
        Path disabled = disabledRoot().resolve(name);
        if (Files.isDirectory(disabled)) {
            return disabled;
        }
        throw new SecurityException("技能目录不存在: " + name);
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

    /** 查看草稿的 SKILL.md 内容（审核用） */
    @GetMapping("/drafts/{name}/content")
    public R<String> draftContent(@PathVariable String name) {
        try {
            Path draft = findDraft(name);
            Path md = draft.resolve("SKILL.md");
            if (!Files.isRegularFile(md)) {
                return R.fail("草稿缺少 SKILL.md");
            }
            // 注意：R.ok(String) 会命中 ok(String msg) 重载导致 data 为 null，必须用双参重载
            return R.ok("操作成功", Files.readString(md));
        } catch (SecurityException e) {
            return R.fail(e.getMessage());
        } catch (IOException e) {
            log.error("读取技能草稿失败: {}", name, e);
            return R.fail("读取失败: " + e.getMessage());
        }
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
                .forEach(d -> result.add(readSkillDirMeta(d)));
        } catch (IOException e) {
            log.warn("扫描技能目录失败: {}", e.getMessage());
        }
        return result;
    }

    /** 读取技能目录的 name/description 元信息 */
    private Map<String, String> readSkillDirMeta(Path d) {
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
        return item;
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
