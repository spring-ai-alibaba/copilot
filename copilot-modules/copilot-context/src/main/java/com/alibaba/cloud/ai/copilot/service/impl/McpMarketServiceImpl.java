package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpMarketToolListResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpMarketInfo;
import com.alibaba.cloud.ai.copilot.domain.entity.McpMarketTool;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpMarketInfoMapper;
import com.alibaba.cloud.ai.copilot.mapper.McpMarketToolMapper;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.service.McpMarketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 市场服务实现
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpMarketServiceImpl extends ServiceImpl<McpMarketInfoMapper, McpMarketInfo>
        implements McpMarketService {

    private final McpMarketToolMapper mcpMarketToolMapper;
    private final McpToolInfoMapper mcpToolInfoMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Override
    public McpMarketListResult listMarkets(String keyword, String status) {
        LambdaQueryWrapper<McpMarketInfo> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(McpMarketInfo::getName, keyword)
                    .or()
                    .like(McpMarketInfo::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(McpMarketInfo::getStatus, status);
        }

        wrapper.orderByDesc(McpMarketInfo::getUpdateTime);

        List<McpMarketInfo> list = list(wrapper);

        return McpMarketListResult.of(list);
    }

    @Override
    @Transactional
    public McpMarketInfo saveMarket(McpMarketInfo market) {
        market.setCreateTime(LocalDateTime.now());
        market.setUpdateTime(LocalDateTime.now());
        if (market.getStatus() == null) {
            market.setStatus(ToolStatus.ENABLED.getValue());
        }
        save(market);
        return market;
    }

    @Override
    @Transactional
    public McpMarketInfo updateMarket(McpMarketInfo market) {
        market.setUpdateTime(LocalDateTime.now());
        updateById(market);
        return getById(market.getId());
    }

    @Override
    @Transactional
    public void deleteMarket(Long id) {
        // 先删除关联的市场工具
        LambdaQueryWrapper<McpMarketTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpMarketTool::getMarketId, id);
        mcpMarketToolMapper.delete(wrapper);

        // 删除市场
        removeById(id);
    }

    @Override
    @Transactional
    public void updateMarketStatus(Long id, String status) {
        McpMarketInfo market = new McpMarketInfo();
        market.setId(id);
        market.setStatus(status);
        market.setUpdateTime(LocalDateTime.now());
        updateById(market);
    }

    @Override
    public McpMarketToolListResult getMarketTools(Long marketId, int page, int size) {
        LambdaQueryWrapper<McpMarketTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpMarketTool::getMarketId, marketId);
        wrapper.orderByDesc(McpMarketTool::getCreateTime);

        Page<McpMarketTool> pageResult = mcpMarketToolMapper.selectPage(new Page<>(page, size), wrapper);

        return McpMarketToolListResult.of(
                pageResult.getRecords(),
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize()
        );
    }

    @Override
    @Transactional
    public void refreshMarketTools(Long marketId) {
        McpMarketInfo market = getById(marketId);
        if (market == null) {
            throw new RuntimeException("市场不存在");
        }

        try {
            // 从市场 URL 获取工具列表
            String response = restTemplate.getForObject(market.getUrl(), String.class);
            JsonNode rootNode = objectMapper.readTree(response);

            // 假设响应格式为 { "data": [...] } 或直接是数组
            JsonNode toolsNode = rootNode.has("data") ? rootNode.get("data") : rootNode;

            if (toolsNode.isArray()) {
                // 先清空原有工具
                LambdaQueryWrapper<McpMarketTool> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(McpMarketTool::getMarketId, marketId);
                mcpMarketToolMapper.delete(deleteWrapper);

                // 插入新工具
                for (JsonNode toolNode : toolsNode) {
                    McpMarketTool tool = new McpMarketTool();
                    tool.setMarketId(marketId);
                    tool.setToolName(getTextValue(toolNode, "name", "title"));
                    tool.setToolDescription(getTextValue(toolNode, "description", "desc"));
                    tool.setToolVersion(getTextValue(toolNode, "version"));
                    tool.setToolMetadata(toolNode.toString());
                    tool.setIsLoaded(false);
                    tool.setCreateTime(LocalDateTime.now());
                    mcpMarketToolMapper.insert(tool);
                }
            }

            log.info("Successfully refreshed market tools for market: {}", market.getName());
        } catch (Exception e) {
            log.error("Failed to refresh market tools for market {}: {}", marketId, e.getMessage());
            throw new RuntimeException("刷新市场工具列表失败: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 节点获取文本值，尝试多个字段名
     */
    private String getTextValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asText();
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void loadToolToLocal(Long toolId) {
        McpMarketTool marketTool = mcpMarketToolMapper.selectById(toolId);
        if (marketTool == null) {
            throw new RuntimeException("市场工具不存在");
        }

        if (marketTool.getIsLoaded()) {
            throw new RuntimeException("工具已加载到本地");
        }

        try {
            // 解析工具元数据
            JsonNode metadata = objectMapper.readTree(marketTool.getToolMetadata());

            // 创建本地工具
            McpToolInfo localTool = new McpToolInfo();
            localTool.setName(marketTool.getToolName());
            localTool.setDescription(marketTool.getToolDescription());

            // 根据元数据判断类型
            if (metadata.has("baseUrl") || metadata.has("url")) {
                localTool.setType("REMOTE");
                String baseUrl = metadata.has("baseUrl") ? metadata.get("baseUrl").asText() :
                                 metadata.has("url") ? metadata.get("url").asText() : null;
                localTool.setConfigJson(objectMapper.writeValueAsString(Map.of("baseUrl", baseUrl != null ? baseUrl : "")));
            } else {
                localTool.setType("LOCAL");
                // 构建本地工具配置
                Map<String, Object> config = new HashMap<>();
                if (metadata.has("command")) {
                    config.put("command", metadata.get("command").asText());
                }
                if (metadata.has("args") && metadata.get("args").isArray()) {
                    config.put("args", objectMapper.convertValue(metadata.get("args"), List.class));
                }
                if (metadata.has("env") && metadata.get("env").isObject()) {
                    config.put("env", objectMapper.convertValue(metadata.get("env"), Map.class));
                }
                // 如果有 npm 包名，使用 npx 启动
                if (metadata.has("package") || metadata.has("npmPackage")) {
                    String packageName = metadata.has("package") ? metadata.get("package").asText() :
                                        metadata.get("npmPackage").asText();
                    config.put("command", "npx");
                    config.put("args", List.of("-y", packageName));
                }
                localTool.setConfigJson(objectMapper.writeValueAsString(config));
            }

            localTool.setStatus(ToolStatus.ENABLED.getValue());
            localTool.setCreateTime(LocalDateTime.now());
            localTool.setUpdateTime(LocalDateTime.now());
            mcpToolInfoMapper.insert(localTool);

            // 更新市场工具状态
            marketTool.setIsLoaded(true);
            marketTool.setLocalToolId(localTool.getId());
            mcpMarketToolMapper.updateById(marketTool);

            log.info("Successfully loaded tool {} to local", marketTool.getToolName());
        } catch (Exception e) {
            log.error("Failed to load tool to local: {}", e.getMessage());
            throw new RuntimeException("加载工具到本地失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public int batchLoadTools(List<Long> toolIds) {
        int successCount = 0;
        for (Long toolId : toolIds) {
            try {
                loadToolToLocal(toolId);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to load tool {}: {}", toolId, e.getMessage());
            }
        }
        return successCount;
    }
}

