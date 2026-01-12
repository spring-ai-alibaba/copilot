package com.alibaba.cloud.ai.copilot.model.service;

import com.alibaba.cloud.ai.copilot.model.domain.ModelConfig;
import com.alibaba.cloud.ai.copilot.model.dto.LlmServiceProvider;
import com.alibaba.cloud.ai.copilot.model.dto.ModelConfigResponse;
import com.alibaba.cloud.ai.copilot.model.entity.ModelConfigEntity;

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
     * 获取用户可见的模型配置实体
     * 包括：公开(PUBLIC)的模型 + 用户自己配置的模型(PRIVATE)
     * @param userId 用户ID
     * @return 用户可见的模型配置列表
     */
    List<ModelConfigEntity> getVisibleModelEntities(Long userId);

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

    /***
     * 判断当前用户配置了的模型
     * @return
     */
    List<LlmServiceProvider> getCurrentUserModels(Long userId);

    /**
     * 根据供应商code删除当前用户在该供应商下的所有模型配置
     * @param userId 用户ID
     * @param providerCode 供应商代码
     * @return 删除的记录数
     */
    int deleteModelsByProvider(Long userId, String providerCode);

    /**
     * 编辑用户已配置的模型
     * @param modelConfig 模型配置实体（需包含id）
     * @return 是否更新成功
     */
    boolean updateUserModelConfig(ModelConfigEntity modelConfig);

    /**
     * 获取当前用户的默认模型配置
     * <p>优先返回启用的、sortOrder 最小的模型配置</p>
     *
     * @param userId 用户ID
     * @return 默认模型配置，如果没有则返回 null
     */
    ModelConfigEntity getDefaultModelConfig(Long userId);
}
