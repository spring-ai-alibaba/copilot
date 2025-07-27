package com.alibaba.cloud.ai.copilot.dto;

import java.time.LocalDateTime;

/**
 * 文件信息 DTO
 */
public class FileInfo {
    private String name;
    private String path;
    private String type; // "file" 或 "directory"
    private Long size;
    private LocalDateTime lastModified;

    public FileInfo() {}

    public FileInfo(String name, String path, String type, Long size, LocalDateTime lastModified) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.lastModified = lastModified;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
}
