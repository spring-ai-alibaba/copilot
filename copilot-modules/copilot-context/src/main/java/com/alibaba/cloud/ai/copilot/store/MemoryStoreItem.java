package com.alibaba.cloud.ai.copilot.store;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆条目（本地 POJO，替代原 {@code com.alibaba.cloud.ai.graph.store.StoreItem}，彻底脱离 spring-ai-alibaba）。
 *
 * @param namespace 命名空间（多段）
 * @param key       键
 * @param value     值（JSON 对象）
 * @author better
 */
public record MemoryStoreItem(List<String> namespace, String key, Map<String, Object> value) {

    public static MemoryStoreItem of(List<String> namespace, String key, Map<String, Object> value) {
        return new MemoryStoreItem(namespace, key, value);
    }

    public List<String> getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    public Map<String, Object> getValue() {
        return value;
    }
}
