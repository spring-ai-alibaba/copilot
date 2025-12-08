package com.alibaba.cloud.ai.copilot.memory.longterm;

/**
 * 记忆范围枚举
 *
 * @author better
 */
public enum MemoryScope {
    /**
     * 全局记忆（用户级）
     */
    GLOBAL,

    /**
     * 项目记忆（项目级）
     */
    PROJECT,

    /**
     * 目录记忆（目录级）
     */
    DIRECTORY
}

