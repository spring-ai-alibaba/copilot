package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.LlmEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LLM模型Mapper接口
 */
@Mapper
public interface LlmMapper extends BaseMapper<LlmEntity> {

    /**
     * 根据厂商ID查询该厂商下的所有模型
     */
    @Select("SELECT * FROM llm WHERE fid = #{fid} ORDER BY id ASC")
    List<LlmEntity> selectByFactoryId(String fid);

    /**
     * 根据厂商ID查询启用状态的模型
     */
    @Select("SELECT * FROM llm WHERE fid = #{fid} AND status = '1' ORDER BY id ASC")
    List<LlmEntity> selectEnabledByFactoryId(String fid);
}
