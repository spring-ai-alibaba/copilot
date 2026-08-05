package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.entity.SkillMarketEntity;
import com.alibaba.cloud.ai.copilot.mapper.SkillMarketMapper;
import com.alibaba.cloud.ai.copilot.mapper.SkillUsageLogMapper;
import com.alibaba.cloud.ai.copilot.service.SkillAdminService;
import com.alibaba.cloud.ai.copilot.skill.MysqlSkillRepository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 技能管理服务实现。
 *
 * <p>agent 通过 propose_skill 起草的技能落在 workspace/skills/_drafts/，
 * 默认永远不会自动生效。人工审核后晋升 = 把草稿目录移入 workspace/skills/
 * （下一轮推理即生效），这一"人工确认"动作本身就是晋升闸门。</p>
 *
 * <p>市场技能的数据访问统一走 {@link SkillMarketMapper}；是否启用市场
 * 以 {@link MysqlSkillRepository} Bean 是否存在为准（同一个条件开关）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillAdminServiceImpl implements SkillAdminService {

    private static final String DRAFTS_DIR = "_drafts";
    private static final String SOURCE_MARKET = "market";

    /** usage 日志里 skillId 携带的来源后缀（如 _mysql-market / _workspace-writable） */
    private static final Pattern SOURCE_SUFFIX = Pattern.compile("_(workspace|mysql|filesystem)[\\w-]*$");

    /**
     * 停用技能的存放目录（workspace/skills-disabled）。
     * 注意不能用改名前缀方案：框架 FileSystemSkillRepository 会扫描 skills/ 下
     * 所有子目录且技能名取自 SKILL.md frontmatter，目录名无关（实测复现），
     * 必须把目录移出扫描根才能真正停用。
     */
    private static final String DISABLED_DIR = "skills-disabled";

    private final ObjectProvider<MysqlSkillRepository> skillMarketProvider;
    private final SkillMarketMapper skillMarketMapper;
    private final SkillUsageLogMapper skillUsageLogMapper;
    private final AppProperties appProperties;

    @Override
    public List<Map<String, String>> listSkills() {
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
        // 市场技能直接查表（含 enabled=0 的下架技能；不取 content 大字段）
        if (marketEnabled()) {
            try {
                for (SkillMarketEntity e : skillMarketMapper.selectList(
                        Wrappers.<SkillMarketEntity>lambdaQuery().select(
                                SkillMarketEntity::getName,
                                SkillMarketEntity::getDescription,
                                SkillMarketEntity::getEnabled))) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", e.getName());
                    item.put("description", e.getDescription());
                    item.put("source", SOURCE_MARKET);
                    item.put("enabled", String.valueOf(Boolean.TRUE.equals(e.getEnabled())));
                    result.add(item);
                }
            } catch (Exception e) {
                log.warn("读取技能市场失败: {}", e.getMessage());
            }
        }
        attachUsageStats(result);
        return result;
    }

    @Override
    public String skillContent(String name, String source) {
        requireSafeName(name);
        if (SOURCE_MARKET.equals(source)) {
            SkillMarketEntity entity = marketEnabled() ? skillMarketMapper.selectById(name) : null;
            if (entity == null) {
                throw new ServiceException("市场技能不存在: " + name);
            }
            return entity.getContent();
        }
        Path md = resolveWorkspaceSkillDir(name).resolve("SKILL.md");
        if (!Files.isRegularFile(md)) {
            throw new ServiceException("技能缺少 SKILL.md");
        }
        return readFile(md);
    }

    @Override
    public void updateStatus(String name, boolean enabled, String source) {
        requireSafeName(name);
        if (SOURCE_MARKET.equals(source)) {
            SkillMarketEntity entity = new SkillMarketEntity();
            entity.setName(name);
            entity.setEnabled(enabled);
            if (skillMarketMapper.updateById(entity) == 0) {
                throw new ServiceException("市场技能不存在: " + name);
            }
            return;
        }
        Path active = skillsRoot().resolve(name);
        Path disabled = disabledRoot().resolve(name);
        try {
            if (enabled) {
                if (Files.isDirectory(active)) {
                    return; // 已是启用态
                }
                if (!Files.isDirectory(disabled)) {
                    throw new ServiceException("技能不存在: " + name);
                }
                Files.move(disabled, active, StandardCopyOption.ATOMIC_MOVE);
            } else {
                if (Files.isDirectory(disabled)) {
                    return; // 已是停用态
                }
                if (!Files.isDirectory(active)) {
                    throw new ServiceException("技能不存在: " + name);
                }
                Files.createDirectories(disabledRoot());
                Files.move(active, disabled, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            log.error("技能启停失败: {}", name, e);
            throw new ServiceException("操作失败: " + e.getMessage());
        }
        log.info("技能[{}]已{}", name, enabled ? "启用" : "停用");
    }

    @Override
    public List<Map<String, String>> listDrafts() {
        List<Map<String, String>> result = new ArrayList<>();
        for (Path root : draftRoots()) {
            // root 形如 workspace/skills/_drafts（共享）或 workspace/<conv>/skills/_drafts
            String from = root.startsWith(skillsRoot())
                    ? "" : root.getParent().getParent().getFileName().toString();
            List<Map<String, String>> items = scanSkillDirs(root);
            items.forEach(i -> i.put("conversationId", from));
            result.addAll(items);
        }
        return result;
    }

    @Override
    public String draftContent(String name) {
        Path md = findDraft(name).resolve("SKILL.md");
        if (!Files.isRegularFile(md)) {
            throw new ServiceException("草稿缺少 SKILL.md");
        }
        return readFile(md);
    }

    @Override
    public void updateDraftContent(String name, String content) {
        if (content == null || content.isBlank()) {
            throw new ServiceException("内容不能为空");
        }
        Path draft = findDraft(name);
        try {
            Files.writeString(draft.resolve("SKILL.md"), content);
        } catch (IOException e) {
            log.error("更新技能草稿失败: {}", name, e);
            throw new ServiceException("保存失败: " + e.getMessage());
        }
        log.info("技能草稿已更新: {}", name);
    }

    @Override
    public void promoteDraft(String name) {
        Path draft = findDraft(name);
        if (!Files.isRegularFile(draft.resolve("SKILL.md"))) {
            throw new ServiceException("草稿缺少 SKILL.md，不能晋升");
        }
        Path target = skillsRoot().resolve(name);
        if (Files.exists(target)) {
            throw new ServiceException("同名技能已存在: " + name);
        }
        try {
            Files.move(draft, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("晋升技能草稿失败: {}", name, e);
            throw new ServiceException("晋升失败: " + e.getMessage());
        }
        log.info("技能草稿已晋升: {}", name);
    }

    @Override
    public void rejectDraft(String name) {
        Path draft = findDraft(name);
        try (Stream<Path> walk = Files.walk(draft)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.error("删除技能草稿失败: {}", name, e);
            throw new ServiceException("删除失败: " + e.getMessage());
        }
        log.info("技能草稿已驳回删除: {}", name);
    }

    @Override
    public List<Map<String, Object>> recentSearchQueries() {
        try {
            return skillUsageLogMapper.recentSearchQueries();
        } catch (Exception e) {
            log.warn("读取检索词失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- private

    /** 市场是否启用（与 MysqlSkillRepository 的条件开关一致） */
    private boolean marketEnabled() {
        return skillMarketProvider.getIfAvailable() != null;
    }

    private Path workspaceRoot() {
        return Paths.get(appProperties.getWorkspace().getRootDirectory());
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

    /** 使用统计：skill_id 去掉来源后缀后按基名归并，回填到技能列表 */
    private void attachUsageStats(List<Map<String, String>> result) {
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
    }

    /** 名称合法性校验（防路径穿越，不校验存在性）——所有入口共用这一份 */
    private void requireSafeName(String name) {
        if (name == null || name.isBlank() || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            throw new ServiceException("非法技能名: " + name);
        }
    }

    /** 解析共享技能目录（兼容已停用技能） */
    private Path resolveWorkspaceSkillDir(String name) {
        Path active = skillsRoot().resolve(name);
        if (Files.isDirectory(active)) {
            return active;
        }
        Path disabled = disabledRoot().resolve(name);
        if (Files.isDirectory(disabled)) {
            return disabled;
        }
        throw new ServiceException("技能目录不存在: " + name);
    }

    /** 在所有草稿根中按名查找草稿目录（名称先做合法性校验） */
    private Path findDraft(String name) {
        requireSafeName(name);
        for (Path root : draftRoots()) {
            Path dir = root.resolve(name);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        throw new ServiceException("技能草稿不存在: " + name);
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.error("读取技能内容失败: {}", file, e);
            throw new ServiceException("读取失败: " + e.getMessage());
        }
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

    /** 只在首个 --- ... --- frontmatter 块内取 key，避免正文同名行误匹配 */
    private String frontmatter(String content, String key) {
        boolean inBlock = false;
        for (String line : content.split("\n", 200)) {
            String t = line.trim();
            if (t.equals("---")) {
                if (inBlock) {
                    break; // frontmatter 结束
                }
                inBlock = true;
                continue;
            }
            if (inBlock && t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return "";
    }
}
