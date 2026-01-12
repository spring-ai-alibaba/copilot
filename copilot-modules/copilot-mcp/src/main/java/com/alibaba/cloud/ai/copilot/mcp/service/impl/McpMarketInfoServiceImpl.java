package com.alibaba.cloud.ai.copilot.mcp.service.impl;


import com.alibaba.cloud.ai.copilot.mcp.dto.McpServerListResponse;
import com.alibaba.cloud.ai.copilot.mcp.entity.McpMarketInfo;
import com.alibaba.cloud.ai.copilot.mcp.entity.McpMarketTool;
import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.enums.McpToolStatusEnum;
import com.alibaba.cloud.ai.copilot.mcp.enums.McpToolTypeEnum;
import com.alibaba.cloud.ai.copilot.mcp.mapper.McpMarketInfoMapper;
import com.alibaba.cloud.ai.copilot.mcp.mapper.McpMarketToolMapper;
import com.alibaba.cloud.ai.copilot.mcp.register.McpToolRegistryService;
import com.alibaba.cloud.ai.copilot.mcp.service.McpMarketInfoService;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MCP 市场服务实现类
 *
 * @author Administrator
 */
@Slf4j
@Service
public class McpMarketInfoServiceImpl
        extends ServiceImpl<McpMarketInfoMapper, McpMarketInfo>
        implements McpMarketInfoService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * HTTP 客户端，用于调用外部 API
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    @Resource
    private McpMarketToolMapper marketToolMapper;
    @Resource
    private McpToolInfoService mcpToolInfoService;
    @Resource
    private McpToolRegistryService mcpToolRegistryService;

    @Override
    public McpMarketInfo saveOrUpdateInfo(McpMarketInfo market) {
        if (market.getId() == null) {
            market.setCreateTime(LocalDateTime.now());
        }
        market.setUpdateTime(LocalDateTime.now());
        if (market.getStatus() == null) {
            market.setStatus("");
        }
        super.saveOrUpdate(market);
        return market;
    }

    @Override
    public McpMarketInfo getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<McpMarketInfo> listAll() {
        return super.list(new LambdaQueryWrapper<McpMarketInfo>().orderByDesc(McpMarketInfo::getCreateTime));
    }

    @Override
    public List<McpMarketInfo> listByStatus(String status) {
        return list(new LambdaQueryWrapper<McpMarketInfo>()
                .eq(McpMarketInfo::getStatus, status)
                .orderByDesc(McpMarketInfo::getCreateTime));
    }

    @Override
    public List<McpMarketInfo> searchByName(String name) {
        return list(new LambdaQueryWrapper<McpMarketInfo>()
                .like(McpMarketInfo::getName, name)
                .orderByDesc(McpMarketInfo::getCreateTime));
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        McpMarketInfo market = getById(id);
        if (market == null) {
            return false;
        }
        market.setStatus(status);
        market.setUpdateTime(LocalDateTime.now());
        return super.updateById(market);
    }

    @Override
    public List<McpMarketTool> getMarketTools(Long marketId) {
        return marketToolMapper.selectList(new LambdaQueryWrapper<McpMarketTool>()
                .eq(McpMarketTool::getMarketId, marketId)
                .orderByDesc(McpMarketTool::getCreateTime));
    }

    @Override
    public Map<String, Object> getMarketToolsWithPage(Long marketId, Integer page, Integer size) {
        // 创建分页对象
        Page<McpMarketTool> pageObj = new Page<>(page, size);

        // 使用 LambdaQueryWrapper 构建查询条件
        LambdaQueryWrapper<McpMarketTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpMarketTool::getMarketId, marketId)
                .orderByDesc(McpMarketTool::getCreateTime);

        // 执行分页查询
        IPage<McpMarketTool> pageResult = marketToolMapper.selectPage(pageObj, wrapper);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());

        return result;
    }

    @Override
    public boolean refreshMarketTools(Long marketId) {
        try {
            McpMarketInfo market = getById(marketId);
            if (market == null) {
                return false;
            }

            // 调用市场API获取工具列表
            String url = market.getUrl();

            // 构建请求头 Map
            Map<String, String> headers = new HashMap<>();

            // 如果有认证配置，添加到请求头
            if (market.getAuthConfig() != null && !market.getAuthConfig().isEmpty()) {
                try {
                    Map<String, String> authConfig = objectMapper.readValue(market.getAuthConfig(),
                            new TypeReference<>() {
                            });
                    if (authConfig.containsKey("apiKey")) {
                        headers.put("Authorization", "Bearer " + authConfig.get("apiKey"));
                    }
                } catch (Exception e) {
                    // 忽略认证配置解析错误
                }
            }

            // 分页获取所有数据，循环请求直到返回为空
            int pageSize = 40; // 每页大小
            int pageNumber = 1;
            Random random = new Random();
            boolean hasMore = true;

            while (hasMore) {
                // 构建带查询参数的 URL
                // https://mcpservers.cn/api/servers/list?tab=all&search=&page=1&pageSize=40&lang=zh
                String queryString = String.format("tab=all&search=&page=%d&pageSize=%d&lang=zh",
                        pageNumber, pageSize);
                String requestUrl = url.contains("?") ? url + "&" + queryString : url + "?" + queryString;

                try {
                    // 构建 HTTP 请求
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(requestUrl))
                            .timeout(Duration.ofSeconds(30))
                            .GET();

                    // 添加请求头
                    if (headers != null && !headers.isEmpty()) {
                        headers.forEach((key, value) -> {
                            if (value != null && !value.isEmpty()) {
                                requestBuilder.header(key, value);
                            }
                        });
                    }

                    HttpRequest request = requestBuilder.build();

                    // 发送请求
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        // 请求失败，停止请求
                        log.warn("请求失败，状态码: {}", response.statusCode());
                        break;
                    }

                    // 解析响应
                    McpServerListResponse responseData =
                            objectMapper.readValue(response.body(), McpServerListResponse.class);

                    if (responseData == null || responseData.getServers() == null) {
                        // 响应不成功或数据为空，停止请求
                        break;
                    }
                    List<McpServerListResponse.McpServerInfo> serverList = responseData.getServers();

                    // 如果返回的列表为空，说明没有更多数据了
                    if (serverList.isEmpty()) {
                        break;
                    }

                    // 保存或更新当前页的工具
                    for (McpServerListResponse.McpServerInfo serverInfo : serverList) {
                        // 获取服务器ID作为唯一标识
                        String serverId = serverInfo.getId();
                        if (serverId == null || serverId.isEmpty()) {
                            continue; // 跳过没有ID的服务器
                        }

                        // 检查是否已存在（根据 marketId 和 serverId，使用 MyBatis-Plus apply 方法处理 JSON 查询）
                        McpMarketTool existingTool = marketToolMapper.selectOne(new LambdaQueryWrapper<McpMarketTool>()
                                .eq(McpMarketTool::getMarketId, marketId)
                                .apply("JSON_UNQUOTE(JSON_EXTRACT(tool_metadata, '$.id')) = {0}", serverId)
                                .last("LIMIT 1"));

                        // 获取工具名称（优先使用 title，其次使用 name）
                        String toolName = serverInfo.getTitle() != null && !serverInfo.getTitle().isEmpty()
                                ? serverInfo.getTitle()
                                : (serverInfo.getName() != null ? serverInfo.getName() : "");

                        // 获取描述
                        String description = serverInfo.getDescription() != null
                                ? serverInfo.getDescription()
                                : "";

                        // 构建完整的元数据
                        Map<String, Object> toolMetadata = new HashMap<>();
                        toolMetadata.put("id", serverId);
                        toolMetadata.put("name", serverInfo.getName() != null ? serverInfo.getName() : "");
                        toolMetadata.put("title", serverInfo.getTitle() != null ? serverInfo.getTitle() : "");
                        toolMetadata.put("description", description);
                        toolMetadata.put("author", serverInfo.getAuthor() != null ? serverInfo.getAuthor() : "");
                        toolMetadata.put("icon", serverInfo.getIcon() != null ? serverInfo.getIcon() : "");
                        toolMetadata.put("github_url", serverInfo.getGithubUrl() != null ? serverInfo.getGithubUrl() : "");
                        toolMetadata.put("orderBy", serverInfo.getOrderBy() != null ? serverInfo.getOrderBy() : 0);
                        toolMetadata.put("score", serverInfo.getScore() != null ? serverInfo.getScore() : "");
                        if (serverInfo.getCategory() != null) {
                            Map<String, Object> category = new HashMap<>();
                            category.put("id", serverInfo.getCategory().getId());
                            category.put("name", serverInfo.getCategory().getName());
                            category.put("label", serverInfo.getCategory().getLabel());
                            toolMetadata.put("category", category);
                        }

                        String metadataJson = objectMapper.writeValueAsString(toolMetadata);

                        if (existingTool != null) {
                            // 更新已存在的工具（保留加载状态）
                            existingTool.setToolName(toolName);
                            existingTool.setToolDescription(description);
                            existingTool.setToolMetadata(metadataJson);
                            // 不更新 isLoaded 和 localToolId，保留原有状态
                            marketToolMapper.updateById(existingTool);
                        } else {
                            // 添加新工具
                            McpMarketTool tool = McpMarketTool.builder()
                                    .marketId(marketId)
                                    .toolName(toolName)
                                    .toolDescription(description)
                                    .toolVersion(null) // API 响应中没有版本信息
                                    .toolMetadata(metadataJson)
                                    .isLoaded(false)
                                    .createTime(LocalDateTime.now())
                                    .build();
                            marketToolMapper.insert(tool);
                        }
                    }

                    // 判断是否还有更多数据
                    int currentPageSize = serverList.size();
                    if (currentPageSize < pageSize) {
                        // 返回的数据少于每页大小，说明已经是最后一页了
                        hasMore = false;
                    } else {
                        // 还有更多数据，继续请求下一页
                        pageNumber++;
                    }
                } catch (Exception e) {
                    log.error("请求市场工具列表失败: page={}, url={}", pageNumber, requestUrl, e);
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("刷新市场工具列表失败", e);
        }
        return false;
    }

    @Override
    public boolean loadToolToLocal(Long marketToolId) {
        try {
            McpMarketTool marketTool = marketToolMapper.selectById(marketToolId);
            if (marketTool == null || marketTool.getIsLoaded()) {
                return false;
            }

            // 创建本地工具
            McpToolInfo localTool = McpToolInfo.builder()
                    .name(marketTool.getToolName())
                    .description(marketTool.getToolDescription())
                    .type(McpToolTypeEnum.REMOTE.getValue())
                    .status(McpToolStatusEnum.ENABLED.getValue())
                    .configJson(marketTool.getToolMetadata())
                    .build();

            // 保存本地工具
            McpToolInfo mcpToolInfo = mcpToolInfoService.saveOrUpdateInfo(localTool);

            // 自动注册到 Spring AI MCP 系统
            try {
                if (mcpToolRegistryService.registerTool(mcpToolInfo)) {
                    log.info("工具加载后自动注册成功: {}", mcpToolInfo.getName());
                } else {
                    log.warn("工具加载后自动注册失败: {}", mcpToolInfo.getName());
                }
            } catch (Exception e) {
                log.error("工具加载后自动注册异常: {}", mcpToolInfo.getName(), e);
                // 注册失败不影响工具加载，继续执行
            }

            // 更新市场工具的加载状态（使用 MyBatis-Plus 方式）
            McpMarketTool updateTool = new McpMarketTool();
            updateTool.setId(marketToolId);
            updateTool.setIsLoaded(true);
            updateTool.setLocalToolId(mcpToolInfo.getId());
            marketToolMapper.updateById(updateTool);

            return true;
        } catch (Exception e) {
            log.error("加载工具到本地失败", e);
            return false;
        }
    }

    @Override
    public int batchLoadToolsToLocal(List<Long> marketToolIds) {
        if (marketToolIds == null || marketToolIds.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (Long marketToolId : marketToolIds) {
            try {
                McpMarketTool marketTool = marketToolMapper.selectById(marketToolId);
                if (marketTool == null || marketTool.getIsLoaded()) {
                    continue; // 跳过已加载或不存在的工具
                }

                // 创建本地工具
                McpToolInfo localTool = McpToolInfo.builder()
                        .name(marketTool.getToolName())
                        .description(marketTool.getToolDescription())
                        .type(McpToolTypeEnum.LOCAL.getValue())
                        .status(McpToolStatusEnum.ENABLED.getValue())
                        .configJson(marketTool.getToolMetadata())
                        .build();

                // 保存本地工具
                McpToolInfo savedTool = mcpToolInfoService.saveOrUpdateInfo(localTool);

                // 更新市场工具的加载状态（使用 MyBatis-Plus 方式）
                McpMarketTool updateTool = new McpMarketTool();
                updateTool.setId(marketToolId);
                updateTool.setIsLoaded(true);
                updateTool.setLocalToolId(savedTool.getId());
                marketToolMapper.updateById(updateTool);
                successCount++;
            } catch (Exception e) {
                log.error("批量加载工具失败: marketToolId={}", marketToolId, e);
                // 继续处理下一个工具，不中断批量操作
            }
        }

        return successCount;
    }
}

