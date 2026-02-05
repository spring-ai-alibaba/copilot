package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolProvider;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.copilot.store.PreferenceDeduplicator;
import com.alibaba.cloud.ai.copilot.store.PreferenceInfo;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 学习用户偏好工具
 * Agent 在对话中识别到偏好表达时，主动调用此工具保存
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearnPreferenceTool implements BiFunction<LearnPreferenceTool.LearnPreferenceParams, ToolContext, String>, BuiltinToolProvider {

    private final DatabaseStore databaseStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PreferenceDeduplicator preferenceDeduplicator;
    private final AppProperties appProperties;

    public static final String DESCRIPTION = "学习并保存用户的偏好信息，用于个性化AI助手的行为和响应。\n\n" +
            "【使用场景】\n" +
            "当用户在对话中明确表达个人偏好、习惯或倾向时，主动调用此工具记录。常见触发词包括：\n" +
            "- \"我喜欢...\"、\"我习惯...\"、\"我偏好...\"、\"我通常...\"\n" +
            "- \"我不喜欢...\"、\"我避免...\"、\"我不用...\"\n" +
            "- \"我倾向于...\"、\"我更偏向于...\"、\"我优先选择...\"\n\n" +
            "【参数说明】\n" +
            "- category: 偏好类别（必填），使用英文下划线命名。常见类别：\n" +
            "  * programming_language: 编程语言偏好（如 Java, Python, TypeScript）\n" +
            "  * coding_style: 编码风格（如 函数式编程, 面向对象, 简洁风格）\n" +
            "  * framework_preference: 框架偏好（如 Spring Boot, React, Vue）\n" +
            "  * tool_preference: 工具偏好（如 Git, Docker, VS Code）\n" +
            "  * response_style: 回答风格（如 详细解释, 简洁回答, 代码优先）\n" +
            "  * language_preference: 语言偏好（如 中文, 英文, 中英混合）\n" +
            "  * other: 其他类别\n" +
            "- value: 偏好值（必填），用户偏好的具体内容，应简洁明确\n" +
            "- context: 用户原始表达（可选但推荐），记录用户的原话或上下文，有助于后续理解和去重\n" +
            "- confidence: 置信度（可选，默认 0.8），范围 0.0-1.0，表示对提取偏好的确定程度：\n" +
            "  * 0.9-1.0: 非常明确（如\"我喜欢Java\"）\n" +
            "  * 0.7-0.9: 比较明确（如\"我通常用Java\"）\n" +
            "  * 0.5-0.7: 推测性（如从上下文推断）\n\n" +
            "【注意事项】\n" +
            "- 仅在用户明确表达偏好时调用，避免过度解读\n" +
            "- category 应使用标准化的英文命名，便于后续检索和去重\n" +
            "- 如果识别到多个偏好，应分别调用多次\n" +
            "- 系统会自动进行去重处理，相似偏好会被合并";

    @Override
    public String apply(LearnPreferenceParams params, ToolContext toolContext) {
        try {
            // 验证参数
            if (params.category == null || params.category.trim().isEmpty()) {
                return "Error: category 不能为空";
            }
            if (params.value == null || params.value.trim().isEmpty()) {
                return "Error: value 不能为空";
            }

            // 检查最小置信度阈值
            double confidence = params.confidence != null ? params.confidence : 0.8;
            if (confidence < appProperties.getMemory().getMinConfidence()) {
                return String.format("Error: 置信度 %.2f 低于最小阈值 %.2f，不保存", 
                        confidence, appProperties.getMemory().getMinConfidence());
            }

            // 获取用户ID
            Long userId = LoginHelper.getUserId();
            if (userId == null) {
                return "Error: 无法获取用户ID";
            }

            // 构建偏好信息
            PreferenceInfo newPreference = PreferenceInfo.builder()
                    .category(params.category)
                    .value(params.value)
                    .context(params.context)
                    .confidence(confidence)
                    .learnedAt(LocalDateTime.now())
                    .usageCount(1)
                    .source("agent")
                    .enabled(true)
                    .build();

            // 加载现有偏好
            List<String> namespace = List.of("user_preferences");
            String key = "user_" + userId;

            Optional<StoreItem> prefsOpt = databaseStore.getItem(namespace, key);

            List<PreferenceInfo> existingPreferences = new ArrayList<>();
            if (prefsOpt.isPresent()) {
                Map<String, Object> prefsData = prefsOpt.get().getValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) prefsData.get("items");
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        existingPreferences.add(PreferenceInfo.fromMap(item));
                    }
                }
            }

            // 去重处理
            PreferenceInfo finalPreference = preferenceDeduplicator.deduplicate(newPreference, existingPreferences);

            // 检查是否是新增还是更新
            boolean isNew = !existingPreferences.stream()
                    .anyMatch(p -> p.getCategory().equals(finalPreference.getCategory()) 
                            && p.getValue().equals(finalPreference.getValue()));

            // 更新偏好列表
            if (!isNew) {
                // 更新现有偏好
                existingPreferences.replaceAll(p -> 
                        p.getCategory().equals(finalPreference.getCategory()) 
                        && p.getValue().equals(finalPreference.getValue()) 
                        ? finalPreference : p);
            } else {
                // 添加新偏好
                existingPreferences.add(finalPreference);
            }

            // 保存到 Store
            Map<String, Object> prefsData = new HashMap<>();
            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (PreferenceInfo pref : existingPreferences) {
                itemsList.add(pref.toMap());
            }
            prefsData.put("items", itemsList);

            StoreItem item = StoreItem.of(namespace, key, prefsData);
            databaseStore.putItem(item);

            log.info("学习用户偏好成功: userId={}, category={}, value={}, confidence={}, isNew={}", 
                    userId, finalPreference.getCategory(), finalPreference.getValue(), 
                    finalPreference.getConfidence(), isNew);

            return String.format("成功%s偏好: category=%s, value=%s, confidence=%.2f", 
                    isNew ? "保存" : "更新", 
                    finalPreference.getCategory(), 
                    finalPreference.getValue(), 
                    finalPreference.getConfidence());

        } catch (Exception e) {
            log.error("学习用户偏好失败", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 学习偏好参数
     */
    public static class LearnPreferenceParams {
        @JsonProperty("category")
        public String category;

        @JsonProperty("value")
        public String value;

        @JsonProperty("context")
        public String context;

        @JsonProperty("confidence")
        public Double confidence;
    }

    @Override
    public String getToolName() {
        return "learn_user_preference";
    }

    @Override
    public String getDisplayName() {
        return "学习用户偏好";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("learn_user_preference", this)
                .description(DESCRIPTION)
                .inputType(LearnPreferenceParams.class)
                .build();
    }
}
