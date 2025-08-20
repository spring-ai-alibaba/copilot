package com.alibaba.cloud.ai.copilot.conversation.application;

import com.alibaba.cloud.ai.copilot.conversation.api.ConversationService;
import com.alibaba.cloud.ai.copilot.conversation.domain.ChatSession;
import com.alibaba.cloud.ai.copilot.conversation.domain.ConversationMode;
import com.alibaba.cloud.ai.copilot.conversation.domain.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话服务实现
 *
 * @author Alibaba Cloud AI Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    // 内存存储会话信息（生产环境应使用数据库）
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userSessions = new ConcurrentHashMap<>();

    @Override
    public String startConversation(String userId, ConversationMode mode) {
        String sessionId = UUID.randomUUID().toString();

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .title("新对话 - " + mode.getName())
                .mode(mode)
                .status(ChatSession.SessionStatus.ACTIVE)
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .config(ChatSession.SessionConfig.builder()
                        .model("default")
                        .temperature(0.7)
                        .maxTokens(4096)
                        .enableTools(true)
                        .allowedTools(new ArrayList<>())
                        .build())
                .build();

        sessions.put(sessionId, session);
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(sessionId);

        log.info("Started new conversation: sessionId={}, userId={}, mode={}",
                sessionId, userId, mode);

        return sessionId;
    }

    @Override
    public void processConversation(String sessionId, List<Message> messages, String model, SseEmitter emitter) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            log.error("Session not found: {}", sessionId);
            try {
                emitter.completeWithError(new IllegalArgumentException("Session not found"));
            } catch (Exception e) {
                log.error("Error completing emitter with error", e);
            }
            return;
        }

        try {
            // 更新会话消息
            session.getMessages().addAll(messages);
            session.setUpdatedAt(LocalDateTime.now());

            // 这里应该调用AI模型进行处理
            // 目前先返回一个模拟响应
            processWithAI(session, model, emitter);

        } catch (Exception e) {
            log.error("Error processing conversation: sessionId={}", sessionId, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Error completing emitter with error", ex);
            }
        }
    }

    @Override
    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void endConversation(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(ChatSession.SessionStatus.ENDED);
            session.setUpdatedAt(LocalDateTime.now());
            log.info("Ended conversation: sessionId={}", sessionId);
        }
    }

    @Override
    public List<ChatSession> getActiveSessions(String userId) {
        List<String> sessionIds = userSessions.get(userId);
        if (sessionIds == null) {
            return new ArrayList<>();
        }

        return sessionIds.stream()
                .map(sessions::get)
                .filter(session -> session != null &&
                        session.getStatus() == ChatSession.SessionStatus.ACTIVE)
                .toList();
    }

    /**
     * 使用AI模型处理对话
     *
     * @param session 会话
     * @param model 模型
     * @param emitter SSE发射器
     */
    private void processWithAI(ChatSession session, String model, SseEmitter emitter) {
        try {
            // TODO: 集成实际的AI模型调用
            // 这里先返回一个模拟响应

            String response = "这是一个模拟的AI响应，会话ID: " + session.getSessionId();

            Message aiMessage = new Message();
            aiMessage.setId(UUID.randomUUID().toString());
            aiMessage.setRole("assistant");
            aiMessage.setContent(response);
            aiMessage.setTimestamp(LocalDateTime.now());

            session.addMessage(aiMessage);

            // 发送响应
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(response));

            emitter.complete();

        } catch (Exception e) {
            log.error("Error in AI processing", e);
            throw new RuntimeException("AI processing failed", e);
        }
    }
}