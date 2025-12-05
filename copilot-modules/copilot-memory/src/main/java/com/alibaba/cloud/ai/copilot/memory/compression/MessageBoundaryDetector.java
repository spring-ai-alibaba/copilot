package com.alibaba.cloud.ai.copilot.memory.compression;

import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息边界检测器
 * 智能检测压缩边界，在用户消息处切割，避免破坏对话完整性
 *
 * @author better
 */
@Slf4j
@Component
public class MessageBoundaryDetector {

    private final ObjectMapper objectMapper;

    public MessageBoundaryDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 查找压缩边界
     * 在用户消息处切割，避免破坏对话完整性
     *
     * @param messages 消息列表
     * @param preserveThreshold 保留比例（0.3 = 保留 30%）
     * @return 边界索引（从该索引开始保留）
     */
    public int findCompressionBoundary(List<Message> messages, double preserveThreshold) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        // 1. 计算总字符数（JSON 序列化）
        int totalChars = 0;
        int[] charCounts = new int[messages.size()];
        
        for (int i = 0; i < messages.size(); i++) {
            try {
                String json = objectMapper.writeValueAsString(messages.get(i));
                charCounts[i] = json.length();
                totalChars += charCounts[i];
            } catch (Exception e) {
                log.warn("Failed to serialize message {} for boundary detection", i, e);
                charCounts[i] = 0;
            }
        }

        // 2. 计算目标分割点（70% 的位置）
        int targetChars = (int) (totalChars * (1 - preserveThreshold));

        // 3. 累计查找初始分割点
        int accumulated = 0;
        int splitIndex = 0;

        for (int i = 0; i < messages.size(); i++) {
            accumulated += charCounts[i];
            if (accumulated >= targetChars) {
                splitIndex = i;
                break;
            }
        }

        // 4. 向后调整到用户消息边界
        while (splitIndex < messages.size()) {
            Message msg = messages.get(splitIndex);

            // 确保是用户消息且不是工具响应
            if ("user".equals(msg.getRole()) && !isFunctionResponse(msg)) {
                log.debug("Found compression boundary at index {} (user message)", splitIndex);
                return splitIndex;
            }

            splitIndex++;
        }

        // 如果找不到用户消息边界，返回最后一个索引
        int finalIndex = Math.min(splitIndex, messages.size());
        log.warn("Could not find user message boundary, using index {}", finalIndex);
        return finalIndex;
    }

    /**
     * 判断是否为工具响应消息
     */
    private boolean isFunctionResponse(Message message) {
        if (message == null || message.getMetadata() == null) {
            return false;
        }
        
        // 检查元数据中是否标记为工具响应
        String source = message.getMetadata().getSource();
        return "function".equals(source) || "tool".equals(message.getRole());
    }
}

