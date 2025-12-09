package com.alibaba.cloud.ai.copilot.model.mapper;

import com.alibaba.cloud.ai.copilot.model.entity.ModelConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型配置Mapper接口
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {
    
    /**
     * 查询所有启用的模型配置，按排序顺序排列
     */
    @Select("SELECT * FROM model_config WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<ModelConfigEntity> selectEnabledModels();
    
    /**
     * 根据模型键查询模型配置
     */
    @Select("SELECT * FROM model_config WHERE model_key = #{modelKey} AND enabled = 1")
    ModelConfigEntity selectByModelKey(String modelKey);
}
