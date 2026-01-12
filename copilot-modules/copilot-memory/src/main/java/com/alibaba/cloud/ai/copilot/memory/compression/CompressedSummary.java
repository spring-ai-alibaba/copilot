package com.alibaba.cloud.ai.copilot.memory.compression;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩摘要模型
 * 存储压缩后的对话摘要信息
 *
 * @author better
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressedSummary {

    /**
     * 主要讨论的话题列表
     */
    @Builder.Default
    private List<String> mainTopics = new ArrayList<>();

    /**
     * 关键决策列表
     */
    @Builder.Default
    private List<String> keyDecisions = new ArrayList<>();

    /**
     * 代码上下文（文件路径 -> 功能描述）
     */
    @Builder.Default
    private List<CodeContext> codeContexts = new ArrayList<>();

    /**
     * 用户需求和偏好
     */
    @Builder.Default
    private List<String> userRequirements = new ArrayList<>();

    /**
     * 未完成的任务
     */
    @Builder.Default
    private List<String> pendingTasks = new ArrayList<>();

    /**
     * 技术细节和配置信息
     */
    @Builder.Default
    private List<String> technicalDetails = new ArrayList<>();

    /**
     * 原始消息数量
     */
    private int originalMessageCount;

    /**
     * 代码上下文
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeContext {
        private String filePath;
        private String description;
    }

    /**
     * 转换为 XML 格式
     */
    public String toXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<conversation_summary>\n");

        // 主要话题
        if (!mainTopics.isEmpty()) {
            xml.append("  <main_topics>\n");
            for (String topic : mainTopics) {
                xml.append("    - ").append(topic).append("\n");
            }
            xml.append("  </main_topics>\n\n");
        }

        // 关键决策
        if (!keyDecisions.isEmpty()) {
            xml.append("  <key_decisions>\n");
            for (String decision : keyDecisions) {
                xml.append("    <decision>").append(decision).append("</decision>\n");
            }
            xml.append("  </key_decisions>\n\n");
        }

        // 代码上下文
        if (!codeContexts.isEmpty()) {
            xml.append("  <code_context>\n");
            for (CodeContext context : codeContexts) {
                xml.append("    <file path=\"").append(context.getFilePath())
                   .append("\">").append(context.getDescription()).append("</file>\n");
            }
            xml.append("  </code_context>\n\n");
        }

        // 用户需求
        if (!userRequirements.isEmpty()) {
            xml.append("  <user_requirements>\n");
            for (String requirement : userRequirements) {
                xml.append("    <requirement>").append(requirement).append("</requirement>\n");
            }
            xml.append("  </user_requirements>\n\n");
        }

        // 未完成任务
        if (!pendingTasks.isEmpty()) {
            xml.append("  <pending_tasks>\n");
            for (String task : pendingTasks) {
                xml.append("    <task>").append(task).append("</task>\n");
            }
            xml.append("  </pending_tasks>\n\n");
        }

        // 技术细节
        if (!technicalDetails.isEmpty()) {
            xml.append("  <technical_details>\n");
            for (String detail : technicalDetails) {
                xml.append("    <detail>").append(detail).append("</detail>\n");
            }
            xml.append("  </technical_details>\n");
        }

        xml.append("</conversation_summary>");
        return xml.toString();
    }
}

