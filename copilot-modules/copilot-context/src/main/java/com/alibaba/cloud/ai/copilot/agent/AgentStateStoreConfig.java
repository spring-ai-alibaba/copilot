package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * agentscope 会话状态持久化配置。
 *
 * <p>用 {@link MysqlAgentStateStore} 把 {@code AgentState}（含 {@code List<Msg>} 消息历史）
 * 持久化到项目业务库的 {@code agentscope_sessions} 表。agentscope 在 agent.call 前自动
 * load、call 后自动 save（AgentBase 用 Mono.using 包裹 load→执行→save），因此多轮对话历史
 * 天然连续——替代旧的 ConversationHistoryHook 还原 tool_calls 链方案，避开 Msg/spring-ai
 * Message 结构差异的复杂度。</p>
 *
 * <p>库名从 DataSource 连接的 catalog 解析（项目业务库），避免 {@code MysqlAgentStateStore(ds,true)}
 * 默认库名 {@code agentscope} 不存在导致 verifyDatabaseExists 失败。</p>
 *
 * <p>sessionId 由 AG-UI adapter 设为 threadId（=conversationId），按会话隔离；
 * userId 未由 adapter 设置，统一为 null，靠 conversationId 唯一性隔离。</p>
 */
@Slf4j
@Configuration
public class AgentStateStoreConfig {

    private static final String TABLE_NAME = "agentscope_sessions";

    @Bean
    public MysqlAgentStateStore agentStateStore(DataSource dataSource) {
        String catalog = resolveCatalog(dataSource);
        log.info("初始化 MysqlAgentStateStore：database={}, table={}, createIfNotExist=true", catalog, TABLE_NAME);
        // createIfNotExist=true：自动建表（表在项目业务库下）
        return new MysqlAgentStateStore(dataSource, catalog, TABLE_NAME, true);
    }

    /**
     * 从 DataSource 连接解析当前库名（MySQL catalog = database name）。
     */
    private String resolveCatalog(DataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            if (catalog == null || catalog.isBlank()) {
                throw new IllegalStateException("无法从 DataSource 解析数据库名（catalog 为空）");
            }
            return catalog;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 MysqlAgentStateStore 失败：无法解析数据库名", e);
        }
    }
}
