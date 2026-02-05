package com.alibaba.cloud.ai.copilot.controller.chat;

import com.alibaba.cloud.ai.copilot.domain.dto.*;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.store.DatabaseStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 长期记忆 API Controller
 *
 * @author better
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final DatabaseStore databaseStore;

    /**
     * 保存记忆
     */
    @PostMapping("/save")
    public ResponseEntity<MemoryResponse> saveMemory(@RequestBody SaveMemoryRequest request) {
        try {
            Long userId = LoginHelper.getUserId();
            
            // 验证命名空间
            if (request.getNamespace() == null || request.getNamespace().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: namespace 不能为空", null, null));
            }
            
            // 验证键
            if (request.getKey() == null || request.getKey().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: key 不能为空", null, null));
            }
            
            // 验证值
            if (request.getValue() == null) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: value 不能为空", null, null));
            }
            
            // 保存记忆
            StoreItem item = StoreItem.of(request.getNamespace(), request.getKey(), request.getValue());
            databaseStore.putItem(item);
            
            log.debug("保存记忆成功: userId={}, namespace={}, key={}", userId, request.getNamespace(), request.getKey());
            return ResponseEntity.ok(new MemoryResponse("成功保存记忆", request.getValue(), null));
            
        } catch (Exception e) {
            log.error("保存记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(new MemoryResponse("Error: " + e.getMessage(), null, null));
        }
    }

    /**
     * 获取记忆
     */
    @GetMapping("/get")
    public ResponseEntity<MemoryResponse> getMemory(
            @RequestParam("namespace") String namespaceStr,
            @RequestParam("key") String key) {
        try {
            Long userId = LoginHelper.getUserId();
            
            // 解析命名空间
            List<String> namespace = parseNamespace(namespaceStr);
            if (namespace == null || namespace.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: namespace 格式错误", null, null));
            }
            
            // 获取记忆
            Optional<StoreItem> itemOpt = databaseStore.getItem(namespace, key);
            
            if (itemOpt.isPresent()) {
                log.debug("获取记忆成功: userId={}, namespace={}, key={}", userId, namespace, key);
                return ResponseEntity.ok(new MemoryResponse("找到记忆", itemOpt.get().getValue(), null));
            } else {
                log.debug("未找到记忆: userId={}, namespace={}, key={}", userId, namespace, key);
                return ResponseEntity.ok(new MemoryResponse("未找到记忆", Map.of(), null));
            }
            
        } catch (Exception e) {
            log.error("获取记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(new MemoryResponse("Error: " + e.getMessage(), null, null));
        }
    }

    /**
     * 搜索记忆
     */
    @PostMapping("/search")
    public ResponseEntity<MemoryResponse> searchMemory(@RequestBody SearchMemoryRequest request) {
        try {
            Long userId = LoginHelper.getUserId();
            
            // 验证命名空间
            if (request.getNamespace() == null || request.getNamespace().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: namespace 不能为空", null, null));
            }
            
            // 搜索记忆（支持 filter: JSON_CONTAINS(value, filter)）
            List<StoreItem> items = databaseStore.searchItems(request.getNamespace(), request.getFilter());

            log.debug("搜索记忆成功: userId={}, namespace={}, count={}", 
                    userId, request.getNamespace(), items.size());
            
            // 转换为响应格式
            List<Map<String, Object>> itemsList = items.stream()
                    .map(item -> {
                        Map<String, Object> itemMap = new java.util.HashMap<>();
                        itemMap.put("namespace", item.getNamespace());
                        itemMap.put("key", item.getKey());
                        itemMap.put("value", item.getValue());
                        return itemMap;
                    })
                    .toList();
            
            return ResponseEntity.ok(new MemoryResponse("搜索完成", null, itemsList));
            
        } catch (Exception e) {
            log.error("搜索记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(new MemoryResponse("Error: " + e.getMessage(), null, null));
        }
    }

    /**
     * 删除记忆
     */
    @DeleteMapping("/delete")
    public ResponseEntity<MemoryResponse> deleteMemory(@RequestBody DeleteMemoryRequest request) {
        try {
            Long userId = LoginHelper.getUserId();
            
            // 验证命名空间
            if (request.getNamespace() == null || request.getNamespace().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: namespace 不能为空", null, null));
            }
            
            // 验证键
            if (request.getKey() == null || request.getKey().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MemoryResponse("Error: key 不能为空", null, null));
            }
            
            // 删除记忆
            databaseStore.deleteItem(request.getNamespace(), request.getKey());
            
            log.debug("删除记忆成功: userId={}, namespace={}, key={}", userId, request.getNamespace(), request.getKey());
            return ResponseEntity.ok(new MemoryResponse("成功删除记忆", null, null));
            
        } catch (Exception e) {
            log.error("删除记忆失败", e);
            return ResponseEntity.internalServerError()
                    .body(new MemoryResponse("Error: " + e.getMessage(), null, null));
        }
    }

    /**
     * 解析命名空间字符串（JSON数组格式）
     */
    private List<String> parseNamespace(String namespaceStr) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(namespaceStr, List.class);
        } catch (Exception e) {
            log.warn("解析命名空间失败: {}", namespaceStr, e);
            return null;
        }
    }
}
