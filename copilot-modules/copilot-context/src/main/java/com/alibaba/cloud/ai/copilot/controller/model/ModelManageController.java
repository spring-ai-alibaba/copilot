package com.alibaba.cloud.ai.copilot.controller.model;

import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.domain.dto.model.ModelOptionVO;
import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelManageController {

    private final ModelConfigService modelConfigService;

    /**
     * 获取所有模型配置（前端用）
     * 过滤敏感信息，只返回前端需要的字段
     */
    @GetMapping("/list")
    public List<ModelOptionVO> getAllModels() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        List<ModelConfigEntity> entities = modelConfigService.getVisibleModelEntities(loginUser.getUserId());
        List<ModelOptionVO> result = entities.stream()
                // 只返回启用的模型
                .filter(entity -> entity.getEnabled() != null && entity.getEnabled())
                .map(ModelOptionVO::fromEntity)
                .sorted(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0))
                .collect(Collectors.toList());
        result.forEach(model -> log.info("Model: value={}, label={}, provider={}",
            model.getKey(), model.getName(), model.getProvider()));
        return result;
    }

    /**
     * 获取所有模型配置（管理后台用）
     * 包含完整信息，用于管理界面
     */
    @GetMapping("/admin/list")
    public List<ModelConfigEntity> getAllModelsForAdmin() {
        return modelConfigService.getAllModelEntities();
    }

    /**
     * 根据ID获取模型配置
     */
    @GetMapping("/{id}")
    public ModelConfigEntity getModelById(@PathVariable Long id) {
        return modelConfigService.getModelEntityById(id);
    }

    /**
     * 根据模型名称获取模型完整信息
     * 支持按模型名称(modelName)或模型键(modelKey)查询
     * 默认只返回第一条匹配的记录
     */
    @GetMapping("/name/{modelName}")
    public ModelConfigEntity getModelByName(@PathVariable String modelName) {
        return modelConfigService.getModelEntityByName(modelName);
    }

    /**
     * 保存或更新模型配置
     */
    @PostMapping("/save")
    public boolean saveOrUpdateModel(@RequestBody ModelConfigEntity modelEntity) {
        return modelConfigService.saveOrUpdateModel(modelEntity);
    }

    /**
     * 删除模型配置
     */
    @DeleteMapping("/{id}")
    public boolean deleteModel(@PathVariable Long id) {
        return modelConfigService.deleteModel(id);
    }

    /**
     * 启用/禁用模型
     */
    @PutMapping("/{id}/toggle")
    public boolean toggleModelStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        return modelConfigService.toggleModelStatus(id, enabled);
    }
}
