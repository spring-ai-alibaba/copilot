package com.alibaba.cloud.ai.copilot.mcp.mapper;

import com.alibaba.cloud.ai.copilot.mcp.entity.McpMarketInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 市场 Mapper 接口
 * 使用 MyBatis-Plus 的 LambdaQueryWrapper 进行查询，无需自定义方法
 *
 * @author Administrator
 */
@Mapper
public interface McpMarketInfoMapper extends BaseMapper<McpMarketInfo> {
}

