package com.alibaba.cloud.ai.copilot.memory.longterm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 记忆内容加载器
 * 按优先级加载并合并记忆内容
 *
 * @author better
 */
@Slf4j
@Service
public class MemoryContentLoader {

    private final ProjectMemoryLoader projectMemoryLoader;

    public MemoryContentLoader(ProjectMemoryLoader projectMemoryLoader) {
        this.projectMemoryLoader = projectMemoryLoader;
    }

    /**
     * 加载并合并记忆内容
     *
     * @param currentDir 当前目录
     * @param projectRoot 项目根目录
     * @return 合并后的记忆内容
     */
    public String loadAndMergeMemories(Path currentDir, Path projectRoot) {
        // 1. 加载所有记忆文件
        List<MemoryFile> memories = projectMemoryLoader.loadProjectMemories(currentDir, projectRoot);

        if (memories.isEmpty()) {
            return "";
        }

        // 2. 读取并格式化内容
        StringBuilder mergedContent = new StringBuilder();

        for (MemoryFile memory : memories) {
            try {
                String content = Files.readString(memory.getPath());

                // 格式化记忆内容
                mergedContent.append("\n")
                        .append("--- Context from: ")
                        .append(memory.getPath())
                        .append(" ---\n")
                        .append(content)
                        .append("\n--- End of Context from: ")
                        .append(memory.getPath())
                        .append(" ---\n");

            } catch (IOException e) {
                log.warn("Failed to read memory file: " + memory.getPath(), e);
            }
        }

        return mergedContent.toString();
    }
}

