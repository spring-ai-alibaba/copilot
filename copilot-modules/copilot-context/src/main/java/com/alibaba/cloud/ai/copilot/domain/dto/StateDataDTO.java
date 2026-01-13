package com.alibaba.cloud.ai.copilot.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 状态数据DTO类 - 只包含data部分
 * 用于接收output.state().data()的数据结构
 */
@Data
public class StateDataDTO {

    @JsonProperty("input")
    private String input;

    @JsonProperty("messages")
    private List<MessageDTO> messages;

    /**
     * Message DTO类 - 支持不同类型的消息
     */
    @Data
    public static class MessageDTO {
        @JsonProperty("messageType")
        private String messageType;

        @JsonProperty("metadata")
        private MetadataDTO metadata;

        @JsonProperty("media")
        private List<Object> media;

        @JsonProperty("text")
        private String text;

        @JsonProperty("toolCalls")
        private List<ToolCallDTO> toolCalls;

        @JsonProperty("responses")
        private List<ToolResponseDTO> responses;
    }

    /**
     * Metadata DTO类
     */
    @Data
    public static class MetadataDTO {
        @JsonProperty("messageType")
        private String messageType;

        @JsonProperty("role")
        private String role;

        @JsonProperty("refusal")
        private String refusal;

        @JsonProperty("finishReason")
        private String finishReason;

        @JsonProperty("annotations")
        private List<Object> annotations;

        @JsonProperty("index")
        private Integer index;

        @JsonProperty("id")
        private String id;

        @JsonProperty("reasoningContent")
        private String reasoningContent;
    }

    /**
     * ToolCall DTO类
     */
    @Getter
    @Setter
    @ToString
    public static class ToolCallDTO {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("name")
        private String name;

        private ToolArgumentsDTO arguments;

        @JsonProperty("arguments")
        public void setArguments(Object arguments) {
            if (arguments instanceof String) {
                // 如果是字符串，解析为 JSON 对象
                try {
                    this.arguments = objectMapper.readValue((String) arguments, ToolArgumentsDTO.class);
                } catch (JsonProcessingException e) {
                    System.err.println("Failed to parse arguments JSON: " + e.getMessage());
                    this.arguments = null;
                }
            } else if (arguments instanceof ToolArgumentsDTO) {
                // 如果已经是 DTO 对象，直接赋值
                this.arguments = (ToolArgumentsDTO) arguments;
            } else {
                // 其他情况，尝试转换
                try {
                    this.arguments = objectMapper.convertValue(arguments, ToolArgumentsDTO.class);
                } catch (Exception e) {
                    System.err.println("Failed to convert arguments: " + e.getMessage());
                    this.arguments = null;
                }
            }
        }
    }

    /**
     * ToolArguments DTO类 - 工具参数
     */
    @Data
    public static class ToolArgumentsDTO {
        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("content")
        private String content;

        @Override
        public String toString() {
            return "ToolArgumentsDTO{" +
                    "filePath='" + filePath + '\'' +
                    ", content='" + (content != null ? content.length() + " chars" : "null") + '\'' +
                    '}';
        }
    }

    /**
     * ToolResponse DTO类 - 工具响应数据
     */
    @Data
    public static class ToolResponseDTO {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("responseData")
        private String responseData;
    }
}

