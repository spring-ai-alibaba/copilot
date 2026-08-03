package com.alibaba.cloud.ai.copilot.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面级计划与执行工作区快照。
 */
@Data
public class PlanWorkspaceDTO {

    private String conversationId;
    private String status = "IDLE";
    private String message;
    private boolean decisionAllowed;
    private PlanReview review;
    private List<PlanTask> tasks = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanReview {
        private String reviewId;
        private String planFile;
        private String planContent;
        private String riskLevel;
        private List<String> affectedFiles = new ArrayList<>();
        private List<FilePreview> filePreviews = new ArrayList<>();
        private String gitStatus;
        private String permissionMode;
        private String executionPolicy;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilePreview {
        private String path;
        private int startLine;
        private int endLine;
        private String content;
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanTask {
        private String id;
        private String content;
        private String status;
        private String priority;
        private String owner;
        private List<String> blockedBy = new ArrayList<>();
    }
}
