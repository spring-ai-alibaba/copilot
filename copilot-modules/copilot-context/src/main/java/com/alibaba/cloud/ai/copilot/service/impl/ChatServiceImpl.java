package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.config.AppProperties;
import com.alibaba.cloud.ai.copilot.domain.dto.ChatRequest;
import com.alibaba.cloud.ai.copilot.handler.OutputHandlerRegistry;
import com.alibaba.cloud.ai.copilot.service.ChatService;
import com.alibaba.cloud.ai.copilot.service.DynamicModelService;
import com.alibaba.cloud.ai.copilot.tools.ListDirectoryTool;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.EditFileTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.ReadFileTool;
import com.alibaba.cloud.ai.graph.agent.extension.tools.filesystem.WriteFileTool;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;


/**
 * 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AppProperties appProperties;

    private final DynamicModelService dynamicModelService;

    private final OutputHandlerRegistry outputHandlerRegistry;

    @Override
    public void handleBuilderMode(ChatRequest request, String userId, SseEmitter emitter) {
        try {
            // 初始化 ChatModel
            ChatModel chatModel = dynamicModelService.getChatModelWithConfigId(request.getModelConfigId());

            ReactAgent agent = ReactAgent.builder()
                    .name("copilot_agent")
                    .model(chatModel)
                    .tools(ListDirectoryTool.createListDirectoryToolCallback(ListDirectoryTool.DESCRIPTION),
                            EditFileTool.createEditFileToolCallback(EditFileTool.DESCRIPTION),
                            ReadFileTool.createReadFileToolCallback(ReadFileTool.DESCRIPTION),
                            WriteFileTool.createWriteFileToolCallback(WriteFileTool.DESCRIPTION)
                    )
                    .systemPrompt("工作目录在:"+appProperties.getWorkspace().getRootDirectory()+"所有的文件操作请在这个目录下进行")
                    .saver(new MemorySaver())
                    .build();

            Flux<NodeOutput> stream = agent.stream(request.getMessage().getContent());

            stream.subscribe(
                    output -> {
                        // 使用处理器注册中心统一处理，直接传递 emitter
                        if (output instanceof StreamingOutput streamingOutput) {
                            outputHandlerRegistry.handle(streamingOutput, emitter);
                        }
                    },
                    error -> {
                        System.err.println("错误: " + error);
                    },
                    () -> {
                        System.out.println("Agent 执行完成");
                    }
            );
        } catch (GraphRunnerException e) {
            log.error("Error in builder mode", e);
            throw new RuntimeException(e);
        }
    }

}