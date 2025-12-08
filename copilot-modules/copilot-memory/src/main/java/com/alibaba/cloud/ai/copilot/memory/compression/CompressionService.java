package com.alibaba.cloud.ai.copilot.memory.compression;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;

import java.util.List;

/**
 * 压缩服务接口
 * 负责将对话历史压缩成结构化摘要
 *
 * @author better
 */
public interface CompressionService {

    /**
     * 压缩消息列表
     *
     * @param messages 要压缩的消息列表
     * @param modelName 用于压缩的模型名称
     * @return 压缩摘要
     */
    CompressedSummary compressMessages(List<Message> messages, String modelName);
}

