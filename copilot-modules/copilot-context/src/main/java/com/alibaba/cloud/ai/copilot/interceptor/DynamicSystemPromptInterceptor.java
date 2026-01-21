package com.alibaba.cloud.ai.copilot.interceptor;

import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

/**
 * 动态系统提示拦截器
 * 根据会话上下文动态调整系统提示
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicSystemPromptInterceptor extends ModelInterceptor {

    private final ConversationService conversationService;

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        // 从上下文获取会话ID
        String conversationId = null;
        if (request.getContext() != null) {
            Object conversationIdObj = request.getContext().get("conversationId");
            if (conversationIdObj != null) {
                conversationId = conversationIdObj.toString();
            }
        }

        if (conversationId != null) {
            try {
                // 获取会话信息
                var conversation = conversationService.getConversation(conversationId);

                if (conversation != null && conversation.getTitle() != null) {
                    // 构建动态系统提示
                    String basePrompt = request.getSystemMessage() != null
                        ? request.getSystemMessage().getText()
                        : "你是一个AI编程助手。";

                    // 添加会话上下文信息
                    String enhancedPrompt = basePrompt + "\n当前会话主题：" + conversation.getTitle();

                    // 更新系统消息
                    SystemMessage enhancedSystemMessage = new SystemMessage(enhancedPrompt);

                    ModelRequest enhancedRequest = ModelRequest.builder(request)
                        .systemMessage(enhancedSystemMessage)
                        .build();

                    return handler.call(enhancedRequest);
                }
            } catch (Exception e) {
                log.error("动态系统提示处理失败: conversationId={}", conversationId, e);
            }
        }

        return handler.call(request);
    }

    @Override
    public String getName() {
        return "dynamic_system_prompt_interceptor";
    }
}

