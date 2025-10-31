package com.alibaba.cloud.ai.copilot.rag.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG处理服务接口
 * 整合文档解析、切割、向量化、存储的完整流程
 */
public interface RagProcessingService {

    /**
     * 处理单个文件的完整RAG流程
     * @param kbKey 知识库键
     * @param file 上传的文件
     * @param uploadedBy 上传者
     * @return 处理结果信息
     */
    ProcessingResult processFile(String kbKey, MultipartFile file, String uploadedBy);

    /**
     * 批量处理文件的完整RAG流程
     * @param kbKey 知识库键
     * @param files 上传的文件列表
     * @param uploadedBy 上传者
     * @return 批量处理结果信息
     */
    BatchProcessingResult processFiles(String kbKey, List<MultipartFile> files, String uploadedBy);

    /**
     * 处理文本内容的完整RAG流程
     * @param kbKey 知识库键
     * @param content 文本内容
     * @param title 内容标题
     * @param createdBy 创建者
     * @return 处理结果信息
     */
    ProcessingResult processTextContent(String kbKey, String content, String title, String createdBy);

    /**
     * 异步处理文件
     * @param kbKey 知识库键
     * @param file 上传的文件
     * @param uploadedBy 上传者
     * @return 任务ID
     */
    String processFileAsync(String kbKey, MultipartFile file, String uploadedBy);

    /**
     * 获取处理任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    ProcessingTaskStatus getTaskStatus(String taskId);

    /**
     * 处理结果
     */
    class ProcessingResult {
        private boolean success;
        private String message;
        private String fileName;
        private int chunkCount;
        private long processingTimeMs;
        private String errorMessage;

        // 构造函数和getter/setter
        public ProcessingResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ProcessingResult success(String fileName, int chunkCount, long processingTimeMs) {
            ProcessingResult result = new ProcessingResult(true, "处理成功");
            result.fileName = fileName;
            result.chunkCount = chunkCount;
            result.processingTimeMs = processingTimeMs;
            return result;
        }

        public static ProcessingResult failure(String fileName, String errorMessage) {
            ProcessingResult result = new ProcessingResult(false, "处理失败");
            result.fileName = fileName;
            result.errorMessage = errorMessage;
            return result;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    /**
     * 批量处理结果
     */
    class BatchProcessingResult {
        private int totalFiles;
        private int successCount;
        private int failureCount;
        private List<ProcessingResult> results;
        private long totalProcessingTimeMs;

        public BatchProcessingResult(int totalFiles, List<ProcessingResult> results) {
            this.totalFiles = totalFiles;
            this.results = results;
            this.successCount = (int) results.stream().filter(ProcessingResult::isSuccess).count();
            this.failureCount = totalFiles - successCount;
            this.totalProcessingTimeMs = results.stream().mapToLong(ProcessingResult::getProcessingTimeMs).sum();
        }

        // Getters and Setters
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
        public List<ProcessingResult> getResults() { return results; }
        public void setResults(List<ProcessingResult> results) { this.results = results; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
        public void setTotalProcessingTimeMs(long totalProcessingTimeMs) { this.totalProcessingTimeMs = totalProcessingTimeMs; }
    }

    /**
     * 处理任务状态
     */
    enum ProcessingTaskStatus {
        PENDING("待处理"),
        PROCESSING("处理中"),
        COMPLETED("已完成"),
        FAILED("处理失败"),
        CANCELLED("已取消");

        private final String description;

        ProcessingTaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
