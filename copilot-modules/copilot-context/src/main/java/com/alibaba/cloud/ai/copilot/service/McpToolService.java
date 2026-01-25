package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.McpToolListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * MCP 工具服务接口
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
public interface McpToolService extends IService<McpToolInfo> {

    /**
     * 查询工具列表
     *
     * @param keyword 关键词
     * @param type    类型
     * @param status  状态
     * @return 工具列表结果
     */
    McpToolListResult listTools(String keyword, String type, String status);

    /**
     * 保存工具
     *
     * @param tool 工具信息
     * @return 保存后的工具信息
     */
    McpToolInfo saveTool(McpToolInfo tool);

    /**
     * 更新工具
     *
     * @param tool 工具信息
     * @return 更新后的工具信息
     */
    McpToolInfo updateTool(McpToolInfo tool);

    /**
     * 删除工具
     *
     * @param id 工具 ID
     */
    void deleteTool(Long id);

    /**
     * 批量删除工具
     *
     * @param ids 工具 ID 列表
     */
    void batchDeleteTools(List<Long> ids);

    /**
     * 更新工具状态
     *
     * @param id     工具 ID
     * @param status 状态
     */
    void updateToolStatus(Long id, String status);

    /**
     * 测试工具连接
     *
     * @param id 工具 ID
     * @return 测试结果
     */
    McpToolTestResult testTool(Long id);
}

