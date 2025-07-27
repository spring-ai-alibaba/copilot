package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.dto.FileContentRequest;
import com.alibaba.cloud.ai.copilot.dto.FileInfo;
import com.alibaba.cloud.ai.copilot.dto.RenameRequest;
import com.alibaba.cloud.ai.copilot.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作目录文件管理控制器
 */
@RestController
@RequestMapping("/api/workspace")
@CrossOrigin(origins = "*") // 允许跨域请求
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 获取工作目录下的所有文件
     */
    @GetMapping("/files")
    public ResponseEntity<List<FileInfo>> getWorkspaceFiles() {
        try {
            List<FileInfo> files = workspaceService.getWorkspaceFiles();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 读取指定文件内容
     */
    @GetMapping("/files/content")
    public ResponseEntity<Map<String, String>> readFile(@RequestParam String path) {
        try {
            String content = workspaceService.readFile(path);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 写入文件内容
     */
    @PostMapping("/files/content")
    public ResponseEntity<Void> writeFile(@RequestBody FileContentRequest request) {
        try {
            workspaceService.writeFile(request.getPath(), request.getContent());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建新文件
     */
    @PostMapping("/files")
    public ResponseEntity<Void> createFile(@RequestBody FileContentRequest request) {
        try {
            workspaceService.createFile(request.getPath(), request.getContent());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/files")
    public ResponseEntity<Void> deleteFile(@RequestParam String path) {
        try {
            workspaceService.deleteFile(path);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建目录
     */
    @PostMapping("/directories")
    public ResponseEntity<Void> createDirectory(@RequestBody Map<String, String> request) {
        try {
            workspaceService.createDirectory(request.get("path"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 重命名文件或目录
     */
    @PutMapping("/files/rename")
    public ResponseEntity<Void> renameFile(@RequestBody RenameRequest request) {
        try {
            workspaceService.renameFile(request.getOldPath(), request.getNewPath());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
