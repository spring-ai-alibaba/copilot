package com.alibaba.cloud.ai.copilot.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystemWithShell;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import io.agentscope.harness.agent.workspace.PathPolicy;

import java.nio.file.Path;
import java.util.List;

/**
 * 会话级文件沙箱：修补 agentscope-harness 2.0.0 ROOTED 模式的相对路径逃逸。
 *
 * <p>框架的 LocalFilesystem#resolveRooted 对不带 "/" 前缀的相对路径直接
 * cwd.resolve(path).normalize() 返回，未做包含性校验，因此 "../其他会话/xxx"
 * 可越出沙箱根读写其他用户的文件（实测复现）。本类在 resolvePath 之后
 * 强制校验解析结果必须位于沙箱根内，作为多租户隔离的最终闸门。</p>
 */
public class SessionSandboxFilesystem extends LocalFilesystemWithShell {

    private final Path root;

    public SessionSandboxFilesystem(Path root) {
        super(root, LocalFsMode.ROOTED, PathPolicy.of(List.of(root)),
                120, 100_000, null, false,
                IsolationScope.GLOBAL.toNamespaceFactory(), root);
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    protected Path resolvePath(RuntimeContext context, String path) {
        Path resolved = super.resolvePath(context, path).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("路径越出会话工作目录: " + path);
        }
        return resolved;
    }
}
