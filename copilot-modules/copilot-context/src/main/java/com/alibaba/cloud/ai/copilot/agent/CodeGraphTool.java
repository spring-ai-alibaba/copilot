package com.alibaba.cloud.ai.copilot.agent;

import com.alibaba.cloud.ai.copilot.service.CodeGraphService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.nio.file.Path;

/** Plan Agent 使用的 CodeGraph 只读工具集合。 */
public class CodeGraphTool {

    private final CodeGraphService codeGraphService;
    private final Path workspace;

    public CodeGraphTool(CodeGraphService codeGraphService, Path workspace) {
        this.codeGraphService = codeGraphService;
        this.workspace = workspace;
    }

    @Tool(name = "codegraph_search", readOnly = true,
            description = "使用 CodeGraph 按符号或功能定位代码。仅在已索引工作区中可用。")
    public String search(@ToolParam(name = "query", description = "要查找的符号、模块或功能描述") String query) {
        return codeGraphService.search(workspace, query);
    }

    @Tool(name = "codegraph_explore", readOnly = true,
            description = "读取关键符号上下文并分析代码链路；适合规划前确认实现位置。")
    public String explore(@ToolParam(name = "query", description = "目标符号或要探索的问题") String query) {
        return codeGraphService.explore(workspace, query);
    }

    @Tool(name = "codegraph_impact", readOnly = true,
            description = "分析修改一个符号可能影响的调用者、下游模块和关系。")
    public String impact(@ToolParam(name = "symbol", description = "要评估影响范围的类、方法或函数名") String symbol) {
        return codeGraphService.impact(workspace, symbol);
    }

    @Tool(name = "codegraph_affected_tests", readOnly = true,
            description = "寻找修改某个工作区内文件后可能需要运行的关联测试。")
    public String affectedTests(@ToolParam(name = "path", description = "工作区内相对文件路径，不允许 ..") String path) {
        return codeGraphService.affectedTests(workspace, path);
    }
}
