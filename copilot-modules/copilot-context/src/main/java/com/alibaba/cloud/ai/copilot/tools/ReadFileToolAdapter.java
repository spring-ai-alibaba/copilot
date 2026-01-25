package com.alibaba.cloud.ai.copilot.tools;

import com.alibaba.cloud.ai.copilot.mcp.BuiltinToolProvider;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.ReadFileTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * ReadFileTool 适配器
 * 将外部包的 ReadFileTool 适配为 BuiltinToolProvider
 */
@Component
public class ReadFileToolAdapter implements BuiltinToolProvider {

    @Override
    public String getToolName() {
        return "read_file";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String getDescription() {
        return ReadFileTool.DESCRIPTION;
    }

    @Override
    public ToolCallback createToolCallback() {
        return ReadFileTool.createReadFileToolCallback(ReadFileTool.DESCRIPTION);
    }
}

