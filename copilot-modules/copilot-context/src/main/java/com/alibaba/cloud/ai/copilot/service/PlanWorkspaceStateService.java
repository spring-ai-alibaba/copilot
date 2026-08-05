package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;

import java.util.List;
import java.util.Map;

public interface PlanWorkspaceStateService {

    PlanWorkspaceDTO getWorkspace(String conversationId);

    void recordStatus(
            String conversationId,
            String status,
            String message,
            boolean decisionAllowed);

    void recordReview(String conversationId, Map<String, Object> reviewPayload);

    void recordTasks(String conversationId, List<PlanWorkspaceDTO.PlanTask> tasks);

    List<PlanWorkspaceDTO.PlanTask> normalizeTasks(Object source);
}
