package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 删除文件工具（agentscope @Tool）。
 *
 * <p>HarnessAgent 自带的 {@code FilesystemTool} 覆盖 read/write/edit/grep/glob/list，
 * 但不含 delete。这里补一个 delete_file，直接在 workspace 本地路径上操作
 *（带路径越界校验：禁止 {@code ..} 路径穿越、限定在 workspace 内）。</p>
 *
 * <p>工具返回字符串，agentscope 自动包成 ToolResult 事件（TOOL_RESULT_TEXT_DELTA /
 * TOOL_RESULT_END），前端可在 {@code TOOL_CALL_RESULT.content} 看到结果。</p>
 */
@Slf4j
public class DeleteFileTool {

    private final String workspaceRoot;

    public DeleteFileTool(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    @Tool(
            name = "delete_file",
            description = "删除 workspace 内指定路径的文件。path 为相对 workspace 根目录的路径。",
            readOnly = false
    )
    public String deleteFile(
            @ToolParam(name = "path", description = "要删除的文件相对路径，如 src/Old.java")
            String path
    ) {
        if (path == null || path.isBlank()) {
            return "错误：path 不能为空";
        }
        // 路径越界校验：禁止 .. 穿越
        if (path.contains("..")) {
            return "错误：path 不允许包含 .. （路径穿越禁止）";
        }

        try {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            Path target = root.resolve(path).normalize();
            // 必须落在 workspace 内
            if (!target.startsWith(root)) {
                return "错误：path 超出 workspace 范围";
            }
            if (!Files.exists(target)) {
                return "文件不存在: " + path;
            }
            Files.delete(target);
            log.info("删除文件: {}", path);
            return "已删除: " + path;
        } catch (Exception e) {
            log.error("删除文件失败: {}", path, e);
            return "删除失败: " + e.getMessage();
        }
    }
}
