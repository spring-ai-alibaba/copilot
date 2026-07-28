package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.CreateConversationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationServiceImplTest {

    @Test
    void rejectsConversationWithoutAuthenticatedUser() {
        ConversationServiceImpl service = new ConversationServiceImpl(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createConversation(null, new CreateConversationRequest()));

        assertEquals("用户未登录或登录状态已失效", error.getMessage());
    }
}
