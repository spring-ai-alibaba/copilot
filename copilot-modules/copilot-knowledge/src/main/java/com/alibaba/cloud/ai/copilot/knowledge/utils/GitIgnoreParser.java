package com.alibaba.cloud.ai.copilot.knowledge.utils;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.ignore.IgnoreNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * .gitignore 解析器
 * 基于 JGit 实现，用于检查文件是否被忽略
 *
 * @author RobustH
 */
@Slf4j
public class GitIgnoreParser {

    private final IgnoreNode ignoreNode;
    private final Path rootPath;

    public GitIgnoreParser(Path rootPath) {
        this.rootPath = rootPath;
        this.ignoreNode = new IgnoreNode();
        loadGitIgnore();
    }

    private void loadGitIgnore() {
        File gitIgnoreFile = rootPath.resolve(".gitignore").toFile();
        if (gitIgnoreFile.exists()) {
            try (FileInputStream fis = new FileInputStream(gitIgnoreFile)) {
                ignoreNode.parse(fis);
                log.info("Loaded .gitignore rules from {}", gitIgnoreFile.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to load .gitignore file: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查文件是否被忽略
     *
     * @param path 文件绝对路径
     * @param isDirectory 是否为目录
     * @return true if ignored
     */
    public boolean isIgnored(Path path, boolean isDirectory) {
        // 计算相对路径
        String relativePath = rootPath.relativize(path).toString().replace(File.separatorChar, '/');
        
        // JGit IgnoreNode 需要相对路径
        // checkIgnored 返回:
        // Ignored -> explicitly ignored
        // NotIgnored -> explicitly included (Negation rule)
        // CHECK_PARENT -> check parent directory
        Boolean result = ignoreNode.checkIgnored(relativePath, isDirectory);
        
        // 如果 result 为 null, 表示没有匹配的规则，默认不忽略
        return result != null && result == Boolean.TRUE;
    }
}
