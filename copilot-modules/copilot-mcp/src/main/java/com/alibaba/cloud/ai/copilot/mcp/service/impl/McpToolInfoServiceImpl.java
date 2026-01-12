package com.alibaba.cloud.ai.copilot.mcp.service.impl;


import com.alibaba.cloud.ai.copilot.mcp.entity.McpToolInfo;
import com.alibaba.cloud.ai.copilot.mcp.mapper.McpToolInfoMapper;
import com.alibaba.cloud.ai.copilot.mcp.service.McpToolInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 工具服务实现类
 *
 * @author Administrator
 */
@Service
public class McpToolInfoServiceImpl extends ServiceImpl<McpToolInfoMapper, McpToolInfo> implements McpToolInfoService {

    @Override
    public McpToolInfo saveOrUpdateInfo(McpToolInfo tool) {
        if (tool.getId() == null) {
            tool.setCreateTime(LocalDateTime.now());
        }
        tool.setUpdateTime(LocalDateTime.now());
        if (tool.getStatus() == null) {
            tool.setStatus("");
        }
        super.saveOrUpdate(tool);
        return tool;
    }

    @Override
    public McpToolInfo getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<McpToolInfo> listAll() {
        return super.list(new LambdaQueryWrapper<McpToolInfo>().orderByDesc(McpToolInfo::getCreateTime));
    }

    @Override
    public List<McpToolInfo> listByType(String type) {
        return list(new LambdaQueryWrapper<McpToolInfo>()
                .eq(McpToolInfo::getType, type)
                .orderByDesc(McpToolInfo::getCreateTime));
    }

    @Override
    public List<McpToolInfo> listByStatus(String status) {
        return list(new LambdaQueryWrapper<McpToolInfo>()
                .eq(McpToolInfo::getStatus, status)
                .orderByDesc(McpToolInfo::getCreateTime));
    }

    @Override
    public List<McpToolInfo> searchByName(String name) {
        return list(new LambdaQueryWrapper<McpToolInfo>()
                .like(McpToolInfo::getName, name)
                .orderByDesc(McpToolInfo::getCreateTime));
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        return super.removeByIds(ids);
    }

    @Override
    public boolean updateStatus(Long id, String status) {
        McpToolInfo tool = getById(id);
        if (tool == null) {
            return false;
        }
        tool.setStatus(status);
        tool.setUpdateTime(LocalDateTime.now());
        return super.updateById(tool);
    }
}

