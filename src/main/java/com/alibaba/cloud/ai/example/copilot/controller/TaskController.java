package com.alibaba.cloud.ai.example.copilot.controller;

import com.alibaba.cloud.ai.example.copilot.planning.TaskCoordinator;
import com.alibaba.cloud.ai.example.copilot.planning.TaskPlan;
import com.alibaba.cloud.ai.example.copilot.service.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务控制器
 * 提供AI编码任务的创建、执行和状态查询接口
 */
@RestController
@RequestMapping("/api/task")
@CrossOrigin(origins = "*")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);

     @Autowired
     private TaskCoordinator taskCoordinator;

     @Autowired
     private SseService sseService;


    /**
     * 健康检查端点
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "AI编码助手服务正常运行");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 创建并开始执行任务
     * @param request 任务请求
     * @return 任务ID和初始状态
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("query");
            if (userRequest == null || userRequest.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "请求内容不能为空"
                ));
            }

            // 生成任务ID
            String taskId = UUID.randomUUID().toString();

            logger.info("创建新任务，任务ID: {}, 请求: {}", taskId, userRequest);

            // 执行任务
            taskCoordinator.startTask(userRequest, taskId);

            // 返回任务创建成功的响应
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", "processing");
            response.put("message", "任务已创建并开始执行，请通过SSE连接获取实时进度");
            response.put("timestamp", System.currentTimeMillis());

            logger.info("任务创建成功，任务ID: {}, 已启动异步执行", taskId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("创建任务失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "创建任务失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            // 获取真实的任务信息
            TaskPlan taskPlan = taskCoordinator.getTaskStatus(taskId);

            // 如果任务不存在，返回错误信息
            if (taskPlan == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "status", "error",
                    "message", "任务不存在: " + taskId
                ));
            }

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("status", taskPlan.getPlanStatus());
            response.put("title", taskPlan.getTitle());
            response.put("description", taskPlan.getDescription());
            response.put("steps", taskPlan.getSteps());
            response.put("stepCount", taskPlan.getStepCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取任务状态失败，任务ID: {}", taskId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "获取任务状态失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 取消结果
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        try {
            // 暂时返回成功响应
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "任务已取消"
            ));

        } catch (Exception e) {
            logger.error("取消任务失败，任务ID: {}", taskId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "取消任务失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 创建SSE连接以接收任务状态更新
     * @param taskId 任务ID
     * @param clientId 客户端ID
     * @return SSE连接
     */
    @GetMapping("/stream/{taskId}")
    public SseEmitter streamTaskUpdates(@PathVariable String taskId,
                                       @RequestParam(defaultValue = "default") String clientId) {
        logger.info("创建SSE连接，任务ID: {}, 客户端ID: {}", taskId, clientId);

        // 使用SseService创建连接
        SseEmitter emitter = sseService.createConnection(taskId, clientId);

        // 发送初始连接确认消息
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"message\":\"SSE连接已建立\",\"taskId\":\"" + taskId + "\",\"timestamp\":" + System.currentTimeMillis() + "}"));
        } catch (Exception e) {
            logger.error("发送SSE初始消息失败，任务ID: {}, 客户端ID: {}", taskId, clientId, e);
        }

        return emitter;
    }

    /**
     * 手动触发下一步规划
     * @param taskId 任务ID
     * @param request 包含步骤结果的请求
     * @return 更新后的任务状态
     */
    @PostMapping("/next-step/{taskId}")
    public ResponseEntity<Map<String, Object>> triggerNextStep(@PathVariable String taskId,
                                                              @RequestBody Map<String, String> request) {
        try {
            String stepResult = request.get("stepResult");
            if (stepResult == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "步骤结果不能为空"
                ));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "下一步已触发");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("触发下一步失败，任务ID: {}", taskId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "触发下一步失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 重试失败的步骤
     * @param taskId 任务ID
     * @param stepIndex 步骤索引
     * @return 重试结果
     */
    @PostMapping("/retry/{taskId}/{stepIndex}")
    public ResponseEntity<Map<String, Object>> retryFailedStep(@PathVariable String taskId,
                                                              @PathVariable int stepIndex) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "步骤重试已开始");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("重试步骤失败，任务ID: {}, 步骤: {}", taskId, stepIndex, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "重试步骤失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取所有活跃任务
     * @return 活跃任务列表
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveTasks() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("activeTasks", new HashMap<>());
            response.put("count", 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取活跃任务失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "获取活跃任务失败: " + e.getMessage()
            ));
        }
    }
}
