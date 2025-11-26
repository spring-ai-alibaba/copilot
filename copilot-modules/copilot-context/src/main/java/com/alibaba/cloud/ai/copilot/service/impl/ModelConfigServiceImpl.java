package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.context.domain.ModelConfig;
import com.alibaba.cloud.ai.copilot.dto.LlmServiceProvider;
import com.alibaba.cloud.ai.copilot.dto.ModelConfigResponse;
import com.alibaba.cloud.ai.copilot.entity.LlmFactoriesEntity;
import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.LlmFactoriesMapper;
import com.alibaba.cloud.ai.copilot.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型配置服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;

    private final LlmFactoriesMapper llmFactoriesMapper;

    @Override
    public List<ModelConfigResponse> getModelConfigResponses() {
        List<ModelConfigEntity> entities = modelConfigMapper.selectEnabledModels();
        return entities.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelConfig> getEnabledModelConfigs() {
        List<ModelConfigEntity> entities = modelConfigMapper.selectEnabledModels();
        return entities.stream()
                .map(this::convertToModelConfig)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ModelConfig> findByModelKey(String modelKey) {
        ModelConfigEntity entity = modelConfigMapper.selectByModelKey(modelKey);
        if (entity != null) {
            return Optional.of(convertToModelConfig(entity));
        }
        return Optional.empty();
    }

    @Override
    public List<ModelConfigEntity> getAllModelEntities() {
        return modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigEntity>()
                        .orderByAsc(ModelConfigEntity::getSortOrder)
                        .orderByAsc(ModelConfigEntity::getId)
        );
    }

    @Override
    public ModelConfigEntity getModelEntityById(Long id) {
        return modelConfigMapper.selectById(id);
    }

    @Override
    public boolean saveOrUpdateModel(ModelConfigEntity modelEntity) {
        try {
            if (modelEntity.getId() == null) {
                return modelConfigMapper.insert(modelEntity) > 0;
            } else {
                return modelConfigMapper.updateById(modelEntity) > 0;
            }
        } catch (Exception e) {
            log.error("保存或更新模型配置失败", e);
            return false;
        }
    }

    @Override
    public boolean deleteModel(Long id) {
        try {
            return modelConfigMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.error("删除模型配置失败", e);
            return false;
        }
    }

    @Override
    public boolean toggleModelStatus(Long id, Boolean enabled) {
        try {
            LambdaUpdateWrapper<ModelConfigEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ModelConfigEntity::getId, id)
                    .set(ModelConfigEntity::getEnabled, enabled);
            return modelConfigMapper.update(null, updateWrapper) > 0;
        } catch (Exception e) {
            log.error("切换模型状态失败", e);
            return false;
        }
    }

    @Override
    public ModelConfigEntity getModelEntityByName(String modelName) {
        LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(ModelConfigEntity::getModelName, modelName)
                .or()
                .eq(ModelConfigEntity::getModelKey, modelName)
        ).orderByAsc(ModelConfigEntity::getSortOrder)
         .orderByAsc(ModelConfigEntity::getId)
         .last("LIMIT 1"); // 只返回第一条记录

        return modelConfigMapper.selectOne(queryWrapper);
    }

    @Override
    public List<LlmServiceProvider> getCurrentUserModels(Long userId) {
        // 1. 查询用户所有模型配置
        List<ModelConfigEntity> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getUserId, userId)
        );

        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 按 provider 分组
        Map<String, List<ModelConfigEntity>> groupedByProvider = configs.stream()
                .collect(Collectors.groupingBy(ModelConfigEntity::getProvider));

        // 3. 为每个 provider 构建 LlmServiceProvider
        List<LlmServiceProvider> result = new ArrayList<>();
        for (Map.Entry<String, List<ModelConfigEntity>> entry : groupedByProvider.entrySet()) {
            String providerId = entry.getKey();
            List<ModelConfigEntity> providerConfigs = entry.getValue();

            // 查询该 provider 的工厂信息，获取 metadata（字符串）
            LlmFactoriesEntity factory = llmFactoriesMapper.selectByFactoriesName(providerId);
            String metadata = (factory != null) ? factory.getTags() : "";

            // 转换模型列表
            List<LlmServiceProvider.LlmModel> models = providerConfigs.stream().map(config ->
                    new LlmServiceProvider.LlmModel(
                            String.valueOf(config.getId()),
                            Optional.ofNullable(config.getModelName()).orElse(""),
                            Optional.ofNullable(config.getModelKey()).orElse("chat"),
                            Optional.ofNullable(config.getMaxToken()).orElse(4096),
                            config.getEnabled() ? "1" : "0",
                            Map.of() // 可根据需要扩展 extra 字段
                    )
            ).collect(Collectors.toList());

            result.add(new LlmServiceProvider(providerId, models, metadata));
        }

        return result;
    }
    @Override
    public int deleteModelsByProvider(Long userId, String providerCode) {
        try {
            LambdaQueryWrapper<ModelConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ModelConfigEntity::getUserId, userId)
                    .eq(ModelConfigEntity::getProvider, providerCode);
            int deletedCount = modelConfigMapper.delete(queryWrapper);
            log.info("删除用户 {} 在供应商 {} 下的模型配置，共删除 {} 条", userId, providerCode, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("删除供应商模型配置失败, userId={}, providerCode={}", userId, providerCode, e);
            return 0;
        }
    }

    /**
     * 将实体转换为响应DTO
     */
    private ModelConfigResponse convertToResponse(ModelConfigEntity entity) {
        return new ModelConfigResponse(
                entity.getModelName(),
                entity.getModelKey(),
                entity.getUseImage(),
                entity.getDescription(),
                entity.getIconUrl(),
                entity.getProvider(),
                entity.getFunctionCall()
        );
    }

    /**
     * 将实体转换为配置对象
     */
    private ModelConfig convertToModelConfig(ModelConfigEntity entity) {
        return new ModelConfig(
                entity.getModelName(),
                entity.getModelKey(),
                entity.getUseImage(),
                entity.getDescription(),
                entity.getIconUrl(),
                entity.getProvider(),
                entity.getApiKey(),
                entity.getApiUrl(),
                entity.getFunctionCall()
        );
    }
}
