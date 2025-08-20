package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.context.domain.ModelConfig;
import com.alibaba.cloud.ai.copilot.dto.ModelConfigResponse;
import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置服务接口
 */
public interface ModelConfigService {

    /**
     * 获取所有启用的模型配置响应
     */
    List<ModelConfigResponse> getModelConfigResponses();

    /**
     * 获取所有启用的模型配置
     */
    List<ModelConfig> getEnabledModelConfigs();

    /**
     * 根据模型键查找模型配置
     */
    Optional<ModelConfig> findByModelKey(String modelKey);

    /**
     * 获取所有模型配置实体
     */
    List<ModelConfigEntity> getAllModelEntities();

    /**
     * 根据ID获取模型配置实体
     */
    ModelConfigEntity getModelEntityById(Long id);

    /**
     * 保存或更新模型配置
     */
    boolean saveOrUpdateModel(ModelConfigEntity modelEntity);

    /**
     * 删除模型配置
     */
    boolean deleteModel(Long id);

    /**
     * 启用/禁用模型
     */
    boolean toggleModelStatus(Long id, Boolean enabled);

    /**
     * 根据模型名称或模型键获取模型配置实体
     */
    ModelConfigEntity getModelEntityByName(String modelName);
}
