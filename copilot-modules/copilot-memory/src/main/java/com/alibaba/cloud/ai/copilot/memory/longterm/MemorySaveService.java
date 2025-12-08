package com.alibaba.cloud.ai.copilot.memory.longterm;

import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆保存服务
 * 允许用户将重要信息保存到长期记忆
 *
 * @author better
 */
@Slf4j
@Service
public class MemorySaveService {

    private static final String MEMORY_FILENAME = "COPILOT_MEMORY.md";
    private final MemoryProperties memoryProperties;
    private final ProjectMemoryLoader projectMemoryLoader;

    public MemorySaveService(
            MemoryProperties memoryProperties,
            ProjectMemoryLoader projectMemoryLoader) {
        this.memoryProperties = memoryProperties;
        this.projectMemoryLoader = projectMemoryLoader;
    }

    /**
     * 保存记忆到指定文件
     *
     * @param content 要保存的内容
     * @param scope 记忆范围（全局/项目/目录）
     * @param section 章节名称（可选）
     * @param targetPath 目标路径（可选，用于项目/目录范围）
     */
    public void saveMemory(String content, MemoryScope scope, String section, Path targetPath) {
        Path memoryFile = resolveMemoryFile(scope, targetPath);

        try {
            // 1. 读取现有内容
            String existingContent = Files.exists(memoryFile)
                    ? Files.readString(memoryFile)
                    : "";

            // 2. 查找或创建章节
            String updatedContent;
            if (section != null && !section.isEmpty()) {
                updatedContent = updateOrCreateSection(existingContent, section, content);
            } else {
                // 如果没有指定章节，追加到末尾
                updatedContent = existingContent + "\n\n" + content + "\n";
            }

            // 3. 保存文件
            Files.createDirectories(memoryFile.getParent());
            Files.writeString(memoryFile, updatedContent);

            log.info("Memory saved to: {}", memoryFile);

        } catch (IOException e) {
            log.error("Failed to save memory", e);
            throw new RuntimeException("Failed to save memory", e);
        }
    }

    /**
     * 解析记忆文件路径
     */
    private Path resolveMemoryFile(MemoryScope scope, Path targetPath) {
        switch (scope) {
            case GLOBAL:
                String globalDir = memoryProperties.getLongTerm().getGlobalDirectory();
                return Paths.get(System.getProperty("user.home"), globalDir, MEMORY_FILENAME);

            case PROJECT:
                if (targetPath == null) {
                    throw new IllegalArgumentException("Project scope requires targetPath");
                }
                Path projectRoot = projectMemoryLoader.findProjectRoot(targetPath);
                return projectRoot.resolve(MEMORY_FILENAME);

            case DIRECTORY:
                if (targetPath == null) {
                    throw new IllegalArgumentException("Directory scope requires targetPath");
                }
                return targetPath.resolve(MEMORY_FILENAME);

            default:
                throw new IllegalArgumentException("Unknown memory scope: " + scope);
        }
    }

    /**
     * 更新或创建章节
     */
    private String updateOrCreateSection(String existingContent, String section, String newContent) {
        // 使用正则表达式查找章节
        String sectionHeader = "## " + section;
        Pattern pattern = Pattern.compile(
                "^## " + Pattern.quote(section) + "\\s*$",
                Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(existingContent);

        if (matcher.find()) {
            // 章节存在，替换内容
            int start = matcher.start();
            int end = findNextSectionStart(existingContent, start);

            return existingContent.substring(0, start) +
                    sectionHeader + "\n" + newContent + "\n" +
                    existingContent.substring(end);
        } else {
            // 章节不存在，追加到末尾
            return existingContent + "\n\n" + sectionHeader + "\n" + newContent + "\n";
        }
    }

    /**
     * 查找下一个章节的开始位置
     */
    private int findNextSectionStart(String content, int currentStart) {
        Pattern pattern = Pattern.compile("^## ", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            if (matcher.start() > currentStart) {
                return matcher.start();
            }
        }

        return content.length();
    }
}

