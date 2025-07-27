package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.config.TaskContextHolder;
import com.alibaba.cloud.ai.copilot.schema.JsonSchema;
import com.alibaba.cloud.ai.copilot.service.FileStreamManager;
import com.alibaba.cloud.ai.copilot.service.ToolExecutionLogger;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 流式文件写入工具
 * 支持先创建文件，然后流式写入内容
 */
@Component
public class StreamingWriteFileTool extends BaseTool<StreamingWriteFileTool.StreamingWriteParams> {

    private final String rootDirectory;
    private final AppProperties appProperties;

    @Autowired
    private ToolExecutionLogger executionLogger;

    @Autowired
    private FileStreamManager fileStreamManager;

    public StreamingWriteFileTool(AppProperties appProperties) {
        super(
            "streaming_write_file",
            "StreamingWriteFile",
            "Creates a file immediately and then streams content to it in real-time. " +
            "Use this when you want to show file creation progress to the user. " +
            "The file is created empty first, then content is written incrementally.",
            createSchema()
        );
        this.appProperties = appProperties;
        this.rootDirectory = appProperties.getWorkspace().getRootDirectory();
    }

    private static String getWorkspaceBasePath() {
        return Paths.get(System.getProperty("user.dir"), "workspace").toString();
    }

    private static String getPathExample(String subPath) {
        return "Example: \"" + Paths.get(getWorkspaceBasePath(), subPath).toString() + "\"";
    }

    private static JsonSchema createSchema() {
        return JsonSchema.object()
            .addProperty("file_path", JsonSchema.string(
                "MUST be an absolute path to the file to write to. Path must be within the workspace directory (" +
                getWorkspaceBasePath() + "). " +
                getPathExample("project/src/main.java") + ". " +
                "Relative paths are NOT allowed."
            ))
            .addProperty("content", JsonSchema.string(
                "The content to write to the file. This will be written incrementally to show progress."
            ))
            .addProperty("estimated_size", JsonSchema.number(
                "Optional estimated size of the content in bytes for progress tracking."
            ))
            .required("file_path", "content");
    }

    @Override
    public String validateToolParams(StreamingWriteParams params) {
        String baseValidation = super.validateToolParams(params);
        if (baseValidation != null) {
            return baseValidation;
        }

        // 验证路径
        if (params.filePath == null || params.filePath.trim().isEmpty()) {
            return "File path cannot be empty";
        }

        if (params.content == null) {
            return "Content cannot be null";
        }

        Path filePath = Paths.get(params.filePath);

        // 验证是否为绝对路径
        if (!filePath.isAbsolute()) {
            return "File path must be absolute: " + params.filePath;
        }

        // 验证是否在工作目录内
        if (!isWithinWorkspace(filePath)) {
            return "File path must be within the workspace directory (" + rootDirectory + "): " + params.filePath;
        }

        // 验证文件扩展名
        String fileName = filePath.getFileName().toString();
        if (!isAllowedFileType(fileName)) {
            return "File type not allowed: " + fileName +
                ". Allowed extensions: " + appProperties.getWorkspace().getAllowedExtensions();
        }

        // 验证内容大小
        byte[] contentBytes = params.content.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > appProperties.getWorkspace().getMaxFileSize()) {
            return "Content too large: " + contentBytes.length + " bytes. Maximum allowed: " +
                appProperties.getWorkspace().getMaxFileSize() + " bytes";
        }

        return null;
    }

    /**
     * Streaming write file tool method for Spring AI integration
     */
    @Tool(name = "streaming_write_file", description = "Creates a file immediately and streams content to it incrementally for real-time progress")
    public String streamingWriteFile(String filePath, String content, Long estimatedSize) {
        String taskId = TaskContextHolder.getCurrentTaskId();
        if (taskId == null) {
            return "Error: No active task context for streaming write";
        }

        long callId = executionLogger.logToolStart("streaming_write_file", "流式写入文件内容",
            String.format("文件路径=%s, 内容长度=%d字符", filePath, content != null ? content.length() : 0));
        long startTime = System.currentTimeMillis();

        try {
            StreamingWriteParams params = new StreamingWriteParams();
            params.setFilePath(filePath);
            params.setContent(content);
            params.setEstimatedSize(estimatedSize != null ? estimatedSize : content.getBytes(StandardCharsets.UTF_8).length);

            executionLogger.logToolStep(callId, "streaming_write_file", "参数验证", "验证文件路径和内容");

            // Validate parameters
            String validation = validateToolParams(params);
            if (validation != null) {
                long executionTime = System.currentTimeMillis() - startTime;
                executionLogger.logToolError(callId, "streaming_write_file", "参数验证失败: " + validation, executionTime);
                return "Error: " + validation;
            }

            executionLogger.logFileOperation(callId, "流式写入文件", filePath,
                String.format("内容长度: %d字符", content != null ? content.length() : 0));

            // Execute the streaming write
            ToolResult result = execute(params).join();

            long executionTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                executionLogger.logToolSuccess(callId, "streaming_write_file", "流式文件写入成功", executionTime);
                return result.getLlmContent();
            } else {
                executionLogger.logToolError(callId, "streaming_write_file", result.getErrorMessage(), executionTime);
                return "Error: " + result.getErrorMessage();
            }

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            executionLogger.logToolError(callId, "streaming_write_file", "工具执行异常: " + e.getMessage(), executionTime);
            logger.error("Error in streaming write file tool", e);
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public CompletableFuture<ToolResult> execute(StreamingWriteParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = TaskContextHolder.getCurrentTaskId();
            if (taskId == null) {
                return ToolResult.error("No active task context for streaming write");
            }

            try {
                // 1. 开始流式写入（创建空文件）
                String sessionId = fileStreamManager.startStreamingWrite(taskId, params.filePath, params.estimatedSize);

                // 2. 分块写入内容
                String content = params.content;
                int chunkSize = 1024; // 1KB chunks
                int totalLength = content.length();
                
                for (int i = 0; i < totalLength; i += chunkSize) {
                    int endIndex = Math.min(i + chunkSize, totalLength);
                    String chunk = content.substring(i, endIndex);
                    
                    fileStreamManager.writeContentChunk(sessionId, chunk);
                    
                    // 添加小延迟以模拟真实的流式写入
                    try {
                        Thread.sleep(50); // 50ms delay between chunks
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 3. 完成流式写入
                fileStreamManager.completeStreamingWrite(sessionId);

                // 生成结果
                Path filePath = Paths.get(params.filePath);
                String relativePath = getRelativePath(filePath);
                long lineCount = params.content.lines().count();
                long byteCount = params.content.getBytes(StandardCharsets.UTF_8).length;

                String successMessage = String.format("Successfully streamed content to file: %s (%d lines, %d bytes)",
                    params.filePath, lineCount, byteCount);
                String displayMessage = String.format("Streamed to %s (%d lines)", relativePath, lineCount);
                
                return ToolResult.success(successMessage, displayMessage);

            } catch (Exception e) {
                logger.error("Error in streaming write: " + params.filePath, e);
                return ToolResult.error("Error in streaming write: " + e.getMessage());
            }
        });
    }

    private boolean isWithinWorkspace(Path filePath) {
        try {
            Path workspaceRoot = Paths.get(rootDirectory).toRealPath();
            Path normalizedPath = filePath.normalize();
            return normalizedPath.startsWith(workspaceRoot.normalize());
        } catch (Exception e) {
            logger.warn("Could not resolve workspace path", e);
            return false;
        }
    }

    private boolean isAllowedFileType(String fileName) {
        List<String> allowedExtensions = appProperties.getWorkspace().getAllowedExtensions();
        return allowedExtensions.stream()
            .anyMatch(ext -> fileName.toLowerCase().endsWith(ext.toLowerCase()));
    }

    private String getRelativePath(Path filePath) {
        try {
            Path workspaceRoot = Paths.get(rootDirectory);
            return workspaceRoot.relativize(filePath).toString();
        } catch (Exception e) {
            return filePath.toString();
        }
    }

    /**
     * 流式写入文件参数
     */
    public static class StreamingWriteParams {
        @JsonProperty("file_path")
        private String filePath;

        private String content;

        @JsonProperty("estimated_size")
        private Long estimatedSize;

        // 构造器
        public StreamingWriteParams() {}

        public StreamingWriteParams(String filePath, String content, Long estimatedSize) {
            this.filePath = filePath;
            this.content = content;
            this.estimatedSize = estimatedSize;
        }

        // Getters and Setters
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Long getEstimatedSize() { return estimatedSize; }
        public void setEstimatedSize(Long estimatedSize) { this.estimatedSize = estimatedSize; }

        @Override
        public String toString() {
            return String.format("StreamingWriteParams{path='%s', contentLength=%d, estimatedSize=%d}",
                filePath, content != null ? content.length() : 0, estimatedSize != null ? estimatedSize : 0);
        }
    }
}
