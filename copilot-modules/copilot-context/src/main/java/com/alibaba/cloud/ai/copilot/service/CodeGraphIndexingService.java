package com.alibaba.cloud.ai.copilot.service;

import java.nio.file.Path;

/**
 * 受控的 CodeGraph 索引调度入口。
 *
 * <p>该服务只接受服务端解析出的会话工作区，并以后台任务执行 init/sync。它不会暴露给
 * Agent 工具，因此模型始终只能调用 {@link CodeGraphService} 提供的只读查询。</p>
 */
public interface CodeGraphIndexingService {

    /** 如有需要，异步为工作区建立索引；调用方不会等待索引完成。 */
    IndexStatus requestIndex(Path workspace);

    /** 在 Agent 执行完成后异步同步已有索引，或为首次生成的代码建立索引。 */
    IndexStatus requestSync(Path workspace);

    enum IndexStatus {
        AVAILABLE,
        INDEXING,
        SYNCING,
        NOT_A_CODE_PROJECT,
        DISABLED,
        FAILED;

        public boolean canUseQueryTool() {
            return this == AVAILABLE || this == INDEXING || this == SYNCING;
        }
    }
}
