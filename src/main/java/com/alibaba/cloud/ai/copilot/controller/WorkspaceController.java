package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.dto.FileContentRequest;
import com.alibaba.cloud.ai.copilot.dto.FileInfo;
import com.alibaba.cloud.ai.copilot.dto.RenameRequest;
import com.alibaba.cloud.ai.copilot.service.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * 静态文件服务 - 用于预览HTML文件及其依赖的CSS/JS文件
     */
    @GetMapping("/static/**")
    public ResponseEntity<Resource> serveStaticFile(HttpServletRequest request) {
        try {
            // 从请求URI中提取文件路径
            String requestURI = request.getRequestURI();
            String filePath = requestURI.substring("/api/workspace/static/".length());

            // 获取工作目录的绝对路径
            Path workspacePath = workspaceService.getWorkspaceRoot();
            Path resolvedPath = workspacePath.resolve(filePath).normalize();

            // 安全检查：确保文件在工作目录内
            if (!resolvedPath.startsWith(workspacePath)) {
                return ResponseEntity.badRequest().build();
            }

            // 检查文件是否存在
            if (!Files.exists(resolvedPath) || Files.isDirectory(resolvedPath)) {
                return ResponseEntity.notFound().build();
            }

            // 创建资源
            Resource resource = new FileSystemResource(resolvedPath);

            // 确定内容类型
            String contentType = determineContentType(resolvedPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 确定文件的内容类型
     */
    private String determineContentType(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType != null) {
                return contentType;
            }
        } catch (IOException e) {
            // 忽略异常，使用默认逻辑
        }

        // 根据文件扩展名确定内容类型
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".html")) {
            return "text/html";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".json")) {
            return "application/json";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "application/octet-stream";
        }
    }
}
