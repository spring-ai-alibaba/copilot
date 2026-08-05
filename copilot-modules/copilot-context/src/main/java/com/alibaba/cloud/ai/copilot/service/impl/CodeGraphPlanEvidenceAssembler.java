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
    private static final int MAX_SYMBOLS = 3;
    private static final int MAX_IMPACT_FILES = 4;

    private final CodeGraphService codeGraphService;
    private final ObjectMapper objectMapper;

    public EvidenceResult assemble(
            Path workspace,
            List<PlanWorkspaceDTO.PlanChange> changes,
            List<String> affectedFiles) {
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
        for (String symbol : distinctSymbols(changes).stream().limit(MAX_SYMBOLS).toList()) {
            PlanWorkspaceDTO.PlanEvidence item = impactEvidence(symbol, codeGraphService.impact(workspace, symbol));
            if (item != null) {
                evidence.add(item);
            }
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

    private PlanWorkspaceDTO.PlanEvidence impactEvidence(String symbol, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            int nodeCount = root.path("nodeCount").asInt();
            int edgeCount = root.path("edgeCount").asInt();
            List<String> files = new ArrayList<>();
            root.path("affected").forEach(node -> {
                String file = node.path("filePath").asText();
                if (!file.isBlank() && files.size() < MAX_IMPACT_FILES && !files.contains(file)) {
                    files.add(file);
                }
            });
            if (nodeCount == 0 && files.isEmpty()) {
                return null;
            }
            PlanWorkspaceDTO.PlanEvidence item = new PlanWorkspaceDTO.PlanEvidence();
            item.setSource("CodeGraph");
            item.setType("CALL_CHAIN_IMPACT");
            item.setSubject(symbol);
            item.setRelatedFiles(files);
            item.setSummary("修改关键符号 “" + symbol + "” 可能影响 "
                    + nodeCount + " 个代码节点、" + edgeCount + " 条依赖关系");
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> distinctSymbols(List<PlanWorkspaceDTO.PlanChange> changes) {
        if (changes == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (PlanWorkspaceDTO.PlanChange change : changes) {
            if (change.getSymbols() == null) {
                continue;
            }
            change.getSymbols().stream()
                    .filter(symbol -> symbol != null && !symbol.isBlank())
                    .forEach(distinct::add);
        }
        return new ArrayList<>(distinct);
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
