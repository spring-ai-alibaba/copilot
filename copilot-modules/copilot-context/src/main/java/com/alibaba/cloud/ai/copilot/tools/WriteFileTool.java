package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.mcp.BuiltinToolProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 文件写入工具
 * 支持创建新文件或覆盖现有文件，自动显示差异
 */
@Component
public class WriteFileTool
        implements BiFunction<WriteFileTool.WriteFileParams, ToolContext, String>, BuiltinToolProvider {

    private final String rootDirectory;
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public static final String DESCRIPTION = "Writes content to a file. Creates new files or overwrites existing ones. " +
            "Always shows a diff before writing. Automatically creates parent directories if needed. " +
            "Use absolute paths within the workspace directory.";

    public WriteFileTool() {
        this.rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
    }

    public static ToolCallback createWriteFileToolCallback(String description) {
        return FunctionToolCallback.builder("write_file", new WriteFileTool())
                .description(description)
                .inputType(WriteFileParams.class)
                .build();
    }

    @Override
    public String apply(
            @ToolParam(description = DESCRIPTION)
            WriteFileParams params,
            ToolContext toolContext) {
        try {
            // 验证参数
            String validationError = validateParams(params);
            if (validationError != null) {
                return "Error: " + validationError;
            }

            Path filePath = Paths.get(params.filePath);

            // 检查是否为目录
            if (Files.exists(filePath) && Files.isDirectory(filePath)) {
                return "Error: Path is a directory, not a file: " + params.filePath;
            }
            
            // 读取原始内容（用于显示差异）
            String originalContent = "";
            boolean isNewFile = !Files.exists(filePath);

            if (!isNewFile) {
                originalContent = Files.readString(filePath, StandardCharsets.UTF_8);
            }

            // 创建父目录
            Files.createDirectories(filePath.getParent());

            // 写入文件
            Files.writeString(filePath, params.content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 生成结果
            String relativePath = getRelativePath(filePath);
            long lineCount = params.content.lines().count();
            long byteCount = params.content.getBytes(StandardCharsets.UTF_8).length;

            if (isNewFile) {
                return String.format("Successfully created file: %s (%d lines, %d bytes)",
                        relativePath, lineCount, byteCount);
            } else {
                String diff = generateDiff(filePath.getFileName().toString(), originalContent, params.content);
                return String.format("Successfully wrote to file: %s (%d lines, %d bytes)\n\nDiff:\n%s",
                        relativePath, lineCount, byteCount, diff);
            }

        } catch (IOException e) {
            logger.error("Error writing file: {}", params.filePath, e);
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error writing file: {}", params.filePath, e);
            return "Error: Unexpected error: " + e.getMessage();
        }
    }

    private String validateParams(WriteFileParams params) {
        // 验证路径
        if (params.filePath == null || params.filePath.trim().isEmpty()) {
            return "File path cannot be empty";
        }

        // 验证内容
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

        // 验证内容大小（例如：10MB）
        byte[] contentBytes = params.content.getBytes(StandardCharsets.UTF_8);
        long maxFileSize = 10 * 1024 * 1024; // 10MB
        if (contentBytes.length > maxFileSize) {
            return "Content too large: " + contentBytes.length + " bytes. Maximum allowed: " + maxFileSize + " bytes";
        }

        return null;
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
        public String filePath;

        public String content;

    }

    // ==================== BuiltinToolProvider 接口实现 ====================

    @Override
    public String getToolName() {
        return "write_file";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return createWriteFileToolCallback(DESCRIPTION);
    }
}