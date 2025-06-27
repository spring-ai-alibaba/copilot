package com.alibaba.cloud.ai.example.copilot.controller;

import com.alibaba.cloud.ai.example.copilot.template.TemplateBasedProjectGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于模板的项目生成控制器
 */
@RestController
@RequestMapping("/api/template-project")
@CrossOrigin(origins = "*")
public class TemplateProjectController {

    private static final Logger logger = LoggerFactory.getLogger(TemplateProjectController.class);

    @Autowired
    private TemplateBasedProjectGenerator templateGenerator;

    /**
     * 项目生成请求DTO
     */
    public static class ProjectGenerationRequest {

        private String projectName;

        private String projectDescription = "";
        private String customRequirements = "";

        // Getters and Setters
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        public String getProjectDescription() { return projectDescription; }
        public void setProjectDescription(String projectDescription) { this.projectDescription = projectDescription; }

        public String getCustomRequirements() { return customRequirements; }
        public void setCustomRequirements(String customRequirements) { this.customRequirements = customRequirements; }
    }

    /**
     * 基于模板生成新项目
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateProject(@RequestBody ProjectGenerationRequest request) {
        try {
            logger.info("收到项目生成请求: {}", request.getProjectName());

            // 生成项目
            String projectPath = templateGenerator.generateProjectFromTemplate(
                    request.getProjectName(),
                    request.getProjectDescription(),
                    request.getCustomRequirements()
            );

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "项目生成成功");
            response.put("projectName", request.getProjectName());
            response.put("projectPath", projectPath);
            response.put("timestamp", System.currentTimeMillis());

            logger.info("项目生成成功: {} -> {}", request.getProjectName(), projectPath);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("项目生成失败: {}", request.getProjectName(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "项目生成失败: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取所有生成的项目列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getGeneratedProjects() {
        try {
            List<String> projects = templateGenerator.getGeneratedProjects();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("projects", projects);
            response.put("count", projects.size());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取项目列表失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取项目列表失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 删除生成的项目
     */
    @DeleteMapping("/{projectName}")
    public ResponseEntity<Map<String, Object>> deleteProject(@PathVariable String projectName) {
        try {
            boolean deleted = templateGenerator.deleteGeneratedProject(projectName);

            Map<String, Object> response = new HashMap<>();
            if (deleted) {
                response.put("status", "success");
                response.put("message", "项目删除成功");
            } else {
                response.put("status", "error");
                response.put("message", "项目不存在或删除失败");
            }
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("删除项目失败: {}", projectName, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "删除项目失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 获取模板项目信息
     */
    @GetMapping("/template-info")
    public ResponseEntity<Map<String, Object>> getTemplateInfo() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("templateName", "Spring AI + Vue3 Chat Template");
            response.put("templateDescription", "基础的AI聊天应用模板，包含Spring Boot后端和Vue3前端");
            response.put("features", List.of(
                "Spring Boot 3.x + Spring AI",
                "Vue3 + Ant Design Vue前端",
                "SSE流式响应",
                "基础聊天功能",
                "现代化开发工具链"
            ));
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取模板信息失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取模板信息失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "模板项目生成服务正常运行");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
}
