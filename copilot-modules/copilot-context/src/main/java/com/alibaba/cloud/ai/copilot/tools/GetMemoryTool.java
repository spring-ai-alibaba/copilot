package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolProvider;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
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
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 获取记忆工具
 * 允许 Agent 从长期存储中检索记忆
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMemoryTool implements BiFunction<GetMemoryTool.GetMemoryParams, ToolContext, String>, BuiltinToolProvider {

    private final DatabaseStore databaseStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String DESCRIPTION = "从长期存储中获取记忆。\n" +
            "参数说明：\n" +
            "- namespace: 命名空间数组，如 [\"user_preferences\", \"user_123\"]\n" +
            "- key: 记忆键";

    @Override
    public String apply(GetMemoryParams params, ToolContext toolContext) {
        try {
            // 验证参数
            if (params.namespace == null || params.namespace.isEmpty()) {
                return "Error: namespace 不能为空";
            }
            if (params.key == null || params.key.trim().isEmpty()) {
                return "Error: key 不能为空";
            }

            // 直接使用 DatabaseStore 获取记忆
            Optional<StoreItem> itemOpt = databaseStore.getItem(params.namespace, params.key);

            if (itemOpt.isPresent()) {
                StoreItem item = itemOpt.get();
                String valueJson = objectMapper.writeValueAsString(item.getValue());
                log.debug("获取记忆成功: namespace={}, key={}", params.namespace, params.key);
                return String.format("找到记忆: namespace=%s, key=%s\n值: %s", 
                        params.namespace, params.key, valueJson);
            } else {
                log.debug("未找到记忆: namespace={}, key={}", params.namespace, params.key);
                return String.format("未找到记忆: namespace=%s, key=%s", params.namespace, params.key);
            }

        } catch (Exception e) {
            log.error("获取记忆失败", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 获取记忆参数
     */
    public static class GetMemoryParams {
        @JsonProperty("namespace")
        public List<String> namespace;

        @JsonProperty("key")
        public String key;
    }

    @Override
    public String getToolName() {
        return "get_memory";
    }

    @Override
    public String getDisplayName() {
        return "获取记忆";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("get_memory", this)
                .description(DESCRIPTION)
                .inputType(GetMemoryParams.class)
                .build();
    }
}
