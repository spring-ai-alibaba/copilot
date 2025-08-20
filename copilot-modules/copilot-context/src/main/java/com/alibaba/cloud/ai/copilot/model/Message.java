package com.alibaba.cloud.ai.copilot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Message model representing a chat message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    private String id;
    
    private String role;
    
    private String content;
    
    @JsonProperty("experimental_attachments")
    private List<Attachment> experimentalAttachments;
    
    private List<Object> parts;

    /**
     * Attachment model for message attachments
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String name;
        private String contentType;
        private String url;
    }
}
