package com.alibaba.cloud.ai.copilot.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * MCP 服务器列表响应实体
 *
 * @author Administrator
 */
@Data
public class McpServerListResponse {

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("servers")
    private List<McpServerInfo> servers;

    @Data
    public static class McpServerInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("icon")
        private String icon;

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("author")
        private String author;

        @JsonProperty("github_url")
        private String githubUrl;

        @JsonProperty("orderBy")
        private Integer orderBy;

        @JsonProperty("score")
        private String score;

        @JsonProperty("category")
        private CategoryInfo category;

        @Data
        public static class CategoryInfo {
            @JsonProperty("id")
            private String id;

            @JsonProperty("name")
            private String name;

            @JsonProperty("label")
            private String label;
        }
    }
}

