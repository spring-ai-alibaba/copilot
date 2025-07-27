package com.alibaba.cloud.ai.copilot.dto;

/**
 * 文件内容请求 DTO
 */
public class FileContentRequest {
    private String path;
    private String content;

    public FileContentRequest() {}

    public FileContentRequest(String path, String content) {
        this.path = path;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
