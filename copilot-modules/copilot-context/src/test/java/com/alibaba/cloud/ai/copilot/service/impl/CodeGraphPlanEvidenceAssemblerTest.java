package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.service.CodeGraphService;
import com.alibaba.cloud.ai.copilot.domain.dto.PlanWorkspaceDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGraphPlanEvidenceAssemblerTest {

    @Test
    void turnsAffectedTestResultsIntoCompactApprovalEvidence(@TempDir Path workspace) {
        CodeGraphService codeGraph = new CodeGraphService() {
            @Override public boolean isAvailable(Path ignored) { return true; }
            @Override public String search(Path ignored, String query) { return ""; }
            @Override public String explore(Path ignored, String query) { return ""; }
            @Override public String impact(Path ignored, String symbol) {
                return """
                        {"nodeCount":7,"edgeCount":9,"affected":[
                          {"filePath":"src/AuthService.java"},
                          {"filePath":"src/LoginController.java"}
                        ]}
                        """;
            }
            @Override public String affectedTests(Path ignored, String file) {
                return """
                        {"affectedTests":["src/LoginForm.test.tsx","src/e2e/login.spec.ts"]}
                        """;
            }
        };
        CodeGraphPlanEvidenceAssembler assembler = new CodeGraphPlanEvidenceAssembler(
                codeGraph, new ObjectMapper());

        PlanWorkspaceDTO.PlanChange change = new PlanWorkspaceDTO.PlanChange();
        change.setSymbols(List.of("AuthService.authenticate"));
        CodeGraphPlanEvidenceAssembler.EvidenceResult result = assembler.assemble(
                workspace, List.of(change), List.of("src/LoginForm.tsx:10-20", "src/LoginForm.tsx"));

        assertEquals("AVAILABLE", result.status());
        assertEquals(2, result.evidence().size());
        assertEquals("src/LoginForm.tsx", result.evidence().getFirst().getSubject());
        assertTrue(result.evidence().getFirst().getSummary().contains("LoginForm.test.tsx"));
        assertEquals("CALL_CHAIN_IMPACT", result.evidence().get(1).getType());
        assertTrue(result.evidence().get(1).getSummary().contains("7 个代码节点"));
    }

    @Test
    void omitsEvidenceWhenTheWorkspaceIsNotIndexed(@TempDir Path workspace) {
        CodeGraphService unavailable = new CodeGraphService() {
            @Override public boolean isAvailable(Path ignored) { return false; }
            @Override public String search(Path ignored, String query) { return ""; }
            @Override public String explore(Path ignored, String query) { return ""; }
            @Override public String impact(Path ignored, String symbol) { return ""; }
            @Override public String affectedTests(Path ignored, String file) { return ""; }
        };

        CodeGraphPlanEvidenceAssembler.EvidenceResult result = new CodeGraphPlanEvidenceAssembler(
                unavailable, new ObjectMapper()).assemble(workspace, List.of(), List.of("src/App.tsx"));

        assertEquals("UNAVAILABLE", result.status());
        assertTrue(result.evidence().isEmpty());
    }
}
