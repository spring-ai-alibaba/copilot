package com.alibaba.cloud.ai.copilot.store;

import com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity;
import com.alibaba.cloud.ai.copilot.mapper.MemoryStoreMapper;
import com.alibaba.cloud.ai.graph.store.NamespaceListRequest;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.StoreSearchRequest;
import com.alibaba.cloud.ai.graph.store.StoreSearchResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * 基于数据库的长期记忆存储实现
 *
 * @author better
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseStore implements Store {

    private final MemoryStoreMapper memoryStoreMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void putItem(StoreItem item) {
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

    @Override
    public Optional<StoreItem> getItem(List<String> namespace, String key) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            MemoryStoreEntity entity = memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);

            if (entity == null) {
                return Optional.empty();
            }

            StoreItem item = StoreItem.of(namespace, key, entity.getValue());
            return Optional.of(item);
        } catch (JsonProcessingException e) {
            log.error("获取记忆失败: namespace={}, key={}", namespace, key, e);
            return Optional.empty();
        }
    }

    @Override
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

    @Override
    public StoreSearchResult searchItems(StoreSearchRequest searchRequest) {
        try {
            List<String> namespace = searchRequest.getNamespace() != null 
                    ? searchRequest.getNamespace() 
                    : List.of();
            String namespaceStr = namespace.isEmpty() 
                    ? null 
                    : objectMapper.writeValueAsString(namespace);

            // 构建查询条件
            LambdaQueryWrapper<MemoryStoreEntity> queryWrapper = new LambdaQueryWrapper<>();
            if (namespaceStr != null) {
                queryWrapper.eq(MemoryStoreEntity::getNamespace, namespaceStr);
            }
            String query = searchRequest.getQuery();
            if (query != null && !query.isEmpty()) {
                // 简单的关键词搜索：在 key 中搜索
                queryWrapper.like(MemoryStoreEntity::getKey, query);
            }

            // 分页
            Integer offsetInt = searchRequest.getOffset();
            Integer limitInt = searchRequest.getLimit();
            int offset = offsetInt != null ? offsetInt : 0;
            int limit = limitInt != null ? limitInt : 100;
            queryWrapper.last("LIMIT " + limit + " OFFSET " + offset);

            // 执行查询
            List<MemoryStoreEntity> entities = memoryStoreMapper.selectList(queryWrapper);

            // 转换为 StoreItem
            List<StoreItem> items = entities.stream()
                    .map(entity -> {
                        try {
                            List<String> ns = objectMapper.readValue(
                                    entity.getNamespace(), 
                                    new TypeReference<List<String>>() {}
                            );
                            return StoreItem.of(ns, entity.getKey(), entity.getValue());
                        } catch (JsonProcessingException e) {
                            log.error("解析命名空间失败: {}", entity.getNamespace(), e);
                            return null;
                        }
                    })
                    .filter(item -> item != null)
                    .collect(Collectors.toList());

            // 计算总数（用于分页）
            long totalCount = memoryStoreMapper.selectCount(
                    new LambdaQueryWrapper<MemoryStoreEntity>()
                            .eq(namespaceStr != null, MemoryStoreEntity::getNamespace, namespaceStr)
                            .like(query != null && !query.isEmpty(), 
                                    MemoryStoreEntity::getKey, query)
            );

            return StoreSearchResult.of(items, totalCount, offset, limit);

        } catch (Exception e) {
            log.error("搜索记忆失败: request={}", searchRequest, e);
            return StoreSearchResult.empty();
        }
    }

    /**
     * 兼容前端 /api/memory/search 的 filter 语义：在指定 namespace 下按 JSON_CONTAINS(value, filter) 查询。
     * 这是 DatabaseStore 的便捷重载方法（不属于 Store 接口），用于 Controller/Tool 直接调用。
     */
    public List<StoreItem> searchItems(List<String> namespace, Map<String, Object> filter) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            List<MemoryStoreEntity> entities;

            if (filter != null && !filter.isEmpty()) {
                entities = memoryStoreMapper.searchByFilter(namespaceStr, filter);
            } else {
                entities = memoryStoreMapper.selectByNamespace(namespaceStr);
            }

            List<StoreItem> items = new ArrayList<>();
            for (MemoryStoreEntity entity : entities) {
                try {
                    List<String> ns = objectMapper.readValue(entity.getNamespace(), new TypeReference<List<String>>() {});
                    items.add(StoreItem.of(ns, entity.getKey(), entity.getValue()));
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

    @Override
    public List<String> listNamespaces(NamespaceListRequest namespaceRequest) {
        try {
            // 查询所有不同的命名空间
            List<MemoryStoreEntity> entities = memoryStoreMapper.selectList(
                    new LambdaQueryWrapper<MemoryStoreEntity>()
                            .select(MemoryStoreEntity::getNamespace)
                            .groupBy(MemoryStoreEntity::getNamespace)
            );

            return entities.stream()
                    .map(entity -> {
                        try {
                            List<String> ns = objectMapper.readValue(
                                    entity.getNamespace(),
                                    new TypeReference<List<String>>() {}
                            );
                            return String.join("/", ns);
                        } catch (JsonProcessingException e) {
                            log.error("解析命名空间失败: {}", entity.getNamespace(), e);
                            return null;
                        }
                    })
                    .filter(ns -> ns != null)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("列出命名空间失败: request={}", namespaceRequest, e);
            return List.of();
        }
    }

    @Override
    public void clear() {
        try {
            memoryStoreMapper.delete(new LambdaQueryWrapper<>());
            log.warn("已清空所有记忆数据");
        } catch (Exception e) {
            log.error("清空记忆失败", e);
            throw new RuntimeException("清空记忆失败", e);
        }
    }

    @Override
    public long size() {
        try {
            return memoryStoreMapper.selectCount(new LambdaQueryWrapper<>());
        } catch (Exception e) {
            log.error("获取记忆数量失败", e);
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }
}
