package com.alibaba.cloud.ai.copilot.rag.service.impl;

import com.alibaba.cloud.ai.copilot.rag.entity.KnowledgeBaseEntity;
import com.alibaba.cloud.ai.copilot.rag.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库服务实现类（简化版本）
 * 用于第一阶段的基本功能实现
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    
    // 临时存储，实际项目中应该使用数据库
    private final Map<String, KnowledgeBaseEntity> knowledgeBaseCache = new ConcurrentHashMap<>();
    private Long idCounter = 1L;
    
    public KnowledgeBaseServiceImpl() {
        // 初始化一个默认的知识库用于测试
        createDefaultKnowledgeBase();
    }
    
    @Override
    public KnowledgeBaseEntity createKnowledgeBase(KnowledgeBaseEntity knowledgeBase) {
        knowledgeBase.setId(idCounter++);
        knowledgeBase.setCreatedTime(LocalDateTime.now());
        knowledgeBase.setUpdatedTime(LocalDateTime.now());
        knowledgeBase.setEnabled(true);
        
        knowledgeBaseCache.put(knowledgeBase.getKbKey(), knowledgeBase);
        
        log.info("创建知识库: {} - {}", knowledgeBase.getKbKey(), knowledgeBase.getKbName());
        return knowledgeBase;
    }
    
    @Override
    public KnowledgeBaseEntity updateKnowledgeBase(KnowledgeBaseEntity knowledgeBase) {
        KnowledgeBaseEntity existing = knowledgeBaseCache.get(knowledgeBase.getKbKey());
        if (existing != null) {
            knowledgeBase.setId(existing.getId());
            knowledgeBase.setCreatedTime(existing.getCreatedTime());
            knowledgeBase.setUpdatedTime(LocalDateTime.now());
            knowledgeBaseCache.put(knowledgeBase.getKbKey(), knowledgeBase);
            log.info("更新知识库: {} - {}", knowledgeBase.getKbKey(), knowledgeBase.getKbName());
            return knowledgeBase;
        }
        return null;
    }
    
    @Override
    public boolean deleteKnowledgeBase(Long id) {
        KnowledgeBaseEntity toDelete = null;
        for (KnowledgeBaseEntity kb : knowledgeBaseCache.values()) {
            if (kb.getId().equals(id)) {
                toDelete = kb;
                break;
            }
        }
        
        if (toDelete != null) {
            knowledgeBaseCache.remove(toDelete.getKbKey());
            log.info("删除知识库: {} - {}", toDelete.getKbKey(), toDelete.getKbName());
            return true;
        }
        return false;
    }
    
    @Override
    public KnowledgeBaseEntity getKnowledgeBaseById(Long id) {
        return knowledgeBaseCache.values().stream()
                .filter(kb -> kb.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public KnowledgeBaseEntity getKnowledgeBaseByKey(String kbKey) {
        return knowledgeBaseCache.get(kbKey);
    }
    
    @Override
    public List<KnowledgeBaseEntity> getAllEnabledKnowledgeBases() {
        return knowledgeBaseCache.values().stream()
                .filter(KnowledgeBaseEntity::getEnabled)
                .toList();
    }
    
    @Override
    public List<KnowledgeBaseEntity> getKnowledgeBasesByCreatedBy(String createdBy) {
        return knowledgeBaseCache.values().stream()
                .filter(kb -> createdBy.equals(kb.getCreatedBy()))
                .toList();
    }
    
    @Override
    public List<KnowledgeBaseEntity> getKnowledgeBasesPage(int page, int size, String keyword) {
        // 简化实现，实际项目中应该使用分页查询
        List<KnowledgeBaseEntity> all = new ArrayList<>(knowledgeBaseCache.values());
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            all = all.stream()
                    .filter(kb -> kb.getKbName().contains(keyword) || kb.getDescription().contains(keyword))
                    .toList();
        }
        
        int start = page * size;
        int end = Math.min(start + size, all.size());
        
        if (start >= all.size()) {
            return new ArrayList<>();
        }
        
        return all.subList(start, end);
    }
    
    @Override
    public boolean toggleKnowledgeBaseStatus(Long id, boolean enabled) {
        KnowledgeBaseEntity kb = getKnowledgeBaseById(id);
        if (kb != null) {
            kb.setEnabled(enabled);
            kb.setUpdatedTime(LocalDateTime.now());
            log.info("切换知识库状态: {} - {}", kb.getKbKey(), enabled ? "启用" : "禁用");
            return true;
        }
        return false;
    }
    
    @Override
    public boolean isKbKeyExists(String kbKey, Long excludeId) {
        KnowledgeBaseEntity existing = knowledgeBaseCache.get(kbKey);
        if (existing == null) {
            return false;
        }
        
        if (excludeId != null && existing.getId().equals(excludeId)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 创建默认知识库用于测试
     */
    private void createDefaultKnowledgeBase() {
        KnowledgeBaseEntity defaultKb = new KnowledgeBaseEntity();
        defaultKb.setKbName("默认知识库");
        defaultKb.setKbKey("default");
        defaultKb.setDescription("用于测试的默认知识库");
        defaultKb.setEmbeddingModel("text-embedding-ada-002");
        defaultKb.setChunkSize(500);
        defaultKb.setChunkOverlap(50);
        defaultKb.setCreatedBy("system");
        
        createKnowledgeBase(defaultKb);
        
        log.info("已创建默认知识库: default");
    }
}
