package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.service.PromptEnhancementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Enhanced prompt controller
 */
@Slf4j
@RestController
@RequestMapping("/api/enhancedPrompt")
@RequiredArgsConstructor
public class EnhancedPromptController {

    private final PromptEnhancementService promptEnhancementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handle enhanced prompt requests
     * Supports both JSON and plain text input
     */
    @PostMapping(consumes = {"application/json", "text/plain"})
    public ResponseEntity<Map<String, Object>> enhancePrompt(@RequestBody String requestBody) {
        try {
            log.info("Enhanced prompt request received: {}", requestBody);

            // Extract prompt from request body
            String originalPrompt = extractPrompt(requestBody);

            if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Prompt cannot be empty",
                    "status", "error"
                ));
            }

            // Use AI service to enhance the prompt
            String enhancedPrompt = promptEnhancementService.enhancePrompt(originalPrompt);

            return ResponseEntity.ok(Map.of(
                "originalPrompt", originalPrompt,
                "enhancedPrompt", enhancedPrompt,
                "status", "success"
            ));

        } catch (Exception e) {
            log.error("Error enhancing prompt", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to enhance prompt: " + e.getMessage(),
                "status", "error"
            ));
        }
    }

    /**
     * Extract prompt from request body (supports both JSON and plain text)
     */
    private String extractPrompt(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return null;
        }

        // Try to parse as JSON first
        if (requestBody.trim().startsWith("{")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(requestBody);

                // Try different field names that might contain the prompt
                if (jsonNode.has("prompt")) {
                    return jsonNode.get("prompt").asText();
                } else if (jsonNode.has("text")) {
                    return jsonNode.get("text").asText();
                } else if (jsonNode.has("message")) {
                    return jsonNode.get("message").asText();
                }

                log.warn("JSON request body does not contain expected prompt field: {}", requestBody);
                return null;

            } catch (Exception e) {
                log.warn("Failed to parse JSON, treating as plain text: {}", e.getMessage());
            }
        }

        // Treat as plain text
        return requestBody.trim();
    }
}
