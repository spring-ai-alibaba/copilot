package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.agent.SessionRunGuard;
import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.core.exception.ServiceException;
import com.alibaba.cloud.ai.copilot.domain.dto.ConversationContextStatus;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ConversationContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/conversations/{conversationId}/context")
@RequiredArgsConstructor
public class ConversationContextController {

    private final ConversationContextService contextService;

    @GetMapping
    public R<ConversationContextStatus> getContext(@PathVariable String conversationId) {
        try {
            return R.ok(contextService.getStatus(conversationId, LoginHelper.getUserId()));
        } catch (IllegalArgumentException e) {
            return R.fail(403, "会话不存在或无权访问");
        } catch (ServiceException e) {
            return R.fail(e.getCode() == null ? 500 : e.getCode(), e.getMessage());
        }
    }

    @DeleteMapping
    public R<ConversationContextStatus> resetContext(@PathVariable String conversationId) {
        try {
            return R.ok(contextService.reset(conversationId, LoginHelper.getUserId()));
        } catch (SessionRunGuard.SessionRunConflictException e) {
            return R.fail(409, "会话正在处理中，暂时不能重置上下文");
        } catch (IllegalArgumentException e) {
            return R.fail(403, "会话不存在或无权访问");
        } catch (ServiceException e) {
            return R.fail(e.getCode() == null ? 500 : e.getCode(), e.getMessage());
        }
    }
}
