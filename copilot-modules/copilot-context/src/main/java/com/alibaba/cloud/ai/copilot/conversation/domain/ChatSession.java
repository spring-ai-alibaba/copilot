package com.alibaba.cloud.ai.copilot.conversation.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话领域模型
 * 表示一个完整的对话会话
 *
 * @author Alibaba Cloud AI Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    /**
     * 会话唯一标识
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 对话模式
     */
    private ConversationMode mode;

    /**
     * 会话状态
     */
    private SessionStatus status;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 会话最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 会话配置
     */
    private SessionConfig config;

    /**
     * 会话元数据
     */
    private Map<String, Object> metadata;

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        ACTIVE("active", "活跃"),
        PAUSED("paused", "暂停"),
        ENDED("ended", "结束"),
        ERROR("error", "错误");

        private final String code;
        private final String name;

        SessionStatus(String code, String name) {
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
     * 会话配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionConfig {
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private Boolean enableTools;
        private List<String> allowedTools;
        private Map<String, Object> customSettings;
    }

    /**
     * 添加消息到会话
     *
     * @param message 消息
     */
    public void addMessage(Message message) {
        if (this.messages != null) {
            this.messages.add(message);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取最后一条消息
     *
     * @return 最后一条消息
     */
    public Message getLastMessage() {
        if (this.messages != null && !this.messages.isEmpty()) {
            return this.messages.get(this.messages.size() - 1);
        }
        return null;
    }

    /**
     * 获取消息总数
     *
     * @return 消息总数
     */
    public int getMessageCount() {
        return this.messages != null ? this.messages.size() : 0;
    }
}