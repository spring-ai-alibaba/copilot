package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import com.alibaba.cloud.ai.copilot.service.CodeGraphService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将 CodeGraph 的原始查询结果压缩为审批卡可直接展示的证据。 */
@Component
@RequiredArgsConstructor
public class CodeGraphPlanEvidenceAssembler {

    private static final int MAX_FILES = 3;
    private static final int MAX_TESTS_PER_FILE = 4;

    private final CodeGraphService codeGraphService;
    private final ObjectMapper objectMapper;

    public EvidenceResult assemble(Path workspace, List<String> affectedFiles) {
        if (!codeGraphService.isAvailable(workspace)) {
            return EvidenceResult.unavailable();
        }
        List<PlanWorkspaceDTO.PlanEvidence> evidence = new ArrayList<>();
        for (String file : distinctWorkspaceFiles(affectedFiles).stream().limit(MAX_FILES).toList()) {
            List<String> tests = affectedTests(codeGraphService.affectedTests(workspace, withoutLineRange(file)));
            if (tests.isEmpty()) {
                continue;
            }
            PlanWorkspaceDTO.PlanEvidence item = new PlanWorkspaceDTO.PlanEvidence();
            item.setSource("CodeGraph");
            item.setType("AFFECTED_TESTS");
            item.setSubject(file);
            item.setRelatedFiles(tests);
            item.setSummary("修改该文件后，建议重点验证 " + String.join("、", tests));
            evidence.add(item);
        }
        return new EvidenceResult("AVAILABLE", evidence);
    }

    private List<String> distinctWorkspaceFiles(List<String> files) {
        if (files == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String file : files) {
            String normalized = withoutLineRange(file);
            if (normalized != null && !normalized.isBlank() && !normalized.contains("..")) {
                distinct.add(normalized);
            }
        }
        return new ArrayList<>(distinct);
    }

    private List<String> affectedTests(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode values = root.path("affectedTests");
            if (!values.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            values.forEach(value -> {
                if (value.isTextual() && result.size() < MAX_TESTS_PER_FILE) {
                    result.add(value.asText());
                }
            });
            return result;
        } catch (Exception ignored) {
            // CodeGraph 失败或返回非 JSON 时，保持审批计划可用并省略证据。
            return List.of();
        }
    }

    private String withoutLineRange(String file) {
        return file == null ? "" : file.replaceFirst(":\\d+(?:-\\d+)?$", "");
    }

    public record EvidenceResult(String status, List<PlanWorkspaceDTO.PlanEvidence> evidence) {
        static EvidenceResult unavailable() {
            return new EvidenceResult("UNAVAILABLE", List.of());
        }
    }
}
