package com.alibaba.cloud.ai.copilot.memory.longterm;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * 项目记忆加载器
 * 实现三阶段搜索策略：全局 → 向上 → 向下
 *
 * @author better
 */
@Slf4j
@Service
public class ProjectMemoryLoader {

    private static final String MEMORY_FILENAME = "COPILOT_MEMORY.md";
    private final MemoryProperties memoryProperties;

    public ProjectMemoryLoader(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /**
     * 加载项目记忆文件
     *
     * @param currentDir 当前目录
     * @param projectRoot 项目根目录
     * @return 记忆文件列表（按优先级排序）
     */
    public List<MemoryFile> loadProjectMemories(Path currentDir, Path projectRoot) {
        List<MemoryFile> allMemories = new ArrayList<>();

        // 阶段 1: 全局记忆
        Path globalMemory = getGlobalMemoryPath();
        if (Files.exists(globalMemory)) {
            allMemories.add(MemoryFile.builder()
                    .path(globalMemory)
                    .scope(MemoryScope.GLOBAL)
                    .build());
        }

        // 阶段 2: 向上搜索（从当前目录到项目根）
        List<Path> upwardPaths = searchUpward(currentDir, projectRoot);
        upwardPaths.forEach(p -> allMemories.add(MemoryFile.builder()
                .path(p)
                .scope(MemoryScope.PROJECT)
                .build()));

        // 阶段 3: 向下搜索（BFS）
        List<Path> downwardPaths = searchDownward(currentDir);
        downwardPaths.forEach(p -> allMemories.add(MemoryFile.builder()
                .path(p)
                .scope(MemoryScope.DIRECTORY)
                .build()));

        log.debug("Loaded {} memory files for directory: {}", allMemories.size(), currentDir);
        return allMemories;
    }

    /**
     * 获取全局记忆路径
     */
    private Path getGlobalMemoryPath() {
        String globalDir = memoryProperties.getLongTerm().getGlobalDirectory();
        return Paths.get(System.getProperty("user.home"), globalDir, MEMORY_FILENAME);
    }

    /**
     * 向上搜索：从当前目录向上到项目根
     */
    private List<Path> searchUpward(Path currentDir, Path projectRoot) {
        List<Path> found = new ArrayList<>();
        Path current = currentDir;

        // 从当前目录向上搜索，直到项目根
        while (current != null && (projectRoot == null || current.startsWith(projectRoot) || current.equals(projectRoot))) {
            Path memoryFile = current.resolve(MEMORY_FILENAME);
            if (Files.exists(memoryFile)) {
                found.add(memoryFile);
            }

            // 到达项目根就停止
            if (projectRoot != null && current.equals(projectRoot)) {
                break;
            }

            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }

        // 反转列表，使其按照 父目录 → 子目录 的顺序
        Collections.reverse(found);
        return found;
    }

    /**
     * 向下搜索：BFS 广度优先搜索子目录
     */
    private List<Path> searchDownward(Path startDir) {
        List<Path> found = new ArrayList<>();
        Queue<Path> queue = new LinkedList<>();
        queue.offer(startDir);

        int maxDirs = memoryProperties.getLongTerm().getMaxSearchDirs();
        int visitedDirs = 0;

        while (!queue.isEmpty() && visitedDirs < maxDirs) {
            Path currentDir = queue.poll();
            visitedDirs++;

            try (Stream<Path> paths = Files.list(currentDir)) {
                paths.forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            // 跳过不需要搜索的目录
                            if (!shouldSkipDirectory(path)) {
                                queue.offer(path);
                            }
                        } else if (path.getFileName().toString().equals(MEMORY_FILENAME)) {
                            found.add(path);
                        }
                    } catch (Exception e) {
                        log.warn("Error processing path: " + path, e);
                    }
                });
            } catch (IOException e) {
                log.warn("Error listing directory: " + currentDir, e);
            }
        }

        return found;
    }

    /**
     * 判断是否应该跳过目录
     */
    private boolean shouldSkipDirectory(Path dir) {
        String dirName = dir.getFileName().toString();
        List<String> ignorePatterns = memoryProperties.getLongTerm().getIgnorePatterns();

        // 检查是否匹配忽略模式
        for (String pattern : ignorePatterns) {
            if (dirName.equals(pattern) || dirName.startsWith(pattern)) {
                return true;
            }
        }

        // 跳过隐藏目录
        return dirName.startsWith(".");
    }

    /**
     * 查找项目根目录
     * 通过查找 .git 目录或其他项目标识来确定
     */
    public Path findProjectRoot(Path startDir) {
        Path current = startDir;

        while (current != null) {
            // 检查是否存在 .git 目录（Git 项目）
            Path gitDir = current.resolve(".git");
            if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                return current;
            }

            // 检查是否存在 pom.xml（Maven 项目）
            Path pomXml = current.resolve("pom.xml");
            if (Files.exists(pomXml)) {
                return current;
            }

            // 检查是否存在 package.json（Node.js 项目）
            Path packageJson = current.resolve("package.json");
            if (Files.exists(packageJson)) {
                return current;
            }

            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }

        // 如果找不到项目根，返回起始目录
        return startDir;
    }
}

