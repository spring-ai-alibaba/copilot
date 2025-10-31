package com.alibaba.cloud.ai.copilot.context.api;

import com.alibaba.cloud.ai.copilot.context.domain.ContextScope;
import com.alibaba.cloud.ai.copilot.context.domain.ConversationContext;

import java.util.List;
import java.util.Map;

/**
 * 上下文管理器接口
 * 负责管理对话上下文、状态和历史记录
 *
 * @author Alibaba Cloud AI Team
 */
public interface ContextManager {

    /**
     * 创建新的对话上下文
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param scope 上下文范围
     * @return 上下文ID
     */
    String createContext(String sessionId, String userId, ContextScope scope);

    /**
     * 获取对话上下文
     *
     * @param contextId 上下文ID
     * @return 对话上下文
     */
    ConversationContext getContext(String contextId);

    /**
     * 更新上下文信息
     *
     * @param contextId 上下文ID
     * @param key 键
     * @param value 值
     */
    void updateContext(String contextId, String key, Object value);

    /**
     * 批量更新上下文信息
     *
     * @param contextId 上下文ID
     * @param updates 更新数据
     */
    void updateContext(String contextId, Map<String, Object> updates);

    /**
     * 清除上下文信息
     *
     * @param contextId 上下文ID
     */
    void clearContext(String contextId);

    /**
     * 获取用户的所有上下文
     *
     * @param userId 用户ID
     * @return 上下文列表
     */
    List<ConversationContext> getUserContexts(String userId);

    /**
     * 切换上下文
     *
     * @param fromContextId 源上下文ID
     * @param toContextId 目标上下文ID
     */
    void switchContext(String fromContextId, String toContextId);

    /**
     * 合并上下文
     *
     * @param sourceContextIds 源上下文ID列表
     * @param targetContextId 目标上下文ID
     */
    void mergeContexts(List<String> sourceContextIds, String targetContextId);

    /**
     * 检查上下文是否存在
     *
     * @param contextId 上下文ID
     * @return 是否存在
     */
    boolean contextExists(String contextId);

    /**
     * 获取上下文统计信息
     *
     * @param contextId 上下文ID
     * @return 统计信息
     */
    Map<String, Object> getContextStats(String contextId);
}