package com.alibaba.cloud.ai.copilot.memory.controller;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.memory.longterm.MemoryContentLoader;
import com.alibaba.cloud.ai.copilot.memory.longterm.MemorySaveService;
import com.alibaba.cloud.ai.copilot.memory.longterm.MemoryScope;
import com.alibaba.cloud.ai.copilot.memory.longterm.ProjectMemoryLoader;
import com.alibaba.cloud.ai.copilot.memory.token.impl.JtokitTokenCounterService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 记忆系统 API 控制器
 *
 * @author better
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemorySaveService memorySaveService;
    private final MemoryContentLoader memoryContentLoader;
    private final ProjectMemoryLoader projectMemoryLoader;
    private final JtokitTokenCounterService tokenCounterService;

    public MemoryController(
            MemorySaveService memorySaveService,
            MemoryContentLoader memoryContentLoader,
            ProjectMemoryLoader projectMemoryLoader,
            JtokitTokenCounterService tokenCounterService) {
        this.memorySaveService = memorySaveService;
        this.memoryContentLoader = memoryContentLoader;
        this.projectMemoryLoader = projectMemoryLoader;
        this.tokenCounterService = tokenCounterService;
    }

    /**
     * 保存记忆
     */
    @PostMapping("/save")
    public R<Void> saveMemory(@RequestBody SaveMemoryRequest request) {
        try {
            Path targetPath = request.getTargetPath() != null
                    ? Paths.get(request.getTargetPath())
                    : null;

            memorySaveService.saveMemory(
                    request.getContent(),
                    request.getScope(),
                    request.getSection(),
                    targetPath
            );
            return R.ok();
        } catch (Exception e) {
            log.error("Failed to save memory", e);
            return R.fail("保存记忆失败: " + e.getMessage());
        }
    }

    /**
     * 查看当前项目记忆
     */
    @GetMapping("/project")
    public R<String> getProjectMemory(@RequestParam(required = false) String currentDir) {
        try {
            Path currentPath = currentDir != null
                    ? Paths.get(currentDir)
                    : Paths.get(System.getProperty("user.dir"));

            Path projectRoot = projectMemoryLoader.findProjectRoot(currentPath);
            String memory = memoryContentLoader.loadAndMergeMemories(currentPath, projectRoot);
            return R.ok(memory);
        } catch (Exception e) {
            log.error("Failed to load project memory", e);
            return R.fail("加载项目记忆失败: " + e.getMessage());
        }
    }

    /**
     * 获取Token计数缓存统计信息
     */
    @GetMapping("/token-cache/statistics")
    public R<Map<String, Object>> getTokenCacheStatistics() {
        try {
            Map<String, Object> stats = tokenCounterService.getCacheStatistics();
            return R.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get token cache statistics", e);
            return R.fail("获取缓存统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除Token计数缓存
     */
    @PostMapping("/token-cache/clear")
    public R<Void> clearTokenCache() {
        try {
            tokenCounterService.clearAllCaches();
            return R.ok();
        } catch (Exception e) {
            log.error("Failed to clear token cache", e);
            return R.fail("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 保存记忆请求
     */
    @Data
    public static class SaveMemoryRequest {
        private String content;
        private MemoryScope scope;
        private String section;
        private String targetPath;
    }
}

