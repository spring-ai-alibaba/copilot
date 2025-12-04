package com.alibaba.cloud.ai.copilot.memory.longterm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * 记忆文件模型
 *
 * @author better
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryFile {
    /**
     * 文件路径
     */
    private Path path;

    /**
     * 记忆范围
     */
    private MemoryScope scope;

    /**
     * 文件内容（可选，延迟加载）
     */
    private String content;
}

