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
import java.util.function.BiFunction;

/**
 * 搜索记忆工具
 * 允许 Agent 在命名空间内搜索相关记忆
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchMemoryTool implements BiFunction<SearchMemoryTool.SearchMemoryParams, ToolContext, String>, BuiltinToolProvider {

    private final DatabaseStore databaseStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String DESCRIPTION = "在命名空间内搜索相关记忆。\n" +
            "参数说明：\n" +
            "- namespace: 命名空间数组，如 [\"user_preferences\"]\n" +
            "- filter: 搜索过滤器，JSON 对象格式（可选）";

    @Override
    public String apply(SearchMemoryParams params, ToolContext toolContext) {
        try {
            // 验证参数
            if (params.namespace == null || params.namespace.isEmpty()) {
                return "Error: namespace 不能为空";
            }

            // 直接使用 DatabaseStore 搜索记忆（支持 filter: JSON_CONTAINS(value, filter)）
            List<StoreItem> items = databaseStore.searchItems(params.namespace, params.filter);

            if (items.isEmpty()) {
                log.debug("未找到记忆: namespace={}, filter={}", params.namespace, params.filter);
                return String.format("未找到记忆: namespace=%s", params.namespace);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("找到 %d 条记忆:\n", items.size()));
            for (StoreItem item : items) {
                String valueJson = objectMapper.writeValueAsString(item.getValue());
                result.append(String.format("- key: %s, value: %s\n", item.getKey(), valueJson));
            }

            log.debug("搜索记忆成功: namespace={}, filter={}, count={}", 
                    params.namespace, params.filter, items.size());
            return result.toString();

        } catch (Exception e) {
            log.error("搜索记忆失败", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 搜索记忆参数
     */
    public static class SearchMemoryParams {
        @JsonProperty("namespace")
        public List<String> namespace;

        @JsonProperty("filter")
        public Map<String, Object> filter;
    }

    @Override
    public String getToolName() {
        return "search_memory";
    }

    @Override
    public String getDisplayName() {
        return "搜索记忆";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return FunctionToolCallback.builder("search_memory", this)
                .description(DESCRIPTION)
                .inputType(SearchMemoryParams.class)
                .build();
    }
}
