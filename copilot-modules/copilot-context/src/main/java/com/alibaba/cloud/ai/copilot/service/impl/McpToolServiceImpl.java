package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.McpToolListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.service.mcp.McpClientManager;
import com.alibaba.cloud.ai.copilot.service.McpToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 工具服务实现
 *
 * @author copilot team: evo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl extends ServiceImpl<McpToolInfoMapper, McpToolInfo>
        implements McpToolService {

    /**
     * 工具类型常量：内置工具（历史遗留数据可能仍带此类型，保留以兼容读写保护）
     */
    private static final String TYPE_BUILTIN = "BUILTIN";

    private final McpClientManager mcpClientManager;

    @Override
    public McpToolListResult listTools(String keyword, String type, String status) {
        LambdaQueryWrapper<McpToolInfo> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(McpToolInfo::getName, keyword)
                    .or()
                    .like(McpToolInfo::getDescription, keyword));
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(McpToolInfo::getType, type);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(McpToolInfo::getStatus, status);
        }

        wrapper.orderByDesc(McpToolInfo::getUpdateTime);

        List<McpToolInfo> list = list(wrapper);

        return McpToolListResult.of(list);
    }

    @Override
    @Transactional
    public McpToolInfo saveTool(McpToolInfo tool) {
        tool.setCreateTime(LocalDateTime.now());
        tool.setUpdateTime(LocalDateTime.now());
        if (tool.getStatus() == null) {
            tool.setStatus(ToolStatus.ENABLED.getValue());
        }
        if (tool.getType() == null) {
            tool.setType("LOCAL");
        }
        // 保存前校验配置，非法配置即时报错而不是等到连接时才失败
        mcpClientManager.validateConfig(tool);
        save(tool);
        return tool;
    }

    @Override
    @Transactional
    public McpToolInfo updateTool(McpToolInfo tool) {
        // 检查是否为内置工具，内置工具不允许编辑
        McpToolInfo existingTool = getById(tool.getId());
        if (existingTool != null && TYPE_BUILTIN.equals(existingTool.getType())) {
            throw new RuntimeException("内置工具不允许编辑");
        }

        // 校验以更新后的生效值为准（局部更新时字段可能为 null）
        if (tool.getConfigJson() != null || tool.getType() != null) {
            McpToolInfo effective = new McpToolInfo();
            effective.setType(tool.getType() != null ? tool.getType()
                    : existingTool != null ? existingTool.getType() : null);
            effective.setConfigJson(tool.getConfigJson() != null ? tool.getConfigJson()
                    : existingTool != null ? existingTool.getConfigJson() : null);
            mcpClientManager.validateConfig(effective);
        }

        tool.setUpdateTime(LocalDateTime.now());
        updateById(tool);

        // 如果工具正在使用中，需要刷新连接
        mcpClientManager.refreshClient(tool.getId());

        return getById(tool.getId());
    }

    @Override
    @Transactional
    public void deleteTool(Long id) {
        // 检查是否为内置工具，内置工具不允许删除
        McpToolInfo tool = getById(id);
        if (tool != null && TYPE_BUILTIN.equals(tool.getType())) {
            throw new RuntimeException("内置工具不允许删除");
        }

        // 关闭可能存在的连接
        mcpClientManager.closeClient(id);
        removeById(id);
    }

    @Override
    @Transactional
    public void batchDeleteTools(List<Long> ids) {
        // 过滤掉内置工具
        List<Long> deletableIds = ids.stream()
                .filter(id -> {
                    McpToolInfo tool = getById(id);
                    return tool == null || !TYPE_BUILTIN.equals(tool.getType());
                })
                .toList();

        if (deletableIds.isEmpty()) {
            throw new RuntimeException("所选工具均为内置工具，不允许删除");
        }

        deletableIds.forEach(mcpClientManager::closeClient);
        removeByIds(deletableIds);
    }

    @Override
    @Transactional
    public void updateToolStatus(Long id, String status) {
        McpToolInfo tool = new McpToolInfo();
        tool.setId(id);
        tool.setStatus(status);
        tool.setUpdateTime(LocalDateTime.now());
        updateById(tool);

        // 如果禁用，关闭连接
        if (ToolStatus.DISABLED.getValue().equals(status)) {
            mcpClientManager.closeClient(id);
        }
    }

    @Override
    public McpToolTestResult testTool(Long id) {
        McpToolInfo tool = getById(id);
        if (tool == null) {
            return McpToolTestResult.fail("工具不存在");
        }

        // 根据工具类型选择不同的测试逻辑
        if (TYPE_BUILTIN.equals(tool.getType())) {
            // 内置工具 - 直接验证是否在注册表中
            return testBuiltinTool(tool);
        } else {
            // MCP 工具 (LOCAL/REMOTE) - 测试连接
            return mcpClientManager.testConnection(tool);
        }
    }

    /**
     * 测试内置工具
     * 内置工具由 agent 框架（agentscope）原生提供，不需要网络连接，记录存在即视为可用。
     *
     * @param tool 工具信息
     * @return 测试结果
     */
    private McpToolTestResult testBuiltinTool(McpToolInfo tool) {
        return McpToolTestResult.success(
                String.format("内置工具 [%s] 可用", tool.getName()),
                1,
                List.of(tool.getName())
        );
    }
}

