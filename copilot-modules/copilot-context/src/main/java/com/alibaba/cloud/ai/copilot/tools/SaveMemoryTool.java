package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolProvider;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 保存记忆工具
 * 允许 Agent 显式保存记忆到长期存储
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaveMemoryTool implements BiFunction<SaveMemoryTool.SaveMemoryParams, ToolContext, String>, BuiltinToolProvider {

    private final DatabaseStore databaseStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String DESCRIPTION = "保存记忆到长期存储。用于保存用户偏好、项目上下文等信息。\n" +
            "参数说明：\n" +
            "- namespace: 命名空间数组，如 [\"user_preferences\", \"user_123\"]\n" +
            "- key: 记忆键，用于标识该记忆\n" +
            "- value: 记忆值，JSON 对象格式";

    @Override
    public String apply(SaveMemoryParams params, ToolContext toolContext) {
        try {
            // 验证参数
            if (params.namespace == null || params.namespace.isEmpty()) {
                return "Error: namespace 不能为空";
            }
            if (params.key == null || params.key.trim().isEmpty()) {
                return "Error: key 不能为空";
            }
            if (params.value == null) {
                return "Error: value 不能为空";
            }

            // 直接使用 DatabaseStore 保存记忆
            StoreItem item = StoreItem.of(params.namespace, params.key, params.value);
            databaseStore.putItem(item);

            log.debug("保存记忆成功: namespace={}, key={}", params.namespace, params.key);
            return String.format("成功保存记忆: namespace=%s, key=%s", params.namespace, params.key);

        } catch (Exception e) {
            log.error("保存记忆失败", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 保存记忆参数
     */
    public static class SaveMemoryParams {
        @JsonProperty("namespace")
        public List<String> namespace;

        @JsonProperty("key")
        public String key;

        @JsonProperty("value")
        public Map<String, Object> value;
    }

    @Override
    public String getToolName() {
        return "save_memory";
    }

    @Override
    public String getDisplayName() {
        return "保存记忆";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("save_memory", this)
                .description(DESCRIPTION)
                .inputType(SaveMemoryParams.class)
                .build();
    }
}
