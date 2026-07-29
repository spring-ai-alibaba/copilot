package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.service.FileSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 文件系统控制器
 * 提供工作空间文件管理API
 *
 * <p>多租户隔离：workspace 下按 conversationId 分目录，本控制器所有接口
 * 只允许访问归属当前登录用户的会话目录。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileSystemController {

    @Autowired
    private FileSystemService fileSystemService;

    @Autowired
    private ConversationService conversationService;

    private static final String WORKSPACE_PATH_ROOT = "workspace/";

    /** 当前用户拥有的会话 ID 集合 */
    private Set<String> ownedConversationIds() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new SecurityException("未登录");
        }
        return new HashSet<>(conversationService.listConversationIds(userId));
    }

    /**
     * 校验路径归属：解码后的 workspacePath 形如 "<conversationId>[/子路径]"
     * （兼容旧前端可能带的 "workspace/" 前缀），首段必须是当前用户的会话目录。
     * 返回规范化后（含 workspace/ 前缀）的实际路径。
     */
    private String requireOwnedPath(String decodedWorkspacePath) {
        String p = decodedWorkspacePath == null ? "" : decodedWorkspacePath.replace('\\', '/');
        if (p.startsWith(WORKSPACE_PATH_ROOT)) {
            p = p.substring(WORKSPACE_PATH_ROOT.length());
        }
        if (p.contains("..")) {
            throw new SecurityException("路径不允许包含 ..");
        }
        String first = p.contains("/") ? p.substring(0, p.indexOf('/')) : p;
        if (first.isBlank() || !ownedConversationIds().contains(first)) {
            throw new SecurityException("无权访问该工作目录: " + first);
        }
        return WORKSPACE_PATH_ROOT + p;
    }

    /**
     * 获取当前用户工作空间中的文件列表（仅本人会话目录）
     */
    @GetMapping("/workspace")
    public ResponseEntity<?> getDefaultWorkspaceFiles() {
        try {
            Map<String, String> files = new HashMap<>();
            for (String convId : ownedConversationIds()) {
                Map<String, String> convFiles = fileSystemService.getAllFiles(WORKSPACE_PATH_ROOT + convId);
                convFiles.forEach((path, content) -> files.put(convId + java.io.File.separator + path, content));
            }

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

//    /**
//     * 获取工作空间中的文件列表
//     */
//    @GetMapping("/workspace/{workspacePath:.*}")
//    public ResponseEntity<?> getWorkspaceFiles(@PathVariable String workspacePath) {
//        try {
//            // 解码路径参数
//            String decodedPath = workspacePath.replace("|", "/");
//            Map<String, String> files = fileSystemService.getAllFiles(WORKSPACE_PATH_ROOT + decodedPath);
//
//            return ResponseEntity.ok(Map.of(
//                "success", true,
//                "files", files,
//                "fileCount", files.size()
//            ));
//        } catch (Exception e) {
//            log.error("Error getting workspace files: {}", e.getMessage());
//            return ResponseEntity.badRequest().body(Map.of(
//                "success", false,
//                "error", e.getMessage()
//            ));
//        }
//    }

    /**
     * 读取工作空间中的特定文件
     */
    @GetMapping("/workspace/{workspacePath:.*}/file/{filePath:.*}")
    public ResponseEntity<?> readWorkspaceFile(
            @PathVariable String workspacePath,
            @PathVariable String filePath) {
        try {
            // 解码路径参数 + 归属校验
            String decodedWorkspacePath = requireOwnedPath(workspacePath.replace("|", "/"));
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
            // 解码路径参数 + 归属校验
            String decodedWorkspacePath = requireOwnedPath(workspacePath.replace("|", "/"));
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
            // 解码路径参数 + 归属校验
            String decodedPath = requireOwnedPath(workspacePath.replace("|", "/"));
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
            // 解码路径参数 + 归属校验
            String decodedWorkspacePath = requireOwnedPath(workspacePath.replace("|", "/"));

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