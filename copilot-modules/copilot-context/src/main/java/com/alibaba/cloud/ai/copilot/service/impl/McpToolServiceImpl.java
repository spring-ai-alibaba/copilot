package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.domain.dto.McpToolListResult;
import com.alibaba.cloud.ai.copilot.domain.dto.McpToolTestResult;
import com.alibaba.cloud.ai.copilot.domain.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.enums.ToolStatus;
import com.alibaba.cloud.ai.copilot.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.mcp.BuiltinToolRegistry;
import com.alibaba.cloud.ai.copilot.mcp.McpClientManager;
import com.alibaba.cloud.ai.copilot.service.McpToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 工具服务实现
 *
 * @author copilot team: evo
 * @email exotisch@163.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolServiceImpl extends ServiceImpl<McpToolInfoMapper, McpToolInfo>
        implements McpToolService {

    private final McpClientManager mcpClientManager;
    private final BuiltinToolRegistry builtinToolRegistry;

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
        save(tool);
        return tool;
    }

    @Override
    @Transactional
    public McpToolInfo updateTool(McpToolInfo tool) {
        // 检查是否为内置工具，内置工具不允许编辑
        McpToolInfo existingTool = getById(tool.getId());
        if (existingTool != null && BuiltinToolRegistry.TYPE_BUILTIN.equals(existingTool.getType())) {
            throw new RuntimeException("内置工具不允许编辑");
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
        if (tool != null && BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getType())) {
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
                    return tool == null || !BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getType());
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
        if (BuiltinToolRegistry.TYPE_BUILTIN.equals(tool.getType())) {
            // 内置工具 - 直接验证是否在注册表中
            return testBuiltinTool(tool);
        } else {
            // MCP 工具 (LOCAL/REMOTE) - 测试连接
            return mcpClientManager.testConnection(tool);
        }
    }

    /**
     * 测试内置工具
     * 内置工具不需要网络连接，只需验证是否在注册表中
     *
     * @param tool 工具信息
     * @return 测试结果
     */
    private McpToolTestResult testBuiltinTool(McpToolInfo tool) {
        try {
            ToolCallback callback = builtinToolRegistry.createToolCallback(tool.getName());
            if (callback != null) {
                return McpToolTestResult.success(
                        String.format("内置工具 [%s] 已注册，可正常使用", tool.getName()),
                        1,
                        List.of(tool.getName())
                );
            } else {
                return McpToolTestResult.fail(
                        String.format("内置工具 [%s] 未在注册表中找到，请检查工具名称是否正确", tool.getName())
                );
            }
        } catch (Exception e) {
            log.error("测试内置工具失败: {} - {}", tool.getName(), e.getMessage());
            return McpToolTestResult.fail("测试失败: " + e.getMessage());
        }
    }
}

