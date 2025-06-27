package com.example.chat.controller;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatResponse;
import com.example.chat.service.ChatService;
import com.example.chat.service.SseService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天控制器
 * 提供聊天相关的REST API接口
 * 
 * @author AI Assistant
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private SseService sseService;
    
    /**
     * 健康检查端点
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        
        boolean isHealthy = chatService.isHealthy();
        response.put("status", isHealthy ? "ok" : "error");
        response.put("message", isHealthy ? "聊天服务正常运行" : "聊天服务异常");
        response.put("timestamp", System.currentTimeMillis());
        response.put("connections", sseService.getConnectionCount());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 发送消息（同步方式）
     * 
     * @param request 消息请求
     * @return 聊天响应
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody Map<String, String> request) {
        try {
            String messageContent = request.get("message");
            String conversationId = request.get("conversationId");
            
            if (messageContent == null || messageContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    ChatResponse.error(conversationId, "消息内容不能为空")
                );
            }
            
            // 如果没有提供会话ID，生成一个新的
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = chatService.generateConversationId();
            }
            
            logger.info("收到聊天消息: {}, 会话ID: {}", messageContent, conversationId);
            
            // 创建消息对象
            ChatMessage message = new ChatMessage(messageContent, ChatMessage.MessageType.USER, conversationId);
            
            // 处理消息
            ChatResponse response = chatService.sendMessage(message);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("处理聊天消息失败", e);
            return ResponseEntity.internalServerError().body(
                ChatResponse.error(null, "服务器内部错误: " + e.getMessage())
            );
        }
    }
    
    /**
     * 发送消息（异步流式方式）
     * 
     * @param request 消息请求
     * @return 会话ID和状态
     */
    @PostMapping("/send-stream")
    public ResponseEntity<Map<String, Object>> sendMessageStream(@Valid @RequestBody Map<String, String> request) {
        try {
            String messageContent = request.get("message");
            String conversationId = request.get("conversationId");
            
            if (messageContent == null || messageContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "消息内容不能为空"
                ));
            }
            
            // 如果没有提供会话ID，生成一个新的
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = chatService.generateConversationId();
            }
            
            logger.info("收到流式聊天消息: {}, 会话ID: {}", messageContent, conversationId);
            
            // 创建消息对象
            ChatMessage message = new ChatMessage(messageContent, ChatMessage.MessageType.USER, conversationId);
            
            // 异步处理消息
            chatService.sendMessageAsync(message);
            
            // 返回会话ID
            Map<String, Object> response = new HashMap<>();
            response.put("status", "processing");
            response.put("conversationId", conversationId);
            response.put("message", "消息正在处理中，请通过SSE连接获取响应");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("处理流式聊天消息失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "服务器内部错误: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 创建SSE连接以接收流式响应
     * 
     * @param conversationId 会话ID
     * @return SSE连接
     */
    @GetMapping("/stream/{conversationId}")
    public SseEmitter streamResponse(@PathVariable String conversationId) {
        logger.info("创建SSE连接，会话ID: {}", conversationId);
        return sseService.createConnection(conversationId);
    }
    
    /**
     * 生成新的会话ID
     * 
     * @return 会话ID
     */
    @PostMapping("/conversation/new")
    public ResponseEntity<Map<String, Object>> createConversation() {
        String conversationId = chatService.generateConversationId();
        
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("timestamp", System.currentTimeMillis());
        
        logger.info("创建新会话，会话ID: {}", conversationId);
        
        return ResponseEntity.ok(response);
    }
}
