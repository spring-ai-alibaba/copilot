package com.alibaba.cloud.ai.copilot.conversation.api;

import com.alibaba.cloud.ai.copilot.conversation.domain.ChatSession;
import com.alibaba.cloud.ai.copilot.conversation.domain.ConversationMode;
import com.alibaba.cloud.ai.copilot.conversation.domain.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话服务接口
 * 负责处理AI对话的核心逻辑
 *
 * @author Alibaba Cloud AI Team
 */
public interface ConversationService {

    /**
     * 开始新的对话会话
     *
     * @param userId 用户ID
     * @param mode 对话模式
     * @return 会话ID
     */
    String startConversation(String userId, ConversationMode mode);

    /**
     * 处理对话消息
     *
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @param model 使用的AI模型
     * @param emitter SSE发射器用于流式响应
     */
    void processConversation(String sessionId, List<Message> messages, String model, SseEmitter emitter);

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    ChatSession getSession(String sessionId);

    /**
     * 结束对话会话
     *
     * @param sessionId 会话ID
     */
    void endConversation(String sessionId);

    /**
     * 获取用户的所有活跃会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> getActiveSessions(String userId);
}