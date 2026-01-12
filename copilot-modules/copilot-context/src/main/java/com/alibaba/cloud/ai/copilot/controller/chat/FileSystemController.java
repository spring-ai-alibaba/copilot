package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.service.FileSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件系统控制器
 * 提供工作空间文件管理API
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileSystemController {

    @Autowired
    private FileSystemService fileSystemService;

    /**
     * 获取工作空间中的文件列表
     */
    @GetMapping("/workspace/{workspacePath:.*}")
    public ResponseEntity<?> getWorkspaceFiles(@PathVariable String workspacePath) {
        try {
            // 解码路径参数
            String decodedPath = workspacePath.replace("|", "/");
            Map<String, String> files = fileSystemService.getAllFiles(decodedPath);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "files", files,
                "fileCount", files.size()
            ));
        } catch (Exception e) {
            log.error("Error getting workspace files: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 读取工作空间中的特定文件
     */
    @GetMapping("/workspace/{workspacePath:.*}/file/{filePath:.*}")
    public ResponseEntity<?> readWorkspaceFile(
            @PathVariable String workspacePath,
            @PathVariable String filePath) {
        try {
            // 解码路径参数
            String decodedWorkspacePath = workspacePath.replace("|", "/");
            String decodedFilePath = filePath.replace("|", "/");

            String content = fileSystemService.readFile(decodedWorkspacePath, decodedFilePath);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "content", content,
                "filePath", decodedFilePath
            ));
        } catch (Exception e) {
            log.error("Error reading workspace file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 保存文件到工作空间
     */
    @PostMapping("/workspace/{workspacePath:.*}/file/{filePath:.*}")
    public ResponseEntity<?> saveWorkspaceFile(
            @PathVariable String workspacePath,
            @PathVariable String filePath,
            @RequestBody Map<String, String> request) {
        try {
            // 解码路径参数
            String decodedWorkspacePath = workspacePath.replace("|", "/");
            String decodedFilePath = filePath.replace("|", "/");
            String content = request.get("content");

            if (content == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Content is required"
                ));
            }

            fileSystemService.saveFile(decodedWorkspacePath, decodedFilePath, content);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "File saved successfully",
                "filePath", decodedFilePath
            ));
        } catch (Exception e) {
            log.error("Error saving workspace file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取工作空间信息
     */
    @GetMapping("/workspace/{workspacePath:.*}/info")
    public ResponseEntity<?> getWorkspaceInfo(@PathVariable String workspacePath) {
        try {
            // 解码路径参数
            String decodedPath = workspacePath.replace("|", "/");
            FileSystemService.WorkspaceInfo info = fileSystemService.getWorkspaceInfo(decodedPath);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "workspaceInfo", info
            ));
        } catch (Exception e) {
            log.error("Error getting workspace info: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 批量保存文件到工作空间
     */
    @PostMapping("/workspace/{workspacePath:.*}/files")
    public ResponseEntity<?> saveWorkspaceFiles(
            @PathVariable String workspacePath,
            @RequestBody Map<String, String> files) {
        try {
            // 解码路径参数
            String decodedWorkspacePath = workspacePath.replace("|", "/");

            fileSystemService.saveFiles(decodedWorkspacePath, files);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Files saved successfully",
                "fileCount", files.size()
            ));
        } catch (Exception e) {
            log.error("Error saving workspace files: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 检查文件系统功能是否启用
     */
    @GetMapping("/config")
    public ResponseEntity<?> getFileSystemConfig() {
        try {
            // 检查文件系统服务是否可用
            boolean enabled = fileSystemService.isFileSystemEnabled();

            return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "success", true
            ));
        } catch (Exception e) {
            log.error("Error checking file system config: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "enabled", false,
                "success", true,
                "error", e.getMessage()
            ));
        }
    }
}