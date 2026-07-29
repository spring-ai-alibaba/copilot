package com.alibaba.cloud.ai.copilot.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 技能检索元工具（大量技能场景下的"三层披露"检索层）。
 *
 * <p>当 available_skills 里没有直接匹配、或技能数量太多不便全量注入时，
 * 模型可用本工具按需求描述检索候选技能，再用 load_skill_through_path 加载。
 * 检索范围：workspace/skills（人工技能）+ MySQL 技能市场（如启用）。
 * 打分用轻量 2-gram 重叠（无外部依赖；技能上量后可替换为向量检索）。</p>
 */
@Slf4j
public class SearchSkillsTool {

    private final Path skillsRoot;
    private final MysqlSkillRepository marketRepo;

    public SearchSkillsTool(Path skillsRoot, MysqlSkillRepository marketRepo) {
        this.skillsRoot = skillsRoot;
        this.marketRepo = marketRepo;
    }

    @Tool(
            name = "search_skills",
            description = "按任务描述检索可用技能。当 available_skills 中没有直接匹配当前任务的技能时使用；" +
                    "返回最相关的技能列表（含 name 和 description），之后用 load_skill_through_path 加载选中的技能。",
            readOnly = true
    )
    public String searchSkills(
            @ToolParam(name = "query", description = "任务需求描述，如：做一个 Excel 报表")
            String query
    ) {
        if (query == null || query.isBlank()) {
            return "错误：query 不能为空";
        }
        List<SkillMeta> candidates = collectCandidates();
        if (candidates.isEmpty()) {
            return "当前没有可检索的技能";
        }
        candidates.forEach(c -> c.score = score(query, c.text()));
        List<SkillMeta> top = candidates.stream()
                .filter(c -> c.score > 0)
                .sorted(Comparator.comparingInt((SkillMeta c) -> c.score).reversed())
                .limit(5)
                .toList();
        if (top.isEmpty()) {
            return "没有找到与「" + query + "」相关的技能，可按通用方式完成任务";
        }
        StringBuilder sb = new StringBuilder("找到 ").append(top.size()).append(" 个候选技能：\n");
        for (SkillMeta c : top) {
            sb.append("- ").append(c.name).append("（来源:").append(c.source).append("）：")
              .append(c.description).append('\n');
        }
        sb.append("使用 load_skill_through_path(skillId=<对应技能的 skill-id>, path=\"SKILL.md\") 加载。");
        return sb.toString();
    }

    private List<SkillMeta> collectCandidates() {
        List<SkillMeta> list = new ArrayList<>();
        // 1. workspace 技能目录
        if (Files.isDirectory(skillsRoot)) {
            try (Stream<Path> dirs = Files.list(skillsRoot)) {
                dirs.filter(Files::isDirectory)
                    .filter(d -> !d.getFileName().toString().startsWith("_")
                            && !d.getFileName().toString().startsWith("."))
                    .forEach(d -> {
                        Path md = d.resolve("SKILL.md");
                        if (Files.isRegularFile(md)) {
                            try {
                                String content = Files.readString(md);
                                list.add(new SkillMeta(
                                        frontmatter(content, "name", d.getFileName().toString()),
                                        frontmatter(content, "description", ""),
                                        "workspace"));
                            } catch (Exception e) {
                                log.debug("读取技能失败: {}", md);
                            }
                        }
                    });
            } catch (Exception e) {
                log.debug("扫描技能目录失败: {}", e.getMessage());
            }
        }
        // 2. MySQL 技能市场
        if (marketRepo != null) {
            for (AgentSkill s : marketRepo.getAllSkills()) {
                try {
                    list.add(new SkillMeta(
                            String.valueOf(s.getMetadata().get("name")),
                            String.valueOf(s.getMetadata().get("description")),
                            "mysql-market"));
                } catch (Exception ignore) {
                    // 元数据缺失的技能跳过
                }
            }
        }
        return list;
    }

    private String frontmatter(String content, String key, String fallback) {
        for (String line : content.split("\n", 60)) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return fallback;
    }

    /** 2-gram 重叠打分（对中英文都可用的零依赖近似） */
    private int score(String query, String text) {
        String q = query.toLowerCase();
        String t = text.toLowerCase();
        int s = 0;
        for (int i = 0; i + 2 <= q.length(); i++) {
            String gram = q.substring(i, i + 2).trim();
            if (gram.length() == 2 && t.contains(gram)) {
                s++;
            }
        }
        return s;
    }

    private static class SkillMeta {
        final String name;
        final String description;
        final String source;
        int score;

        SkillMeta(String name, String description, String source) {
            this.name = name;
            this.description = description;
            this.source = source;
        }

        String text() {
            return name + " " + description;
        }
    }
}
