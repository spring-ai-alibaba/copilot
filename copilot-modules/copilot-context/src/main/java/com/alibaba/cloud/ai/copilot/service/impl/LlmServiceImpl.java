package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.entity.LlmEntity;
import com.alibaba.cloud.ai.copilot.mapper.LlmMapper;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM模型服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final LlmMapper llmMapper;

    @Override
    public List<LlmEntity> getAllModels() {
        return llmMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    public LlmEntity getById(Long id) {
        return llmMapper.selectById(id);
    }

    @Override
    public List<LlmEntity> getModelsByFactoryId(String fid) {
        return llmMapper.selectByFactoryId(fid);
    }

    @Override
    public List<LlmEntity> getEnabledModelsByFactoryId(String fid) {
        return llmMapper.selectEnabledByFactoryId(fid);
    }

    @Override
    public boolean saveOrUpdate(LlmEntity entity) {
        try {
            if (entity.getId() == null) {
                return llmMapper.insert(entity) > 0;
            } else {
                return llmMapper.updateById(entity) > 0;
            }
        } catch (Exception e) {
            log.error("保存或更新LLM模型失败", e);
            return false;
        }
    }

    @Override
    public boolean delete(Long id) {
        try {
            return llmMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.error("删除LLM模型失败", e);
            return false;
        }
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        try {
            LambdaUpdateWrapper<LlmEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(LlmEntity::getId, id)
                    .set(LlmEntity::getStatus, status);
            return llmMapper.update(null, updateWrapper) > 0;
        } catch (Exception e) {
            log.error("更新LLM模型状态失败", e);
            return false;
        }
    }
}
