package com.alibaba.cloud.ai.copilot.service;

import java.nio.file.Path;

/** 受控的 CodeGraph 查询入口；不暴露任意 Shell 或索引写操作。 */
public interface CodeGraphService {

    boolean isAvailable(Path workspace);

    String search(Path workspace, String query);

    String explore(Path workspace, String query);

    String impact(Path workspace, String symbol);

    String affectedTests(Path workspace, String relativePath);
}
