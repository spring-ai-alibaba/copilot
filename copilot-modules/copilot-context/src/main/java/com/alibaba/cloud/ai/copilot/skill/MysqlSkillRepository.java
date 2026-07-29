package com.alibaba.cloud.ai.copilot.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MySQL 技能市场仓库（自研，实现 agentscope 的 {@link AgentSkillRepository} 扩展点）。
 *
 * <p>agentscope 2.0.0 官方扩展包暂无 MySQL 技能仓库实现，这里基于 skill_market 表
 * 提供"平台侧集中管理、只读分发"的技能市场层：运营在表里上/下架技能（enabled 开关），
 * 下一轮推理即生效，无需重启或改代码。与 workspace/skills 的四层覆盖关系由框架处理
 * （市场层优先级低于工作区，同名时工作区版本生效）。</p>
 */
@Slf4j
public class MysqlSkillRepository implements AgentSkillRepository {

    private static final String SOURCE = "mysql-market";

    private final DataSource dataSource;
    private final String tableName;

    public MysqlSkillRepository(DataSource dataSource, String tableName, boolean createIfNotExist) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        if (createIfNotExist) {
            createTableIfNotExist();
        }
    }

    private void createTableIfNotExist() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "name VARCHAR(128) NOT NULL COMMENT '技能名（frontmatter name）'," +
                "description VARCHAR(1000) NOT NULL COMMENT '触发条件式描述'," +
                "content MEDIUMTEXT NOT NULL COMMENT 'SKILL.md 全文（含 frontmatter）'," +
                "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态'," +
                "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能市场'";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(ddl);
        } catch (Exception e) {
            throw new IllegalStateException("初始化技能市场表失败: " + tableName, e);
        }
    }

    @Override
    public AgentSkill getSkill(String name) {
        String sql = "SELECT name, description, content FROM " + tableName + " WHERE name = ? AND enabled = 1";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toSkill(rs) : null;
            }
        } catch (Exception e) {
            log.warn("读取市场技能失败: name={}, err={}", name, e.getMessage());
            return null;
        }
    }

    @Override
    public List<String> getAllSkillNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM " + tableName + " WHERE enabled = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        } catch (Exception e) {
            log.warn("读取市场技能名列表失败: {}", e.getMessage());
        }
        return names;
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        List<AgentSkill> skills = new ArrayList<>();
        String sql = "SELECT name, description, content FROM " + tableName + " WHERE enabled = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skills.add(toSkill(rs));
            }
        } catch (Exception e) {
            log.warn("读取市场技能列表失败: {}", e.getMessage());
        }
        return skills;
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public boolean isWriteable() {
        // 只读分发：技能上/下架由平台侧直接操作 skill_market 表
        return false;
    }

    @Override
    public void setWriteable(boolean writeable) {
        // 只读仓库，忽略写开关
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("mysql", tableName, false);
    }

    @Override
    public boolean skillExists(String name) {
        return getSkill(name) != null;
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        // 只读分发，不支持从 agent 侧写回
        return false;
    }

    @Override
    public boolean delete(String skillName) {
        return false;
    }

    @Override
    public void close() {
        // DataSource 由 Spring 管理，无需关闭
    }

    private AgentSkill toSkill(ResultSet rs) throws Exception {
        return new AgentSkill(
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("content"),
                Map.of(),
                SOURCE);
    }
}
