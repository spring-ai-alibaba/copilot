package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.schema.JsonSchema;
import com.alibaba.cloud.ai.copilot.service.ToolExecutionLogger;
import com.alibaba.cloud.ai.copilot.service.FileStreamManager;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 文件写入工具
 * 支持创建新文件或覆盖现有文件，自动显示差异
 */
@Component
public class WriteFileTool extends BaseTool<WriteFileTool.WriteFileParams> {

    private final String rootDirectory;
    private final AppProperties appProperties;

    @Autowired
    private ToolExecutionLogger executionLogger;

    @Autowired
    private FileStreamManager fileStreamManager;

    public WriteFileTool(AppProperties appProperties) {
        super(
            "write_file",
            "WriteFile",
            "Writes content to a file. Creates new files or overwrites existing ones. " +
            "Always shows a diff before writing. Automatically creates parent directories if needed. " +
            "Use absolute paths within the workspace directory.",
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
                "The content to write to the file. Will completely replace existing content if file exists."
            ))
            .required("file_path", "content");
    }

    @Override
    public String validateToolParams(WriteFileParams params) {
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

    @Override
    public CompletableFuture<ToolConfirmationDetails> shouldConfirmExecute(WriteFileParams params) {
        // 根据配置决定是否需要确认
        if (appProperties.getSecurity().getApprovalMode() == AppProperties.ApprovalMode.AUTO_EDIT ||
            appProperties.getSecurity().getApprovalMode() == AppProperties.ApprovalMode.YOLO) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = Paths.get(params.filePath);
                String currentContent = "";
                boolean isNewFile = !Files.exists(filePath);

                if (!isNewFile) {
                    currentContent = Files.readString(filePath, StandardCharsets.UTF_8);
                }

                // 生成差异显示
                String diff = generateDiff(
                    filePath.getFileName().toString(),
                    currentContent,
                    params.content
                );

                String title = isNewFile ?
                    "Confirm Create: " + getRelativePath(filePath) :
                    "Confirm Write: " + getRelativePath(filePath);

                return ToolConfirmationDetails.edit(title, filePath.getFileName().toString(), diff);

            } catch (IOException e) {
                logger.warn("Could not read existing file for diff: " + params.filePath, e);
                return null; // 如果无法读取文件，直接执行
            }
        });
    }

    // 原来的write_file工具已被删除，使用streaming_write_file代替

    @Override
    public CompletableFuture<ToolResult> execute(WriteFileParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = Paths.get(params.filePath);
                boolean isNewFile = !Files.exists(filePath);
                String originalContent = "";

                // 读取原始内容（用于备份和差异显示）
                if (!isNewFile) {
                    originalContent = Files.readString(filePath, StandardCharsets.UTF_8);
                }

                // 创建备份（如果启用）
                if (!isNewFile && shouldCreateBackup()) {
                    createBackup(filePath, originalContent);
                }

                // 确保父目录存在
                Files.createDirectories(filePath.getParent());

                // 写入文件
                Files.writeString(filePath, params.content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // 生成结果
                String relativePath = getRelativePath(filePath);
                long lineCount = params.content.lines().count();
                long byteCount = params.content.getBytes(StandardCharsets.UTF_8).length;

                if (isNewFile) {
                    String successMessage = String.format("Successfully created file: %s (%d lines, %d bytes)",
                        params.filePath, lineCount, byteCount);
                    String displayMessage = String.format("Created %s (%d lines)", relativePath, lineCount);
                    return ToolResult.success(successMessage, displayMessage);
                } else {
                    String diff = generateDiff(filePath.getFileName().toString(), originalContent, params.content);
                    String successMessage = String.format("Successfully wrote to file: %s (%d lines, %d bytes)",
                        params.filePath, lineCount, byteCount);
                    return ToolResult.success(successMessage, new FileDiff(diff, filePath.getFileName().toString()));
                }

            } catch (IOException e) {
                logger.error("Error writing file: " + params.filePath, e);
                return ToolResult.error("Error writing file: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error writing file: " + params.filePath, e);
                return ToolResult.error("Unexpected error: " + e.getMessage());
            }
        });
    }

    private String generateDiff(String fileName, String oldContent, String newContent) {
        try {
            List<String> oldLines = Arrays.asList(oldContent.split("\n"));
            List<String> newLines = Arrays.asList(newContent.split("\n"));

            Patch<String> patch = DiffUtils.diff(oldLines, newLines);
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                fileName + " (Original)",
                fileName + " (New)",
                oldLines,
                patch,
                3 // context lines
            );

            return String.join("\n", unifiedDiff);
        } catch (Exception e) {
            logger.warn("Could not generate diff", e);
            return "Diff generation failed: " + e.getMessage();
        }
    }

    private void createBackup(Path filePath, String content) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = filePath.getFileName().toString() + ".backup." + timestamp;
        Path backupPath = filePath.getParent().resolve(backupFileName);

        Files.writeString(backupPath, content, StandardCharsets.UTF_8);
        logger.info("Created backup: {}", backupPath);
    }

    private boolean shouldCreateBackup() {
        // 可以从配置中读取，这里简化为总是创建备份
        return true;
    }

    private boolean isWithinWorkspace(Path filePath) {
        try {
            Path workspaceRoot = Paths.get(rootDirectory).toRealPath();
            Path normalizedPath = filePath.normalize();
            return normalizedPath.startsWith(workspaceRoot.normalize());
        } catch (IOException e) {
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
     * 写入文件参数
     */
    public static class WriteFileParams {
        @JsonProperty("file_path")
        private String filePath;

        private String content;

        // 构造器
        public WriteFileParams() {}

        public WriteFileParams(String filePath, String content) {
            this.filePath = filePath;
            this.content = content;
        }

        // Getters and Setters
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        @Override
        public String toString() {
            return String.format("WriteFileParams{path='%s', contentLength=%d}",
                filePath, content != null ? content.length() : 0);
        }
    }

    /**
     * 开始流式文件写入
     * 先创建空文件，返回会话ID
     */
    public String startStreamingWrite(String taskId, String filePath, long estimatedTotalBytes) {
        try {
            // 验证参数
            WriteFileParams params = new WriteFileParams();
            params.setFilePath(filePath);
            params.setContent(""); // 空内容用于验证

            String validation = validateToolParams(params);
            if (validation != null) {
                throw new IllegalArgumentException("参数验证失败: " + validation);
            }

            // 开始流式写入
            return fileStreamManager.startStreamingWrite(taskId, filePath, estimatedTotalBytes);
        } catch (Exception e) {
            logger.error("开始流式文件写入失败: " + filePath, e);
            throw new RuntimeException("开始流式文件写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 写入内容块
     */
    public void writeContentChunk(String sessionId, String content) {
        try {
            fileStreamManager.writeContentChunk(sessionId, content);
        } catch (Exception e) {
            logger.error("写入内容块失败: sessionId=" + sessionId, e);
            fileStreamManager.handleWriteError(sessionId, e.getMessage());
            throw new RuntimeException("写入内容块失败: " + e.getMessage(), e);
        }
    }

    /**
     * 完成流式写入
     */
    public void completeStreamingWrite(String sessionId) {
        try {
            fileStreamManager.completeStreamingWrite(sessionId);
        } catch (Exception e) {
            logger.error("完成流式写入失败: sessionId=" + sessionId, e);
            fileStreamManager.handleWriteError(sessionId, e.getMessage());
            throw new RuntimeException("完成流式写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理流式写入错误
     */
    public void handleStreamingWriteError(String sessionId, String errorMessage) {
        fileStreamManager.handleWriteError(sessionId, errorMessage);
    }
}
