package com.alibaba.cloud.ai.copilot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;

/**
 * 应用配置属性
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Workspace workspace = new Workspace();
    private Security security = new Security();
    private Tools tools = new Tools();
    private Conversation conversation = new Conversation();


    /**
     * 工作空间配置
     */
    public static class Workspace {
        // 使用 Paths.get() 和 File.separator 实现跨平台兼容
        private String rootDirectory = Paths.get(System.getProperty("user.dir"), "workspace").toString();
        private long maxFileSize = 10485760L; // 10MB
        private List<String> allowedExtensions = List.of(
            ".txt", ".md", ".java", ".js", ".ts", ".json", ".xml",
            ".yml", ".yaml", ".properties", ".html", ".css", ".sql"
        );

        // Getters and Setters
        public String getRootDirectory() { return rootDirectory; }
        public void setRootDirectory(String rootDirectory) {
            // 确保设置的路径也是跨平台兼容的
            this.rootDirectory = Paths.get(rootDirectory).toString();
        }

        public long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

        public List<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    }

    /**
     * 安全配置
     */
    public static class Security {
        private ApprovalMode approvalMode = ApprovalMode.DEFAULT;
        private List<String> dangerousCommands = List.of("rm", "del", "format", "fdisk", "mkfs");

        // Getters and Setters
        public ApprovalMode getApprovalMode() { return approvalMode; }
        public void setApprovalMode(ApprovalMode approvalMode) { this.approvalMode = approvalMode; }

        public List<String> getDangerousCommands() { return dangerousCommands; }
        public void setDangerousCommands(List<String> dangerousCommands) { this.dangerousCommands = dangerousCommands; }
    }

    /**
     * 工具配置
     */
    public static class Tools {
        private ToolConfig readFile = new ToolConfig(true);
        private ToolConfig writeFile = new ToolConfig(true);
        private ToolConfig editFile = new ToolConfig(true);
        private ToolConfig listDirectory = new ToolConfig(true);
        private ToolConfig shell = new ToolConfig(true);

        // Getters and Setters
        public ToolConfig getReadFile() { return readFile; }
        public void setReadFile(ToolConfig readFile) { this.readFile = readFile; }

        public ToolConfig getWriteFile() { return writeFile; }
        public void setWriteFile(ToolConfig writeFile) { this.writeFile = writeFile; }

        public ToolConfig getEditFile() { return editFile; }
        public void setEditFile(ToolConfig editFile) { this.editFile = editFile; }

        public ToolConfig getListDirectory() { return listDirectory; }
        public void setListDirectory(ToolConfig listDirectory) { this.listDirectory = listDirectory; }

        public ToolConfig getShell() { return shell; }
        public void setShell(ToolConfig shell) { this.shell = shell; }
    }

    /**
     * 工具配置
     */
    public static class ToolConfig {
        private boolean enabled;

        public ToolConfig() {}
        public ToolConfig(boolean enabled) { this.enabled = enabled; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 会话配置
     */
    public static class Conversation {
        /**
         * 消息压缩配置
         */
        private Summarization summarization = new Summarization();

        public Summarization getSummarization() {
            return summarization;
        }

        public void setSummarization(Summarization summarization) {
            this.summarization = summarization;
        }

        /**
         * 消息压缩配置
         */
        public static class Summarization {
            /**
             * 超过多少 tokens 时触发压缩（默认：4000）
             */
            private int maxTokensBeforeSummary = 4000;

            /**
             * 压缩后保留最近多少条消息（默认：20）
             */
            private int messagesToKeep = 20;

            public int getMaxTokensBeforeSummary() {
                return maxTokensBeforeSummary;
            }

            public void setMaxTokensBeforeSummary(int maxTokensBeforeSummary) {
                this.maxTokensBeforeSummary = maxTokensBeforeSummary;
            }

            public int getMessagesToKeep() {
                return messagesToKeep;
            }

            public void setMessagesToKeep(int messagesToKeep) {
                this.messagesToKeep = messagesToKeep;
            }
        }
    }

    /**
     * 审批模式
     */
    public enum ApprovalMode {
        DEFAULT,    // 默认模式，危险操作需要确认
        AUTO_EDIT,  // 自动编辑模式，文件编辑不需要确认
        YOLO        // 完全自动模式，所有操作都不需要确认
    }
}
