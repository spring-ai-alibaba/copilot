package com.alibaba.cloud.ai.copilot.config;

import com.alibaba.cloud.ai.copilot.tools.ContinuousTaskTool;
import com.alibaba.cloud.ai.copilot.tools.ContinuousTaskTool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务工具配置类
 * 提供任务持久化、依赖管理、调度等组件的配置
 */
@Configuration
public class TaskToolConfiguration {

    @Value("${copilot.task.storage.path:./task-storage}")
    private String taskStoragePath;

    @Value("${copilot.task.storage.type:memory}")
    private String taskStorageType;

    /**
     * 任务持久化管理器
     * 根据配置选择内存或文件系统存储
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskPersistenceManager taskPersistenceManager() {
        if ("filesystem".equalsIgnoreCase(taskStorageType)) {
            return new FileSystemTaskPersistenceManager(taskStoragePath);
        } else {
            return new InMemoryTaskPersistenceManager();
        }
    }

    /**
     * 任务依赖管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskDependencyManager taskDependencyManager() {
        return new SimpleDependencyManager();
    }

    /**
     * 任务调度器
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskScheduler taskScheduler() {
        return new PriorityTaskScheduler();
    }

    /**
     * 任务事件监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskEventListener taskEventListener() {
        return new DefaultTaskEventListener();
    }

    /**
     * 连续任务执行工具
     */
    @Bean
    @ConditionalOnProperty(name = "copilot.task.enabled", havingValue = "true", matchIfMissing = true)
    public ContinuousTaskTool continuousTaskTool(
            TaskPersistenceManager persistenceManager,
            TaskDependencyManager dependencyManager,
            TaskScheduler taskScheduler) {
        return new ContinuousTaskTool(persistenceManager, dependencyManager, taskScheduler);
    }
}

