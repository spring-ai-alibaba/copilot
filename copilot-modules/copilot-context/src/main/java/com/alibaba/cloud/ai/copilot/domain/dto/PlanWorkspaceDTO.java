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
        /** 面向审批者的一句话交付摘要，由 PLAN.md 自动提取。 */
        private String summary;
        /** 面向审批者的结构化变更步骤。 */
        private List<PlanChange> changes = new ArrayList<>();
        /** 明确不会涉及的范围。 */
        private List<String> scopeOut = new ArrayList<>();
        /** 可执行或可观察的验证项。 */
        private List<PlanVerification> verifications = new ArrayList<>();
        /** 需要用户关注的实际风险。 */
        private List<String> risks = new ArrayList<>();
        /** 阻塞审批前需要用户选择的问题。 */
        private List<PlanQuestion> questions = new ArrayList<>();
        /** CodeGraph 等代码理解器生成的可审查证据。 */
        private String evidenceStatus;
        private List<PlanEvidence> evidence = new ArrayList<>();
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
    public static class PlanChange {
        private String title;
        private List<String> files = new ArrayList<>();
        private String action;
        private String reason;
        private String impact;
        private List<String> acceptanceCriteria = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanVerification {
        private String type;
        private String description;
        private String command;
        private String expectedResult;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanQuestion {
        private String question;
        private boolean blocking;
        private String suggestedAnswer;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanEvidence {
        private String source;
        private String type;
        private String subject;
        private String summary;
        private List<String> relatedFiles = new ArrayList<>();
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
