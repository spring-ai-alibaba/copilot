package com.alibaba.cloud.ai.copilot.skill;

import com.alibaba.cloud.ai.copilot.domain.entity.SkillMarketEntity;
import com.alibaba.cloud.ai.copilot.mapper.SkillMarketMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * MySQL 技能市场仓库（自研，实现 agentscope 的 {@link AgentSkillRepository} 扩展点）。
 *
 * <p>agentscope 2.0.0 官方扩展包暂无 MySQL 技能仓库实现，这里基于 skill_market 表
 * 提供"平台侧集中管理、只读分发"的技能市场层：运营在表里上/下架技能（enabled 开关），
 * 下一轮推理即生效，无需重启或改代码。与 workspace/skills 的四层覆盖关系由框架处理
 * （市场层优先级低于工作区，同名时工作区版本生效）。</p>
 *
 * <p>数据访问统一走 {@link SkillMarketMapper}（MyBatis-Plus），与项目其余持久层
 * 保持同一套栈；建表兜底见 {@link SkillMarketMapper#ensureTable()}，由
 * {@link SkillMarketConfig} 按配置决定是否执行。</p>
 */
@Slf4j
public class MysqlSkillRepository implements AgentSkillRepository {

    private static final String SOURCE = "mysql-market";
    private static final String TABLE = "skill_market";

    private final SkillMarketMapper mapper;

    public MysqlSkillRepository(SkillMarketMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentSkill getSkill(String name) {
        try {
            SkillMarketEntity entity = mapper.selectOne(enabledQuery()
                    .eq(SkillMarketEntity::getName, name));
            return entity == null ? null : toSkill(entity);
        } catch (Exception e) {
            // 市场不可用时降级为"查无此技能"，不阻塞会话（工作区技能不受影响）
            log.warn("读取市场技能失败: name={}, err={}", name, e.getMessage());
            return null;
        }
    }

    @Override
    public List<String> getAllSkillNames() {
        try {
            return mapper.selectList(enabledQuery().select(SkillMarketEntity::getName))
                    .stream()
                    .map(SkillMarketEntity::getName)
                    .toList();
        } catch (Exception e) {
            log.warn("读取市场技能名列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        try {
            return mapper.selectList(enabledQuery())
                    .stream()
                    .map(this::toSkill)
                    .toList();
        } catch (Exception e) {
            log.warn("读取市场技能列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public boolean isWriteable() {
        // 只读分发：技能上/下架由平台侧操作 skill_market 表（SkillAdminService）
        return false;
    }

    @Override
    public void setWriteable(boolean writeable) {
        // 只读仓库，忽略写开关
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("mysql", TABLE, false);
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
        // Mapper/DataSource 由 Spring 管理，无需关闭
    }

    private LambdaQueryWrapper<SkillMarketEntity> enabledQuery() {
        return Wrappers.<SkillMarketEntity>lambdaQuery().eq(SkillMarketEntity::getEnabled, true);
    }

    private AgentSkill toSkill(SkillMarketEntity entity) {
        return new AgentSkill(
                entity.getName(),
                entity.getDescription(),
                entity.getContent(),
                Map.of(),
                SOURCE);
    }
}
