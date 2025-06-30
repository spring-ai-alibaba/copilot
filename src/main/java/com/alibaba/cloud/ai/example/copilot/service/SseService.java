package com.alibaba.cloud.ai.example.copilot.service;

import com.alibaba.cloud.ai.example.copilot.planning.TaskPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE服务
 * 提供Server-Sent Events功能来推送任务状态更新
 */
@Service
public class SseService {

    private static final Logger logger = LoggerFactory.getLogger(SseService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 存储客户端连接，键为任务ID，值为SseEmitter列表
    private final Map<String, ConcurrentHashMap<String, SseEmitter>> taskEmitters = new ConcurrentHashMap<>();
    
    /**
     * 创建SSE连接
     * @param taskId 任务ID
     * @param clientId 客户端ID
     * @return SseEmitter
     */
    public SseEmitter createConnection(String taskId, String clientId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // 为任务创建emitter映射
        taskEmitters.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(clientId, emitter);
        
        // 设置连接完成和超时的回调
        emitter.onCompletion(() -> removeConnection(taskId, clientId));
        emitter.onTimeout(() -> removeConnection(taskId, clientId));
        emitter.onError((ex) -> {
            logger.error("SSE连接错误，任务ID: {}, 客户端ID: {}", taskId, clientId, ex);
            removeConnection(taskId, clientId);
        });
        
        logger.info("创建SSE连接，任务ID: {}, 客户端ID: {}", taskId, clientId);
        return emitter;
    }
    
    /**
     * 移除连接
     * @param taskId 任务ID
     * @param clientId 客户端ID
     */
    private void removeConnection(String taskId, String clientId) {
        Map<String, SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters != null) {
            emitters.remove(clientId);
            if (emitters.isEmpty()) {
                taskEmitters.remove(taskId);
            }
        }
        logger.info("移除SSE连接，任务ID: {}, 客户端ID: {}", taskId, clientId);
    }
    

    /**
     * 发送步骤流式内容更新
     * @param taskId 任务ID
     * @param stepIndex 步骤索引
     * @param chunk 内容块
     * @param isComplete 是否完成
     */
    public void sendStepChunkUpdate(String taskId, int stepIndex, String chunk, boolean isComplete) {
        Map<String, SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        try {
            // 构建消息数据
            Map<String, Object> data = Map.of(
                "taskId", taskId,
                "stepIndex", stepIndex,
                "chunk", chunk,
                "isComplete", isComplete,
                "timestamp", System.currentTimeMillis()
            );

            String jsonData = objectMapper.writeValueAsString(data);

            // 向所有连接的客户端发送消息
            emitters.entrySet().removeIf(entry -> {
                try {
                    entry.getValue().send(SseEmitter.event()
                        .name("stepChunk")
                        .data(jsonData));
                    return false;
                } catch (IOException e) {
                    logger.error("发送SSE消息失败，任务ID: {}, 客户端ID: {}", taskId, entry.getKey(), e);
                    return true; // 移除失败的连接
                }
            });

            logger.debug("已通过SSE发送步骤chunk更新，任务ID: {}, 步骤: {}, 连接数: {}", taskId, stepIndex, emitters.size());

        } catch (Exception e) {
            logger.error("构建SSE消息失败，任务ID: {}", taskId, e);
        }
    }

    /**
     * 发送任务更新
     * @param taskId 任务ID
     * @param taskPlan 任务计划
     */
    public void sendTaskUpdate(String taskId, TaskPlan taskPlan) {
        Map<String, SseEmitter> emitters = taskEmitters.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        
        try {
            // 构建消息数据
            Map<String, Object> data = Map.of(
                "taskId", taskId,
                "taskPlan", taskPlan,
                "timestamp", System.currentTimeMillis()
            );
            
            String jsonData = objectMapper.writeValueAsString(data);
            
            // 向所有连接的客户端发送消息
            emitters.entrySet().removeIf(entry -> {
                try {
                    entry.getValue().send(SseEmitter.event()
                        .name("taskUpdate")
                        .data(jsonData));
                    return false;
                } catch (IOException e) {
                    logger.error("发送SSE消息失败，任务ID: {}, 客户端ID: {}", taskId, entry.getKey(), e);
                    return true; // 移除失败的连接
                }
            });
            
            logger.info("已通过SSE发送任务更新，任务ID: {}, 连接数: {}", taskId, emitters.size());
            
        } catch (Exception e) {
            logger.error("构建SSE消息失败，任务ID: {}", taskId, e);
        }
    }
}
