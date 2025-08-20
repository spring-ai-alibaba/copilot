package com.alibaba.cloud.ai.copilot.conversation.domain;

/**
 * 对话模式枚举
 * 定义不同的对话交互模式
 *
 * @author Alibaba Cloud AI Team
 */
public enum ConversationMode {

    /**
     * 聊天模式 - 普通对话交互
     */
    CHAT("chat", "聊天模式", "普通的AI对话交互模式"),

    /**
     * 构建模式 - 代码生成和项目构建
     */
    BUILDER("builder", "构建模式", "用于代码生成和项目构建的模式"),

    /**
     * 分析模式 - 代码分析和审查
     */
    ANALYSIS("analysis", "分析模式", "用于代码分析和审查的模式"),

    /**
     * 调试模式 - 问题诊断和调试
     */
    DEBUG("debug", "调试模式", "用于问题诊断和调试的模式");

    private final String code;
    private final String name;
    private final String description;

    ConversationMode(String code, String name, String description) {
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
     * 根据代码获取对话模式
     *
     * @param code 模式代码
     * @return 对话模式
     */
    public static ConversationMode fromCode(String code) {
        for (ConversationMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return CHAT; // 默认返回聊天模式
    }
}