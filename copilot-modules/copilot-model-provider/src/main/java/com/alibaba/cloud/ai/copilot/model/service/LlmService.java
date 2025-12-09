package com.alibaba.cloud.ai.copilot.model.service;

import com.alibaba.cloud.ai.copilot.model.entity.LlmEntity;

import java.util.List;

/**
 * LLM模型服务接口
 */
public interface LlmService {

    /**
     * 获取所有LLM模型
     */
    List<LlmEntity> getAllModels();

    /**
     * 根据ID获取LLM模型
     */
    LlmEntity getById(Long id);

    /**
     * 根据厂商ID获取该厂商下的所有模型
     */
    List<LlmEntity> getModelsByFactoryId(String fid);

    /**
     * 根据厂商ID获取该厂商下启用的模型
     */
    List<LlmEntity> getEnabledModelsByFactoryId(String fid);

    /**
     * 保存或更新模型
     */
    boolean saveOrUpdate(LlmEntity entity);

    /**
     * 删除模型
     */
    boolean delete(Long id);

    /**
     * 更新模型状态
     */
    boolean updateStatus(Long id, String status);
}
