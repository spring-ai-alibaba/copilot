package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.dto.FileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作目录服务
 */
@Service
public class WorkspaceService {

    @Value("${app.workspace.root-directory}")
    private String workspaceDirectory;

    /**
     * 获取工作目录路径
     */
    private Path getWorkspacePath() {
        return Paths.get(workspaceDirectory).toAbsolutePath();
    }

    /**
     * 获取工作目录下的所有文件
     */
    public List<FileInfo> getWorkspaceFiles() throws IOException {
        List<FileInfo> files = new ArrayList<>();
        Path workspacePath = getWorkspacePath();
        
        System.out.println("工作目录路径: " + workspacePath);
        
        if (!Files.exists(workspacePath)) {
            System.out.println("工作目录不存在，创建目录: " + workspacePath);
            Files.createDirectories(workspacePath);
            return files;
        }

        Files.walkFileTree(workspacePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = workspacePath.relativize(file).toString().replace("\\", "/");
                files.add(new FileInfo(
                    file.getFileName().toString(),
                    relativePath,
                    "file",
                    attrs.size(),
                    LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
                ));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(workspacePath)) {
                    String relativePath = workspacePath.relativize(dir).toString().replace("\\", "/");
                    files.add(new FileInfo(
                        dir.getFileName().toString(),
                        relativePath,
                        "directory",
                        null,
                        LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
                    ));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("找到 " + files.size() + " 个文件/目录");
        return files;
    }

    /**
     * 读取文件内容
     */
    public String readFile(String relativePath) throws IOException {
        Path filePath = getWorkspacePath().resolve(relativePath);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new IOException("文件不存在或是目录: " + relativePath);
        }
        return Files.readString(filePath);
    }

    /**
     * 写入文件内容
     */
    public void writeFile(String relativePath, String content) throws IOException {
        Path filePath = getWorkspacePath().resolve(relativePath);
        
        // 确保父目录存在
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        Files.writeString(filePath, content);
    }

    /**
     * 创建文件
     */
    public void createFile(String relativePath, String content) throws IOException {
        Path filePath = getWorkspacePath().resolve(relativePath);
        
        if (Files.exists(filePath)) {
            throw new IOException("文件已存在: " + relativePath);
        }
        
        // 确保父目录存在
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        Files.writeString(filePath, content != null ? content : "");
    }

    /**
     * 删除文件或目录
     */
    public void deleteFile(String relativePath) throws IOException {
        Path filePath = getWorkspacePath().resolve(relativePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("文件不存在: " + relativePath);
        }
        
        if (Files.isDirectory(filePath)) {
            // 递归删除目录
            Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(filePath);
        }
    }

    /**
     * 创建目录
     */
    public void createDirectory(String relativePath) throws IOException {
        Path dirPath = getWorkspacePath().resolve(relativePath);
        Files.createDirectories(dirPath);
    }

    /**
     * 重命名文件或目录
     */
    public void renameFile(String oldRelativePath, String newRelativePath) throws IOException {
        Path oldPath = getWorkspacePath().resolve(oldRelativePath);
        Path newPath = getWorkspacePath().resolve(newRelativePath);
        
        if (!Files.exists(oldPath)) {
            throw new IOException("源文件不存在: " + oldRelativePath);
        }
        
        if (Files.exists(newPath)) {
            throw new IOException("目标文件已存在: " + newRelativePath);
        }
        
        // 确保目标目录存在
        Path parentDir = newPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        Files.move(oldPath, newPath);
    }
}
