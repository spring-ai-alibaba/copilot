package com.alibaba.cloud.ai.copilot.skill;

import com.alibaba.cloud.ai.copilot.mapper.SkillMarketMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 技能市场配置：把 MySQL 技能市场仓库注册为 Bean，供 CopilotAgentFactory 挂到 HarnessAgent。
 *
 * <ul>
 *   <li>app.skills.mysql-market.enabled=false 整体关闭市场；</li>
 *   <li>app.skills.mysql-market.create-table=false 关闭启动建表兜底
 *       （生产库 DDL 受限或走正规迁移时使用，脚本见 docs/scripts/sql/）。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class SkillMarketConfig {

    @Bean
    @ConditionalOnProperty(name = "app.skills.mysql-market.enabled", havingValue = "true", matchIfMissing = true)
    public MysqlSkillRepository mysqlSkillRepository(
            SkillMarketMapper skillMarketMapper,
            @Value("${app.skills.mysql-market.create-table:true}") boolean createTable) {
        if (createTable) {
            skillMarketMapper.ensureTable();
        }
        log.info("初始化 MySQL 技能市场仓库：table=skill_market, createTable={}", createTable);
        return new MysqlSkillRepository(skillMarketMapper);
    }
}
