package com.alibaba.cloud.ai.copilot.service;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 文件流式写入事件类
 * 继承自LogEvent，添加文件流式写入相关的字段
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileStreamEvent extends LogEvent {

    private String filePath;
    private String icon;
    private String status; // CREATED, WRITING, COMPLETE, ERROR
    private Long executionTime; // 执行时间(毫秒)
    private Integer chunkIndex; // 内容块索引
    private Long totalBytes; // 总字节数
    private Long writtenBytes; // 已写入字节数
    private Double progressPercent; // 进度百分比
    private String contentChunk; // 内容块（仅在FILE_CONTENT_CHUNK事件中使用）

    // Constructors
    public FileStreamEvent() {
        super();
    }

    public FileStreamEvent(String type, String taskId, String filePath, String message, String timestamp, String icon, String status) {
        super(type, taskId, message, timestamp);
        this.filePath = filePath;
        this.icon = icon;
        this.status = status;
    }

    // Getters and Setters
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Long getWrittenBytes() {
        return writtenBytes;
    }

    public void setWrittenBytes(Long writtenBytes) {
        this.writtenBytes = writtenBytes;
    }

    public Double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getContentChunk() {
        return contentChunk;
    }

    public void setContentChunk(String contentChunk) {
        this.contentChunk = contentChunk;
    }

    @Override
    public String toString() {
        return "FileStreamEvent{" +
                "filePath='" + filePath + '\'' +
                ", icon='" + icon + '\'' +
                ", status='" + status + '\'' +
                ", executionTime=" + executionTime +
                ", chunkIndex=" + chunkIndex +
                ", totalBytes=" + totalBytes +
                ", writtenBytes=" + writtenBytes +
                ", progressPercent=" + progressPercent +
                ", contentChunk='" + (contentChunk != null ? contentChunk.substring(0, Math.min(50, contentChunk.length())) + "..." : null) + '\'' +
                "} " + super.toString();
    }
}
