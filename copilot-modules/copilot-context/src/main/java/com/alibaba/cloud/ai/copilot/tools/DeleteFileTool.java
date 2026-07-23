package com.alibaba.cloud.ai.copilot.tools;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 删除文件工具（agentscope @Tool 形态）
 *
 * <p>由 {@code CopilotAgentFactory} 通过 {@code toolkit.registerTool(new DeleteFileTool(rootDirectory))}
 * 注册，补齐 HarnessAgent 自带 FilesystemTool 所缺的 delete 能力。仅能删除 workspace 内的文件，不能删目录。</p>
 */
public class DeleteFileTool {

    private static final Logger logger = LoggerFactory.getLogger(DeleteFileTool.class);

    /**
     * 工作区根目录，用于限制删除范围，防止越权删除。
     */
    private final Path baseDir;

    public DeleteFileTool(String baseDir) {
        this.baseDir = baseDir != null ? Paths.get(baseDir).toAbsolutePath().normalize() : null;
        if (this.baseDir != null) {
            logger.info("DeleteFileTool initialized with base directory: {}", this.baseDir);
        } else {
            logger.info("DeleteFileTool initialized without base directory restriction");
        }
    }

    /**
     * 删除指定文件。
     *
     * @param filePath 要删除的文件绝对路径，必须位于 workspace 内
     * @return 删除结果
     */
    @Tool(
            name = "delete_file",
            description =
                    "Deletes the specified file. Only works on files, not directories. The path"
                        + " must be absolute and within the workspace.")
    public Mono<ToolResultBlock> deleteFile(
            @ToolParam(
                            name = "file_path",
                            description =
                                    "The absolute path of the file to delete. Must be within the"
                                        + " workspace.")
                    String filePath) {
        return Mono.fromCallable(
                () -> {
                    if (filePath == null || filePath.trim().isEmpty()) {
                        return ToolResultBlock.error(
                                "InvalidArgumentsError: file_path is required");
                    }

                    Path target = Paths.get(filePath);
                    if (!target.isAbsolute()) {
                        return ToolResultBlock.error(
                                "InvalidArgumentsError: file_path must be absolute: " + filePath);
                    }
                    if (!isWithinWorkspace(target)) {
                        return ToolResultBlock.error(
                                "PermissionError: file_path must be within workspace ("
                                        + baseDir
                                        + ")");
                    }
                    if (!Files.exists(target)) {
                        return ToolResultBlock.error("NotFoundError: file not found: " + filePath);
                    }
                    if (Files.isDirectory(target)) {
                        return ToolResultBlock.error(
                                "InvalidArgumentsError: cannot delete directory, only files: "
                                        + filePath);
                    }

                    try {
                        Files.delete(target);
                        logger.info("Deleted file: {}", target);
                        return ToolResultBlock.text("Deleted: " + filePath);
                    } catch (IOException e) {
                        logger.error("Error deleting file: {}", filePath, e);
                        return ToolResultBlock.error("Error: " + e.getMessage());
                    }
                });
    }

    private boolean isWithinWorkspace(Path path) {
        if (baseDir == null) {
            return true;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            return normalized.startsWith(baseDir.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }
}
