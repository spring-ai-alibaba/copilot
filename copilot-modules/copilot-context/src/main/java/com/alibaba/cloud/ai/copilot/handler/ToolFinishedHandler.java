package com.alibaba.cloud.ai.copilot.handler;

import com.alibaba.cloud.ai.copilot.domain.dto.StateDataDTO;
import com.alibaba.cloud.ai.copilot.service.SseEventService;
import com.alibaba.cloud.ai.copilot.tools.PathUtils;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用完成处理器
 * 处理 AGENT_TOOL_FINISHED 类型的输出
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolFinishedHandler implements OutputTypeHandler {

    private final SseEventService sseEventService;

    // 伪流式输出配置
    private static final int CHUNK_SIZE = 100; // 每次发送的字符数
    private static final int DELAY_MS = 50; // 每次发送间隔毫秒数

    @Override
    public OutputType getOutputType() {
        return OutputType.AGENT_TOOL_FINISHED;
    }

    @Override
    public void handle(StreamingOutput output, SseEmitter emitter) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Object stateData = output.state().data();
            if (stateData != null) {
                StateDataDTO data = objectMapper.convertValue(stateData, StateDataDTO.class);
                List<StateDataDTO.MessageDTO> messages = data.getMessages();
                for (StateDataDTO.MessageDTO message : messages) {
                    if ("ASSISTANT".equals(message.getMessageType()) && message.getToolCalls() != null) {
                        message.getToolCalls().forEach(toolCall -> {
                            if (toolCall.getArguments() != null) {
                                String filePath = toolCall.getArguments().getFilePath();
                                String content = toolCall.getArguments().getContent();

                                if (filePath != null && !filePath.isEmpty()) {
                                    // 统一路径格式（转换为 workspace/xxx 的标准格式）
                                    String normalizedPath = PathUtils.normalizeWorkspacePath(filePath);
                                    log.info("原始路径: {}", filePath);
                                    log.info("转换后路径: {}", normalizedPath);

                                    // 使用伪流式输出发送内容
                                    sendContentWithPseudoStreaming(emitter, normalizedPath, content);
                                }
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Error] Failed to convert state data to StateDataDTO: {}", e.getMessage(), e);
        }
    }

    /**
     * 伪流式输出内容
     * 当内容超过100字符时，分块发送以模拟流式效果
     */
    private void sendContentWithPseudoStreaming(SseEmitter emitter, String filePath, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        // 如果内容长度小于等于100字符，直接发送
        if (content.length() <= CHUNK_SIZE) {
            sseEventService.sendFileEditProgress(emitter, filePath, content);
            return;
        }

        // 异步执行伪流式输出
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting pseudo streaming output for file: {}, content length: {}", filePath, content.length());

                // 分块发送内容
                StringBuilder accumulatedContent = new StringBuilder();
                int totalLength = content.length();

                for (int i = 0; i < totalLength; i += CHUNK_SIZE) {
                    int endIndex = Math.min(i + CHUNK_SIZE, totalLength);
                    String chunk = content.substring(i, endIndex);
                    accumulatedContent.append(chunk);

                    // 发送累积的内容（模拟逐步构建文件的效果）
                    sseEventService.sendFileEditProgress(emitter, filePath, accumulatedContent.toString());

                    log.debug("Sent chunk {}/{}, accumulated length: {}",
                             (i / CHUNK_SIZE) + 1,
                             (totalLength + CHUNK_SIZE - 1) / CHUNK_SIZE,
                             accumulatedContent.length());

                    // 如果不是最后一块，添加延迟
                    if (endIndex < totalLength) {
                        Thread.sleep(DELAY_MS);
                    }
                }

                log.info("Completed pseudo streaming output for file: {}", filePath);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Pseudo streaming interrupted for file: {}", filePath);
            } catch (Exception e) {
                log.error("Error during pseudo streaming for file: {}", filePath, e);
                // 发生错误时，发送完整内容作为备用
                sseEventService.sendFileEditProgress(emitter, filePath, content);
            }
        });
    }
}