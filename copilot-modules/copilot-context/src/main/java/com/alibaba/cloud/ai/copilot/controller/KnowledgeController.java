package com.alibaba.cloud.ai.copilot.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.IndexRequest;
import com.alibaba.cloud.ai.copilot.knowledge.service.CodebaseIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final CodebaseIndexer codebaseIndexer;
    private final AppProperties appProperties;

    /**
     * 触发全量/增量代码索引
     */
    @PostMapping("/index")
    public ResponseEntity<String> refreshIndex(@RequestBody IndexRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        
        log.info("接收到索引请求: userId={}, path={}", userId, request.getWorkspacePath());
        
        try {
            codebaseIndexer.refresh(userId, request.getWorkspacePath());
            return ResponseEntity.ok("索引任务已触发");
        } catch (Exception e) {
            log.error("索引失败", e);
            return ResponseEntity.internalServerError().body("索引失败: " + e.getMessage());
        }
    }

    /**
     * 获取 workspace 根目录的绝对路径
     * 前端可以用这个路径来触发索引
     */
    @GetMapping("/workspace-path")
    public ResponseEntity<Map<String, String>> getWorkspacePath() {
        try {
            // workspace 根目录统一取自 app.workspace.root-directory 配置
            String workspacePath = java.nio.file.Paths.get(
                    appProperties.getWorkspace().getRootDirectory()).toAbsolutePath().toString();
            String workingDir = System.getProperty("user.dir");
            
            log.info("返回 workspace 路径: {}", workspacePath);
            
            return ResponseEntity.ok(Map.of(
                "workspacePath", workspacePath,
                "workingDir", workingDir
            ));
        } catch (Exception e) {
            log.error("获取 workspace 路径失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "获取路径失败: " + e.getMessage()
            ));
        }
    }
}
