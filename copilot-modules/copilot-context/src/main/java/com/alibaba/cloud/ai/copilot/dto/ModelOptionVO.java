package com.alibaba.cloud.ai.copilot.dto;

import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型选项 VO，用于前端模型列表展示
 * 过滤敏感信息（如 apiKey、apiUrl）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelOptionVO {

    /**
     * 模型标识键，对应前端的 value
     */
    private String value;

    /**
     * 模型显示名称，对应前端的 label
     */
    private String label;

    /**
     * 是否支持图像处理
     */
    private Boolean useImage;

    /**
     * 配额（暂时设为默认值）
     */
    private Integer quota = 100;

    /**
     * 来源标识（可选）
     */
    private String from;

    /**
     * 模型提供商
     */
    private String provider;

    /**
     * 是否支持函数调用
     */
    private Boolean functionCall;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 图标URL
     */
    private String iconUrl;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 从 ModelConfigEntity 转换为 ModelOptionVO
     * 过滤敏感信息
     */
    public static ModelOptionVO fromEntity(ModelConfigEntity entity) {
        ModelOptionVO vo = new ModelOptionVO();
        // 确保 value 不为空，如果 modelKey 为空则使用 id
        String value = entity.getModelKey();
        if (value == null || value.trim().isEmpty()) {
            value = "model_" + entity.getId();
        }
        vo.setValue(value);

        // 确保 label 不为空
        String label = entity.getModelName();
        if (label == null || label.trim().isEmpty()) {
            label = "Model " + entity.getId();
        }
        vo.setLabel(label);

        vo.setUseImage(entity.getUseImage() != null ? entity.getUseImage() : false);
        vo.setQuota(100); // 默认配额
        vo.setFrom(entity.getProvider());
        vo.setProvider(entity.getProvider());
        vo.setFunctionCall(entity.getFunctionCall() != null ? entity.getFunctionCall() : false);
        vo.setDescription(entity.getDescription());
        vo.setIconUrl(entity.getIconUrl());
        vo.setEnabled(entity.getEnabled() != null ? entity.getEnabled() : false);
        vo.setSortOrder(entity.getSortOrder() != null ? entity.getSortOrder() : 0);
        return vo;
    }
}
