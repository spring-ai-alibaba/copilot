package com.alibaba.cloud.ai.copilot.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 技能市场配置：把 MySQL 技能市场仓库注册为 Bean，供 CopilotAgentFactory 挂到 HarnessAgent。
 * 通过 app.skills.mysql-market.enabled=false 可整体关闭。
 */
@Slf4j
@Configuration
public class SkillMarketConfig {

    @Bean
    @ConditionalOnProperty(name = "app.skills.mysql-market.enabled", havingValue = "true", matchIfMissing = true)
    public MysqlSkillRepository mysqlSkillRepository(DataSource dataSource) {
        log.info("初始化 MySQL 技能市场仓库：table=skill_market, createIfNotExist=true");
        return new MysqlSkillRepository(dataSource, "skill_market", true);
    }
}
