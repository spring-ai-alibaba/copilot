package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理控制器
 *
 * @author better
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建会话
     */
    @PostMapping
    public R<ConversationDTO> createConversation(@RequestBody CreateConversationRequest request) {
        try {
            Long userId = LoginHelper.getUserId();
            String conversationId = conversationService.createConversation(userId, request);
            ConversationDTO conversation = conversationService.getConversation(conversationId);
            return R.ok(conversation);
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return R.fail("创建会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话列表
     */
    @GetMapping
    public R<PageResult<ConversationDTO>> listConversations(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Long userId = LoginHelper.getUserId();
            PageResult<ConversationDTO> result = conversationService.listConversations(userId, page, size);
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取会话列表失败", e);
            return R.fail("获取会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/{conversationId}")
    public R<ConversationDTO> getConversation(@PathVariable String conversationId) {
        try {
            Long userId = LoginHelper.getUserId();
            // Service层会进行权限验证
            conversationService.checkConversationPermission(conversationId, userId);
            ConversationDTO conversation = conversationService.getConversation(conversationId);
            return R.ok(conversation);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取会话信息失败", e);
            return R.fail("获取会话信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/{conversationId}/messages")
    public R<List<ChatMessage>> getConversationMessages(@PathVariable String conversationId) {
        try {
            Long userId = LoginHelper.getUserId();
            // Service层会进行权限验证和DTO转换
            List<ChatMessage> messages = conversationService.getConversationMessages(conversationId, userId);
            return R.ok(messages);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取会话消息失败", e);
            return R.fail("获取会话消息失败: " + e.getMessage());
        }
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/{conversationId}/title")
    public R<Void> updateConversationTitle(
        @PathVariable String conversationId,
        @RequestParam String title
    ) {
        try {
            Long userId = LoginHelper.getUserId();
            // Service层会进行权限验证
            conversationService.updateConversationTitle(conversationId, title, userId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新会话标题失败", e);
            return R.fail("更新会话标题失败: " + e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{conversationId}")
    public R<Void> deleteConversation(@PathVariable String conversationId) {
        try {
            Long userId = LoginHelper.getUserId();
            // Service层会进行权限验证
            conversationService.deleteConversation(conversationId, userId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除会话失败", e);
            return R.fail("删除会话失败: " + e.getMessage());
        }
    }
}

