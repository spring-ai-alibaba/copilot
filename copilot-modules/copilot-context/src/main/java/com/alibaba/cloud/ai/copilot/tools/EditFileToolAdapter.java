package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.service.mcp.BuiltinToolProvider;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.EditFileTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * EditFileTool 适配器
 * 将外部包的 EditFileTool 适配为 BuiltinToolProvider
 */
@Component
public class EditFileToolAdapter implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "edit_file";
    }

    @Override
    public String getDisplayName() {
        return "编辑文件";
    }

    @Override
    public String getDescription() {
        return EditFileTool.DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION);
    }
}

