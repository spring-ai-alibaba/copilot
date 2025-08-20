package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.service.OpenAiModelFactory;
import com.alibaba.cloud.ai.copilot.service.PromptEnhancementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.Map;

/**
 * Implementation of prompt enhancement service using Spring AI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptEnhancementServiceImpl implements PromptEnhancementService {

    private final DynamicModelService dynamicModelService;
    private final OpenAiModelFactory openAiModelFactory;
    private final ModelConfigService modelConfigService;

    // System prompt for enhancing user prompts
    private static final String ENHANCEMENT_SYSTEM_PROMPT = """
            You are an expert prompt engineer specializing in software development and programming assistance.
            Your task is to enhance user prompts to make them more effective for AI coding assistants.

            Guidelines for enhancement:
            1. Make the prompt more specific and clear about the technical requirements
            2. Add relevant context about the technology stack, programming language, or framework
            3. Structure the request with clear objectives and expected deliverables
            4. Include relevant constraints (performance, security, compatibility)
            5. Specify desired output format (code, explanation, step-by-step guide)
            6. Add context about the project or use case when helpful
            7. Maintain the original intent while improving technical clarity
            8. For coding requests, specify language, framework, and any dependencies
            9. For debugging, include error details and environment information
            10. For architecture questions, specify scale and requirements

            Original prompt: {originalPrompt}

            Please provide an enhanced version of this prompt that will yield better, more actionable results from AI coding assistants.
            Focus on making the request more specific and technically precise.
            Return only the enhanced prompt without any explanations or meta-commentary.
            """;

    @Override
    public String enhancePrompt(String originalPrompt) {
        try {
            log.info("Enhancing prompt: {}", originalPrompt);

            // Validate input
            if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
                return "Please provide a clear and specific description of what you want to accomplish.";
            }

            // 获取默认模型名称
            String modelName = getDefaultModelName();

            // 使用动态模型服务获取ChatModel
            ChatModel chatModel = dynamicModelService.getChatModel(modelName);

            // Create prompt template
            PromptTemplate promptTemplate = new PromptTemplate(ENHANCEMENT_SYSTEM_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("originalPrompt", originalPrompt));

            // 使用OpenAiModelFactory创建自定义配置的ChatOptions
            OpenAiChatOptions chatOptions = openAiModelFactory.createChatOptions(
                    modelName,
                    5000,  // maxTokens - 较小的值用于prompt增强
                    0.3    // temperature - 较低的温度确保一致性
            );

            // Create prompt with options
            Prompt enhancementPrompt = new Prompt(prompt.getInstructions(), chatOptions);

            // Call AI model
            ChatResponse response = chatModel.call(enhancementPrompt);
            String enhancedPrompt = response.getResult().getOutput().getText();

            return enhancedPrompt.trim();

        } catch (Exception e) {
            log.error("Error enhancing prompt", e);
            // Fallback to simple enhancement
            return enhancePromptFallback(originalPrompt);
        }
    }

    /**
     * Fallback enhancement method when AI service is unavailable
     */
    private String enhancePromptFallback(String originalPrompt) {
        log.info("Using fallback enhancement for prompt");

        if (originalPrompt == null || originalPrompt.trim().isEmpty()) {
            return "Please provide a clear description of what you want to build or accomplish.";
        }

        StringBuilder enhanced = new StringBuilder();

        // Add context if the prompt is very short
        if (originalPrompt.length() < 20) {
            enhanced.append("I need help with: ").append(originalPrompt).append("\n\n");
            enhanced.append("Please provide a detailed solution that includes:\n");
            enhanced.append("- Step-by-step implementation\n");
            enhanced.append("- Best practices and considerations\n");
            enhanced.append("- Error handling where appropriate\n");
            enhanced.append("- Clear explanations of the approach");
        } else {
            // For longer prompts, add structure
            enhanced.append("Request: ").append(originalPrompt).append("\n\n");
            enhanced.append("Please ensure your response includes:\n");
            enhanced.append("- A clear implementation plan\n");
            enhanced.append("- Code examples where relevant\n");
            enhanced.append("- Explanation of key concepts\n");
            enhanced.append("- Potential challenges and solutions");
        }

        return enhanced.toString();
    }

    /**
     * 获取默认模型名称（数据库中第一个启用的模型）
     */
    private String getDefaultModelName() {
        try {
            List<ModelConfigEntity> enabledModels = modelConfigService.getAllModelEntities()
                    .stream()
                    .filter(model -> model.getEnabled() != null && model.getEnabled())
                    .sorted((a, b) -> {
                        // 按sortOrder排序，如果没有则按ID排序
                        if (a.getSortOrder() != null && b.getSortOrder() != null) {
                            return a.getSortOrder().compareTo(b.getSortOrder());
                        } else if (a.getSortOrder() != null) {
                            return -1;
                        } else if (b.getSortOrder() != null) {
                            return 1;
                        } else {
                            return a.getId().compareTo(b.getId());
                        }
                    })
                    .toList();

            if (enabledModels.isEmpty()) {
                log.warn("No enabled models found in database, using fallback");
                throw new IllegalStateException("No enabled models available");
            }

            String defaultModel = enabledModels.get(0).getModelName();
            log.debug("Using default model for prompt enhancement: {}", defaultModel);
            return defaultModel;

        } catch (Exception e) {
            log.error("Error getting default model, using fallback", e);
            // 如果数据库查询失败，使用一个常见的模型名作为fallback
            return "deepseek-v3";
        }
    }
}
