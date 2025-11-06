package com.alibaba.cloud.ai.copilot.context.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话上下文领域模型
 * 存储对话过程中的上下文信息
 *
 * @author Alibaba Cloud AI Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationContext {

    /**
     * 上下文唯一标识
     */
    private String contextId;

    /**
     * 关联的会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 上下文范围
     */
    private ContextScope scope;

    /**
     * 上下文状态
     */
    private ContextStatus status;

    /**
     * 上下文数据
     */
    @Builder.Default
    private Map<String, Object> data = new ConcurrentHashMap<>();

    /**
     * 上下文变量
     */
    @Builder.Default
    private Map<String, String> variables = new ConcurrentHashMap<>();

    /**
     * 上下文元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 上下文状态枚举
     */
    public enum ContextStatus {
        ACTIVE("active", "活跃"),
        INACTIVE("inactive", "非活跃"),
        EXPIRED("expired", "已过期"),
        ARCHIVED("archived", "已归档");

        private final String code;
        private final String name;

        ContextStatus(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 设置上下文数据
     *
     * @param key 键
     * @param value 值
     */
    public void setData(String key, Object value) {
        this.data.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 获取上下文数据
     *
     * @param key 键
     * @return 值
     */
    public Object getData(String key) {
        this.lastAccessedAt = LocalDateTime.now();
        return this.data.get(key);
    }

    /**
     * 设置上下文变量
     *
     * @param key 键
     * @param value 值
     */
    public void setVariable(String key, String value) {
        this.variables.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 获取上下文变量
     *
     * @param key 键
     * @return 值
     */
    public String getVariable(String key) {
        this.lastAccessedAt = LocalDateTime.now();
        return this.variables.get(key);
    }

    /**
     * 检查上下文是否过期
     *
     * @return 是否过期
     */
    public boolean isExpired() {
        return this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt);
    }

    /**
     * 刷新最后访问时间
     */
    public void refreshAccess() {
        this.lastAccessedAt = LocalDateTime.now();
    }
}