package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.alibaba.cloud.ai.copilot.mapper.ChatMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanWorkspaceStateServiceImplTest {

    private final ChatMessageMapper mapper = mock(ChatMessageMapper.class);
    private final AgentStateStore stateStore = mock(AgentStateStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PlanWorkspaceStateServiceImpl service =
            new PlanWorkspaceStateServiceImpl(mapper, stateStore, objectMapper);

    @Test
    void normalizesTodoWritePayload() {
        List<PlanWorkspaceDTO.PlanTask> tasks = service.normalizeTasks("""
                {"todos":[
                  {"id":"one","content":"修改文件","status":"in-progress","priority":"HIGH"},
                  {"subject":"运行测试","state":"done"}
                ]}
                """);

        assertEquals(2, tasks.size());
        assertEquals("in_progress", tasks.get(0).getStatus());
        assertEquals("high", tasks.get(0).getPriority());
        assertEquals("completed", tasks.get(1).getStatus());
    }

    @Test
    void persistsStatusAsHiddenSystemEvent() {
        service.recordStatus("conversation", "EXECUTING", "正在执行", false);

        verify(mapper).insert(any(ChatMessageEntity.class));
    }

    @Test
    void restoresLatestStatusReviewAndTasks() throws Exception {
        PlanWorkspaceDTO status = new PlanWorkspaceDTO();
        status.setConversationId("conversation");
        status.setStatus("EXECUTING");
        status.setMessage("正在执行");

        PlanWorkspaceDTO.PlanReview review = new PlanWorkspaceDTO.PlanReview();
        review.setReviewId("review-v2");
        review.setPlanContent("# 实施计划");

        PlanWorkspaceDTO.PlanTask task = new PlanWorkspaceDTO.PlanTask();
        task.setContent("修改文件");
        task.setStatus("in_progress");

        when(mapper.selectLatestWorkspaceEvent("conversation", PlanWorkspaceStateServiceImpl.STATUS_EVENT))
                .thenReturn(event(objectMapper.writeValueAsString(status)));
        when(mapper.selectLatestWorkspaceEvent("conversation", PlanWorkspaceStateServiceImpl.REVIEW_EVENT))
                .thenReturn(event(objectMapper.writeValueAsString(review)));
        when(mapper.selectLatestWorkspaceEvent("conversation", PlanWorkspaceStateServiceImpl.TASKS_EVENT))
                .thenReturn(event(objectMapper.writeValueAsString(List.of(task))));
        when(stateStore.get(eq(null), eq("conversation"), eq("agent_state"), any()))
                .thenReturn(java.util.Optional.empty());

        PlanWorkspaceDTO restored = service.getWorkspace("conversation");

        assertEquals("EXECUTING", restored.getStatus());
        assertNotNull(restored.getReview());
        assertEquals("review-v2", restored.getReview().getReviewId());
        assertEquals(1, restored.getTasks().size());
        assertFalse(restored.isDecisionAllowed());
    }

    private ChatMessageEntity event(String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setContent(content);
        return entity;
    }
}
