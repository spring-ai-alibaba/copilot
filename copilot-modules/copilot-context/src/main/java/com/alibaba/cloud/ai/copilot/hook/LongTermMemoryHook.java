package com.alibaba.cloud.ai.copilot.hook;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.store.PreferenceDeduplicator;
import com.alibaba.cloud.ai.copilot.store.PreferenceInfo;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 长期记忆 Hook
 * 在模型调用前后自动加载和保存长期记忆
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LongTermMemoryHook extends ModelHook {

    private final AppProperties appProperties;
    private final DynamicModelService dynamicModelService;
    private final ModelConfigService modelConfigService;
    private final PreferenceDeduplicator preferenceDeduplicator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "long_term_memory_hook";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[]{HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // 检查是否启用长期记忆
        if (!appProperties.getMemory().isEnabled()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 检查是否启用偏好（会话级开关）
        boolean enablePreferences = config.metadata("enable_preferences")
                .map(v -> Boolean.parseBoolean(v.toString()))
                .orElse(true); // 默认启用

        if (!enablePreferences) {
            log.debug("会话偏好已禁用，跳过加载用户画像");
            return CompletableFuture.completedFuture(Map.of());
        }

        // 从 config 的 metadata 中获取 user_id
        String userId = null;
        Optional<Object> userIdOpt = config.metadata("user_id");
        if (userIdOpt.isPresent()) {
            userId = userIdOpt.get().toString();
        }

        if (userId == null || userId.isEmpty()) {
            log.debug("未找到 user_id，跳过加载用户画像");
            return CompletableFuture.completedFuture(Map.of());
        }

        try {
            Store store = config.store();
            if (store == null) {
                log.debug("Store 未配置，跳过加载用户画像");
                return CompletableFuture.completedFuture(Map.of());
            }

            // 加载用户画像
            Optional<StoreItem> profileOpt = store.getItem(List.of("user_profiles"), "user_" + userId);
            Map<String, Object> profile = null;
            if (profileOpt.isPresent()) {
                profile = profileOpt.get().getValue();
            }

            // 加载用户偏好（只加载启用的偏好）
            Optional<StoreItem> preferencesOpt = store.getItem(List.of("user_preferences"), "user_" + userId);
            List<Map<String, Object>> enabledPreferences = new ArrayList<>();
            if (preferencesOpt.isPresent()) {
                Map<String, Object> prefsData = preferencesOpt.get().getValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allPrefs = (List<Map<String, Object>>) prefsData.get("items");
                if (allPrefs != null) {
                    for (Map<String, Object> pref : allPrefs) {
                        Boolean enabled = pref.get("enabled") instanceof Boolean
                                ? (Boolean) pref.get("enabled")
                                : pref.get("enabled") != null && Boolean.parseBoolean(pref.get("enabled").toString());
                        if (enabled != null && enabled) {
                            enabledPreferences.add(pref);
                        }
                    }
                }
            }

            // 构建用户上下文信息
            StringBuilder contextBuilder = new StringBuilder();

            if (profile != null) {
                String name = (String) profile.get("name");
                String language = (String) profile.get("language");
                if (name != null && !name.isEmpty()) {
                    contextBuilder.append("用户姓名：").append(name).append("\n");
                }
                if (language != null && !language.isEmpty()) {
                    contextBuilder.append("用户语言：").append(language).append("\n");
                }
            }

            if (!enabledPreferences.isEmpty()) {
                contextBuilder.append("用户偏好：\n");
                for (Map<String, Object> pref : enabledPreferences) {
                    String category = (String) pref.get("category");
                    String value = (String) pref.get("value");
                    if (category != null && value != null) {
                        contextBuilder.append("- ").append(category).append(": ").append(value).append("\n");
                    }
                }
            }

            if (contextBuilder.length() > 0) {
                // 获取消息列表
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) state.value("messages").orElse(new ArrayList<>());
                List<Message> newMessages = new ArrayList<>();

                // 查找是否已存在 SystemMessage
                SystemMessage existingSystemMessage = null;
                int systemMessageIndex = -1;
                for (int i = 0; i < messages.size(); i++) {
                    Message msg = messages.get(i);
                    if (msg instanceof SystemMessage) {
                        existingSystemMessage = (SystemMessage) msg;
                        systemMessageIndex = i;
                        break;
                    }
                }

                // 构建增强的系统消息
                String userContext = contextBuilder.toString().trim();
                SystemMessage enhancedSystemMessage;
                if (existingSystemMessage != null) {
                    // 更新现有的 SystemMessage
                    enhancedSystemMessage = new SystemMessage(
                            existingSystemMessage.getText() + "\n\n" + userContext
                    );
                } else {
                    // 创建新的 SystemMessage
                    enhancedSystemMessage = new SystemMessage(userContext);
                }

                // 构建新的消息列表
                if (systemMessageIndex >= 0) {
                    // 如果找到了 SystemMessage，替换它
                    for (int i = 0; i < messages.size(); i++) {
                        if (i == systemMessageIndex) {
                            newMessages.add(enhancedSystemMessage);
                        } else {
                            newMessages.add(messages.get(i));
                        }
                    }
                } else {
                    // 如果没有找到 SystemMessage，在开头添加新的
                    newMessages.add(enhancedSystemMessage);
                    newMessages.addAll(messages);
                }

                log.debug("加载用户画像和偏好: userId={}, preferencesCount={}", userId, enabledPreferences.size());
                return CompletableFuture.completedFuture(Map.of("messages", newMessages));
            }
        } catch (Exception e) {
            log.error("加载用户画像失败: userId={}", userId, e);
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        // 检查是否启用长期记忆
        if (!appProperties.getMemory().isEnabled()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 检查全局偏好学习开关
        if (!appProperties.getMemory().isPreferenceLearningEnabled()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        // 检查会话级偏好学习开关
        boolean enablePreferenceLearning = config.metadata("enable_preference_learning")
                .map(v -> Boolean.parseBoolean(v.toString()))
                .orElse(true); // 默认启用

        if (!enablePreferenceLearning) {
            log.debug("会话偏好学习已禁用");
            return CompletableFuture.completedFuture(Map.of());
        }

        // 从 config 的 metadata 中获取 user_id
        String userId = null;
        Optional<Object> userIdOpt = config.metadata("user_id");
        if (userIdOpt.isPresent()) {
            userId = userIdOpt.get().toString();
        }

        if (userId == null || userId.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        try {
            Store store = config.store();
            if (store == null) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 检查用户级别的偏好学习开关
            Optional<StoreItem> profileOpt = store.getItem(List.of("user_profiles"), "user_" + userId);
            if (profileOpt.isPresent()) {
                Map<String, Object> profile = profileOpt.get().getValue();
                Object enableLearningObj = profile.get("enablePreferenceLearning");
                if (enableLearningObj != null) {
                    boolean userEnableLearning = enableLearningObj instanceof Boolean
                            ? (Boolean) enableLearningObj
                            : Boolean.parseBoolean(enableLearningObj.toString());
                    if (!userEnableLearning) {
                        log.debug("用户已关闭偏好学习: userId={}", userId);
                        return CompletableFuture.completedFuture(Map.of());
                    }
                }
            }

            // hybrid（少误记优先）兜底学习：仅在“强触发句式”命中时，提取 0~1 条偏好。
            // 注意：兜底写入默认 enabled=false，避免误记直接影响模型行为，用户可在前端手动启用。
            @SuppressWarnings("unchecked")
            List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
            String lastUserText = extractLastUserText(messages);
            if (lastUserText == null || lastUserText.isBlank()) {
                return CompletableFuture.completedFuture(Map.of());
            }

            // 兜底学习（少漏记优先）：对大多数用户输入都尝试一次 LLM 结构化判定，由模型决定是否 should_learn。
            // 仍然保留 shouldFallbackLearn 的最小过滤（过短/空白），避免无意义调用。
            if (!shouldFallbackLearn(lastUserText)) {
                return CompletableFuture.completedFuture(Map.of());
            }

            Optional<PreferenceInfo> extractedOpt = extractPreferenceByLlm(config, lastUserText);
            if (extractedOpt.isEmpty()) {
                return CompletableFuture.completedFuture(Map.of());
            }

            PreferenceInfo extracted = extractedOpt.get();
            double minConfidence = appProperties.getMemory().getMinConfidence();
            double confidence = extracted.getConfidence() != null ? extracted.getConfidence() : 0.0;
            if (confidence < Math.max(minConfidence, 0.80)) {
                // 少漏记优先：放宽阈值，但仍保留最小置信度约束
                return CompletableFuture.completedFuture(Map.of());
            }

            // 加载现有偏好
            List<String> namespace = List.of("user_preferences");
            String key = "user_" + userId;
            List<PreferenceInfo> existingPreferences = new ArrayList<>();

            Optional<StoreItem> existingItemOpt = store.getItem(namespace, key);
            if (existingItemOpt.isPresent()) {
                Map<String, Object> prefsData = existingItemOpt.get().getValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) prefsData.get("items");
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        existingPreferences.add(PreferenceInfo.fromMap(item));
                    }
                }
            }

            // 去重合并
            PreferenceInfo finalPreference = preferenceDeduplicator.deduplicate(extracted, existingPreferences);

            boolean isNew = existingPreferences.stream()
                    .noneMatch(p -> safeEq(p.getCategory(), finalPreference.getCategory())
                            && safeEq(p.getValue(), finalPreference.getValue()));

            if (!isNew) {
                existingPreferences.replaceAll(p -> safeEq(p.getCategory(), finalPreference.getCategory())
                        && safeEq(p.getValue(), finalPreference.getValue()) ? finalPreference : p);
            } else {
                existingPreferences.add(finalPreference);
            }

            List<Map<String, Object>> itemsList = new ArrayList<>();
            for (PreferenceInfo pref : existingPreferences) {
                itemsList.add(pref.toMap());
            }
            Map<String, Object> prefsData = Map.of("items", itemsList);
            store.putItem(StoreItem.of(namespace, key, prefsData));

            log.info("兜底学习用户偏好(默认禁用): userId={}, category={}, value={}, confidence={}, isNew={}",
                    userId, finalPreference.getCategory(), finalPreference.getValue(),
                    finalPreference.getConfidence(), isNew);

        } catch (Exception e) {
            log.error("偏好学习处理失败: userId={}", userId, e);
        }

        return CompletableFuture.completedFuture(Map.of());
    }

    private static boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static String extractLastUserText(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }

    private static boolean shouldFallbackLearn(String text) {
        String t = text.trim();
        // 少漏记优先：仅做“极小过滤”避免无意义调用
        return !t.isEmpty() && t.length() >= 2;
    }

    /**
     * 使用 LLM 做结构化抽取（少误记优先）：最多抽取 1 条偏好。
     * 仅在 shouldFallbackLearn(text) 命中后调用，降低额外调用次数。
     */
    private Optional<PreferenceInfo> extractPreferenceByLlm(RunnableConfig config, String userText) {
        try {
            ChatModel chatModel = getFallbackExtractionModel(config);
            if (chatModel == null) {
                return Optional.empty();
            }

            String instruction = """
你是“用户偏好抽取器（Preference Extractor）”。任务：从【单条用户输入】中抽取可能对后续对话有帮助的“可复用偏好/习惯/默认设置”，并输出严格 JSON（不允许任何解释、Markdown、代码块）。

策略（少漏记优先）：
- 宁可多判断一次，也不要轻易漏掉潜在偏好；但要把“不确定”反映到 confidence 上。
- 如果用户表达的是一次性任务约束、临时要求、或与个人偏好无关，则 should_learn=false。
- 最多输出 1 条偏好（选最重要/最可复用的那条）。

字段要求：
- 只能输出 1 个 JSON 对象。
- category 必须是以下之一：
  programming_language | framework_preference | tool_preference | coding_style | response_style | language_preference | other
- value：偏好值，简短明确（<=40字），不要包含解释。
- confidence：0.0~1.0。
  - 0.90~1.00：非常明确的长期偏好（强烈推荐 should_learn=true）
  - 0.80~0.89：比较像偏好但略缺上下文（可 should_learn=true）
  - <0.80：不建议学习（should_learn=false 或降低 confidence）
- reason：可选，<=30字，说明为何判断为偏好/为何不学习。

必须输出以下 JSON 格式（字段齐全）：
{
  "should_learn": true|false,
  "category": "other",
  "value": "",
  "confidence": 0.0,
  "reason": ""
}
""";

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(instruction),
                    new UserMessage("用户输入：\n" + userText)
            ));

            ChatResponse response = chatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }

            // 容错：截取第一个 JSON 对象
            String json = extractFirstJsonObject(text);
            if (json == null) {
                return Optional.empty();
            }

            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            boolean shouldLearn = map.get("should_learn") instanceof Boolean b ? b
                    : map.get("should_learn") != null && Boolean.parseBoolean(map.get("should_learn").toString());
            if (!shouldLearn) {
                return Optional.empty();
            }

            String category = map.get("category") != null ? map.get("category").toString().trim() : "";
            String value = map.get("value") != null ? map.get("value").toString().trim() : "";
            Double confidence = null;
            if (map.get("confidence") instanceof Number n) {
                confidence = n.doubleValue();
            } else if (map.get("confidence") != null) {
                try {
                    confidence = Double.parseDouble(map.get("confidence").toString());
                } catch (Exception ignore) {
                }
            }

            if (category.isEmpty() || value.isEmpty()) {
                return Optional.empty();
            }
            if (value.length() > 40) {
                return Optional.empty();
            }

            return Optional.of(PreferenceInfo.builder()
                    .category(category)
                    .value(value)
                    .context(userText)
                    .confidence(confidence != null ? confidence : 0.0)
                    .learnedAt(LocalDateTime.now())
                    .usageCount(1)
                    .source("post_process")
                    .enabled(false) // 少误记优先：默认禁用，需用户确认启用
                    .build());
        } catch (Exception e) {
            log.warn("LLM 结构化抽取偏好失败（忽略兜底学习）：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 兜底抽取使用的模型：优先使用会话携带的 model_config_id，其次使用系统默认启用模型的最小 sortOrder。
     */
    private ChatModel getFallbackExtractionModel(RunnableConfig config) {
        // 1) 尝试从 metadata 读取（如果你后续愿意，我们也可以在 ChatServiceImpl 里补充写入）
        Optional<Object> modelConfigIdOpt = config.metadata("model_config_id");
        if (modelConfigIdOpt.isPresent()) {
            String id = modelConfigIdOpt.get().toString();
            try {
                return dynamicModelService.getChatModelWithConfigId(id);
            } catch (Exception e) {
                log.debug("使用 metadata.model_config_id 获取模型失败，id={}, err={}", id, e.getMessage());
            }
        }

        // 2) 使用默认启用模型（尽量复用系统已有配置）
        try {
            ModelConfigEntity defaultModel = modelConfigService.getAllModelEntities().stream()
                    .filter(m -> m.getEnabled() != null && m.getEnabled())
                    .sorted((a, b) -> {
                        if (a.getSortOrder() != null && b.getSortOrder() != null) {
                            return a.getSortOrder().compareTo(b.getSortOrder());
                        } else if (a.getSortOrder() != null) {
                            return -1;
                        } else if (b.getSortOrder() != null) {
                            return 1;
                        }
                        return Long.compare(a.getId(), b.getId());
                    })
                    .findFirst()
                    .orElse(null);
            if (defaultModel == null) {
                return null;
            }
            return dynamicModelService.getChatModelWithConfigId(String.valueOf(defaultModel.getId()));
        } catch (Exception e) {
            log.warn("获取兜底抽取模型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从任意文本中截取第一个 JSON 对象（简单容错）。
     */
    private static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) {
                return text.substring(start, i + 1).trim();
            }
        }
        return null;
    }
}
