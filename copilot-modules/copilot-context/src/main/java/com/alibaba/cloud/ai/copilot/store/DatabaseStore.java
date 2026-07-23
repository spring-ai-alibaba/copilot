package com.alibaba.cloud.ai.copilot.store;

import com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity;
import com.alibaba.cloud.ai.copilot.mapper.MemoryStoreMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于数据库的长期记忆存储实现（自有实现，不再依赖 spring-ai-alibaba 的 graph Store 接口）。
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseStore {

    private final MemoryStoreMapper memoryStoreMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void putItem(MemoryStoreItem item) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(item.getNamespace());
            String key = item.getKey();
            Map<String, Object> value = item.getValue();

            // 查询是否已存在
            MemoryStoreEntity existing = memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);

            if (existing != null) {
                // 更新
                existing.setValue(value);
                existing.setUpdatedTime(java.time.LocalDateTime.now());
                memoryStoreMapper.updateById(existing);
                log.debug("更新记忆: namespace={}, key={}", namespaceStr, key);
            } else {
                // 新增
                MemoryStoreEntity entity = new MemoryStoreEntity();
                entity.setNamespace(namespaceStr);
                entity.setKey(key);
                entity.setValue(value);
                // 提取 userId：优先从 namespace 中的 user_XXX，其次从 key= user_XXX（前端当前使用 key 存 userId）
                for (String ns : item.getNamespace()) {
                    if (ns.startsWith("user_")) {
                        try {
                            String userIdStr = ns.replace("user_", "");
                            entity.setUserId(Long.parseLong(userIdStr));
                            break;
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
                if (entity.getUserId() == null && key != null && key.startsWith("user_")) {
                    try {
                        entity.setUserId(Long.parseLong(key.substring("user_".length())));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                memoryStoreMapper.insert(entity);
                log.debug("保存记忆: namespace={}, key={}", namespaceStr, key);
            }
        } catch (JsonProcessingException e) {
            log.error("保存记忆失败: namespace={}, key={}", item.getNamespace(), item.getKey(), e);
            throw new RuntimeException("保存记忆失败", e);
        }
    }

    public Optional<MemoryStoreItem> getItem(List<String> namespace, String key) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            MemoryStoreEntity entity = memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);

            if (entity == null) {
                return Optional.empty();
            }

            MemoryStoreItem item = MemoryStoreItem.of(namespace, key, entity.getValue());
            return Optional.of(item);
        } catch (JsonProcessingException e) {
            log.error("获取记忆失败: namespace={}, key={}", namespace, key, e);
            return Optional.empty();
        }
    }

    public boolean deleteItem(List<String> namespace, String key) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            MemoryStoreEntity entity = memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);

            if (entity != null) {
                memoryStoreMapper.deleteById(entity.getId());
                log.debug("删除记忆: namespace={}, key={}", namespaceStr, key);
                return true;
            }
            return false;
        } catch (JsonProcessingException e) {
            log.error("删除记忆失败: namespace={}, key={}", namespace, key, e);
            return false;
        }
    }

    /**
     * 兼容前端 /api/memory/search 的 filter 语义：在指定 namespace 下按 JSON_CONTAINS(value, filter) 查询。
     */
    public List<MemoryStoreItem> searchItems(List<String> namespace, Map<String, Object> filter) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            List<MemoryStoreEntity> entities;

            if (filter != null && !filter.isEmpty()) {
                entities = memoryStoreMapper.searchByFilter(namespaceStr, filter);
            } else {
                entities = memoryStoreMapper.selectByNamespace(namespaceStr);
            }

            List<MemoryStoreItem> items = new ArrayList<>();
            for (MemoryStoreEntity entity : entities) {
                try {
                    List<String> ns = objectMapper.readValue(entity.getNamespace(), new TypeReference<List<String>>() {});
                    items.add(MemoryStoreItem.of(ns, entity.getKey(), entity.getValue()));
                } catch (JsonProcessingException e) {
                    log.error("解析命名空间失败: {}", entity.getNamespace(), e);
                }
            }
            return items;
        } catch (JsonProcessingException e) {
            log.error("搜索记忆失败: namespace={}, filter={}", namespace, filter, e);
            return List.of();
        }
    }
}
