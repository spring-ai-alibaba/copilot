package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketToolListResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpMarketInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MCP 市场服务接口
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
public interface McpMarketService extends IService<McpMarketInfo> {

    /**
     * 查询市场列表
     *
     * @param keyword 关键词
     * @param status  状态
     * @return 市场列表结果
     */
    McpMarketListResult listMarkets(String keyword, String status);

    /**
     * 保存市场
     *
     * @param market 市场信息
     * @return 保存后的市场信息
     */
    McpMarketInfo saveMarket(McpMarketInfo market);

    /**
     * 更新市场
     *
     * @param market 市场信息
     * @return 更新后的市场信息
     */
    McpMarketInfo updateMarket(McpMarketInfo market);

    /**
     * 删除市场
     *
     * @param id 市场 ID
     */
    void deleteMarket(Long id);

    /**
     * 更新市场状态
     *
     * @param id 市场 ID
     * @param status 状态
     */
    void updateMarketStatus(Long id, String status);

    /**
     * 获取市场工具列表
     *
     * @param marketId 市场 ID
     * @param page     页码
     * @param size     每页大小
     * @return 工具列表结果
     */
    McpMarketToolListResult getMarketTools(Long marketId, int page, int size);

    /**
     * 刷新市场工具列表
     *
     * @param marketId 市场 ID
     */
    void refreshMarketTools(Long marketId);

    /**
     * 加载工具到本地
     *
     * @param toolId 市场工具 ID
     */
    void loadToolToLocal(Long toolId);

    /**
     * 批量加载工具到本地
     *
     * @param toolIds 工具 ID 列表
     * @return 成功加载的数量
     */
    int batchLoadTools(java.util.List<Long> toolIds);
}

