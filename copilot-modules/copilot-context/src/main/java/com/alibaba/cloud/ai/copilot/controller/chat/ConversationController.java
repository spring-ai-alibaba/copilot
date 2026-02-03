package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationDTO;
import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import com.alibaba.cloud.ai.copilot.domain.dto.PageResult;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.alibaba.cloud.ai.copilot.service.ConversationService;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final ChatMessageMapper chatMessageMapper;

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
            // 验证权限
            Long userId = LoginHelper.getUserId();
            ConversationDTO conversation = conversationService.getConversation(conversationId);

            if (conversation == null) {
                return R.fail("会话不存在");
            }

            if (!conversation.getUserId().equals(userId)) {
                return R.fail("无权访问该会话");
            }

            return R.ok(conversation);
        } catch (Exception e) {
            log.error("获取会话信息失败", e);
            return R.fail("获取会话信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/{conversationId}/messages")
    public R<List<com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage>> getConversationMessages(
        @PathVariable String conversationId
    ) {
        try {
            // 验证权限
            Long userId = LoginHelper.getUserId();
            ConversationDTO conversation = conversationService.getConversation(conversationId);

            if (conversation == null) {
                return R.fail("会话不存在");
            }

            if (!conversation.getUserId().equals(userId)) {
                return R.fail("无权访问该会话");
            }

            // 从数据库读取历史消息
            List<ChatMessageEntity> entities = chatMessageMapper.selectByConversationId(conversationId);

            // 转换为 DTO
            List<com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage> dtos = new ArrayList<>();
            for (ChatMessageEntity entity : entities) {
                // 仅返回 user/assistant，过滤 tool/system 以及空内容，避免历史记录污染前端展示
                if (entity.getRole() == null) {
                    continue;
                }
                String role = entity.getRole().trim();
                if (!("user".equals(role) || "assistant".equals(role))) {
                    continue;
                }
                if (entity.getContent() == null || entity.getContent().trim().isEmpty()) {
                    continue;
                }
                com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage dto =
                    new com.alibaba.cloud.ai.copilot.domain.dto.ChatMessage();
                dto.setRole(role);
                dto.setContent(entity.getContent());
                dto.setConversationId(conversationId);
                dto.setCreatedAt(entity.getCreatedTime());
                dtos.add(dto);
            }

            return R.ok(dtos);
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
            // 验证权限
            Long userId = LoginHelper.getUserId();
            ConversationDTO conversation = conversationService.getConversation(conversationId);

            if (conversation == null) {
                return R.fail("会话不存在");
            }

            if (!conversation.getUserId().equals(userId)) {
                return R.fail("无权修改该会话");
            }

            conversationService.updateConversationTitle(conversationId, title);
            return R.ok();
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
            // 验证权限
            Long userId = LoginHelper.getUserId();
            ConversationDTO conversation = conversationService.getConversation(conversationId);

            if (conversation == null) {
                return R.fail("会话不存在");
            }

            if (!conversation.getUserId().equals(userId)) {
                return R.fail("无权删除该会话");
            }

            conversationService.deleteConversation(conversationId);
            return R.ok();
        } catch (Exception e) {
            log.error("删除会话失败", e);
            return R.fail("删除会话失败: " + e.getMessage());
        }
    }
}

