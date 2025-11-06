package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.FileSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件系统服务实现类
 */
@Slf4j
@Service
public class FileSystemServiceImpl implements FileSystemService {

    @Value("${app.workspace.root-directory:./workspace}")
    private String workspaceRoot;

    @Override
    public String createSessionWorkspace(String conversationId, String userId) {
        try {
            // 处理null值，使用默认值
            String safeUserId = (userId != null) ? userId : "anonymous";
            String safeConversationId = (conversationId != null) ? conversationId : "default";

            // Windows 文件名不允许的字符: \\ / : * ? " < > |
            // 为跨平台安全，统一将这些非法字符替换为下划线，并移除结尾的点/空格
            String userDir = sanitizePathSegment(safeUserId);
            String conversationDir = sanitizePathSegment(safeConversationId);

            // 创建工作目录路径: workspaceRoot/userId/conversationId
            String workspacePath = Paths.get(workspaceRoot, userDir, conversationDir).toString();
            File workspaceDir = new File(workspacePath);

            if (!workspaceDir.exists()) {
                boolean created = workspaceDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("Failed to create workspace directory: " + workspacePath);
                }
            }

            if (!safeUserId.equals(userDir) || !safeConversationId.equals(conversationDir)) {
                log.debug("Sanitized workspace path segments. userId='{}'->'{}', conversationId='{}'->'{}'",
                    safeUserId, userDir, safeConversationId, conversationDir);
            }
            log.info("Created workspace for conversation {} at: {}", safeConversationId, workspacePath);
            return workspacePath;
        } catch (Exception e) {
            log.error("Error creating workspace for conversation {}: {}", conversationId, e.getMessage());
            throw new RuntimeException("Failed to create workspace", e);
        }
    }

    /**
     * 将路径段标准化为跨平台安全的文件/目录名
     * 规则：
     * - 替换非法字符（\\ / : * ? " < > |）为下划线
     * - 将连续空白替换为单个下划线
     * - 去除结尾的点和空格（Windows 不允许）
     * - 为空时退化为 "unnamed"
     */
    private String sanitizePathSegment(String input) {
        String s = (input == null) ? "" : input;
        // 替换非法字符
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        // 折叠空白
        s = s.replaceAll("\\s+", "_");
        // 去除末尾的点和空格
        s = s.replaceAll("[. ]+$", "");
        // 避免空字符串
        if (s.isEmpty()) {
            s = "unnamed";
        }
        return s;
    }

    @Override
    public void saveFile(String workspacePath, String filePath, String content) {
        try {
            // 确保文件路径在工作目录内
            File workspaceDir = new File(workspacePath);
            File targetFile = new File(workspaceDir, filePath);

            // 安全检查：确保文件在workspace目录内
            if (!targetFile.getCanonicalPath().startsWith(workspaceDir.getCanonicalPath())) {
                throw new SecurityException("File path is outside workspace: " + filePath);
            }

            // 创建父目录
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    throw new RuntimeException("Failed to create parent directory: " + parentDir);
                }
            }

            // 写入文件内容
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
                writer.write(content);
            }

            log.debug("Saved file: {} to workspace: {}", filePath, workspacePath);
        } catch (Exception e) {
            log.error("Error saving file {} to workspace {}: {}", filePath, workspacePath, e.getMessage());
            throw new RuntimeException("Failed to save file", e);
        }
    }

    @Override
    public void saveFiles(String workspacePath, Map<String, String> files) {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            saveFile(workspacePath, entry.getKey(), entry.getValue());
        }
        log.info("Saved {} files to workspace: {}", files.size(), workspacePath);
    }

    @Override
    public String readFile(String workspacePath, String filePath) {
        try {
            File workspaceDir = new File(workspacePath);
            File targetFile = new File(workspaceDir, filePath);

            // 安全检查
            if (!targetFile.getCanonicalPath().startsWith(workspaceDir.getCanonicalPath())) {
                throw new SecurityException("File path is outside workspace: " + filePath);
            }

            if (!targetFile.exists()) {
                throw new FileNotFoundException("File not found: " + filePath);
            }

            return Files.readString(targetFile.toPath());
        } catch (Exception e) {
            log.error("Error reading file {} from workspace {}: {}", filePath, workspacePath, e.getMessage());
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public Map<String, String> getAllFiles(String workspacePath) {
        try {
            File workspaceDir = new File(workspacePath);
            if (!workspaceDir.exists()) {
                return Collections.emptyMap();
            }

            Map<String, String> files = new HashMap<>();

            // 递归获取所有文件
            try (var paths = Files.walk(workspaceDir.toPath())) {
                paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String relativePath = workspaceDir.toPath().relativize(path).toString();
                            String content = Files.readString(path);
                            files.put(relativePath, content);
                        } catch (IOException e) {
                            log.error("Error reading file {}: {}", path, e.getMessage());
                        }
                    });
            }

            return files;
        } catch (Exception e) {
            log.error("Error getting all files from workspace {}: {}", workspacePath, e.getMessage());
            throw new RuntimeException("Failed to get all files", e);
        }
    }

    @Override
    public void deleteWorkspace(String workspacePath) {
        try {
            File workspaceDir = new File(workspacePath);
            if (workspaceDir.exists()) {
                deleteDirectoryRecursively(workspaceDir);
                log.info("Deleted workspace: {}", workspacePath);
            }
        } catch (Exception e) {
            log.error("Error deleting workspace {}: {}", workspacePath, e.getMessage());
            throw new RuntimeException("Failed to delete workspace", e);
        }
    }

    @Override
    public WorkspaceInfo getWorkspaceInfo(String workspacePath) {
        try {
            File workspaceDir = new File(workspacePath);
            if (!workspaceDir.exists()) {
                return new WorkspaceInfo(workspacePath, Collections.emptyList(), 0, 0);
            }

            List<String> files = new ArrayList<>();
            long totalSize = 0;

            try (var paths = Files.walk(workspaceDir.toPath())) {
                files = paths.filter(Files::isRegularFile)
                    .map(path -> workspaceDir.toPath().relativize(path).toString())
                    .collect(Collectors.toList());

                totalSize = paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            }

            long createTime = workspaceDir.lastModified();

            return new WorkspaceInfo(workspacePath, files, totalSize, createTime);
        } catch (Exception e) {
            log.error("Error getting workspace info for {}: {}", workspacePath, e.getMessage());
            throw new RuntimeException("Failed to get workspace info", e);
        }
    }

    @Override
    public boolean isFileSystemEnabled() {
        try {
            // 检查工作空间根目录是否存在或可创建
            File rootDir = new File(workspaceRoot);
            if (!rootDir.exists()) {
                boolean created = rootDir.mkdirs();
                if (!created) {
                    log.warn("Failed to create workspace root directory: {}", workspaceRoot);
                    return false;
                }
            }
            return rootDir.exists() && rootDir.isDirectory() && rootDir.canWrite();
        } catch (Exception e) {
            log.error("Error checking file system status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectoryRecursively(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}