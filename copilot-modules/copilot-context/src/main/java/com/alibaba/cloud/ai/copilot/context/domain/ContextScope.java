package com.alibaba.cloud.ai.copilot.context.domain;

/**
 * 上下文范围枚举
 * 定义上下文的作用域和生命周期
 *
 * @author Alibaba Cloud AI Team
 */
public enum ContextScope {

    /**
     * 会话级别 - 仅在当前会话中有效
     */
    SESSION("session", "会话级别", "仅在当前会话中有效"),

    /**
     * 用户级别 - 在用户的所有会话中有效
     */
    USER("user", "用户级别", "在用户的所有会话中有效"),

    /**
     * 全局级别 - 在所有用户和会话中有效
     */
    GLOBAL("global", "全局级别", "在所有用户和会话中有效"),

    /**
     * 临时级别 - 短期有效，会自动过期
     */
    TEMPORARY("temporary", "临时级别", "短期有效，会自动过期"),

    /**
     * 项目级别 - 在特定项目范围内有效
     */
    PROJECT("project", "项目级别", "在特定项目范围内有效");

    private final String code;
    private final String name;
    private final String description;

    ContextScope(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取上下文范围
     *
     * @param code 范围代码
     * @return 上下文范围
     */
    public static ContextScope fromCode(String code) {
        for (ContextScope scope : values()) {
            if (scope.code.equals(code)) {
                return scope;
            }
        }
        return SESSION; // 默认返回会话级别
    }

    /**
     * 检查是否为持久化范围
     *
     * @return 是否持久化
     */
    public boolean isPersistent() {
        return this == USER || this == GLOBAL || this == PROJECT;
    }

    /**
     * 检查是否为临时范围
     *
     * @return 是否临时
     */
    public boolean isTemporary() {
        return this == TEMPORARY || this == SESSION;
    }
}