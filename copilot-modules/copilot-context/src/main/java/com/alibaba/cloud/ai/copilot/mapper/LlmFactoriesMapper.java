package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.LlmFactoriesEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型供应商Mapper接口
 */
@Mapper
public interface LlmFactoriesMapper extends BaseMapper<LlmFactoriesEntity> {
    
    /**
     * 查询所有启用的供应商
     */
    @Select("SELECT * FROM llm_factories WHERE status = 1 ORDER BY `rank` ASC")
    List<LlmFactoriesEntity> selectEnabledProviders();
    
    /**
     * 根据供应商名称查询供应商
     */
    @Select("SELECT * FROM llm_factories WHERE name = #{name}")
    LlmFactoriesEntity selectByFactoriesName(String name);
    
    /**
     * 根据供应商代码查询供应商
     */
    @Select("SELECT * FROM llm_factories WHERE provider_code = #{providerCode}")
    LlmFactoriesEntity selectByProviderCode(String providerCode);
}
