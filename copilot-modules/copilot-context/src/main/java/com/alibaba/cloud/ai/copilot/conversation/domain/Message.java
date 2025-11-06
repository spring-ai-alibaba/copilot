package com.alibaba.cloud.ai.copilot.conversation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息领域模型
 * 表示对话中的一条消息
 *
 * @author Alibaba Cloud AI Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * 消息唯一标识
     */
    private String id;

    /**
     * 消息角色：user, assistant, system, tool
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息创建时间
     */
    private LocalDateTime timestamp;

    /**
     * 消息附件
     */
    @JsonProperty("experimental_attachments")
    private List<Attachment> experimentalAttachments;

    /**
     * 消息部分（用于多模态内容）
     */
    private List<Object> parts;

    /**
     * 消息元数据
     */
    private MessageMetadata metadata;

    /**
     * 消息附件模型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String name;
        private String contentType;
        private String url;
        private Long size;
    }

    /**
     * 消息元数据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageMetadata {
        private String model;
        private Integer tokenCount;
        private Double confidence;
        private String source;
    }
}