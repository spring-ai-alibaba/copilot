package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.Message;

import java.util.List;
import java.util.Map;

/**
 * File processor service interface
 */
public interface FileProcessorService {

    /**
     * Process files from messages
     */
    ProcessedFiles processFiles(List<Message> messages, boolean clearText);

    /**
     * Parse message content for files
     */
    ParsedMessage parseMessage(String content);

    /**
     * Processed files result
     */
    class ProcessedFiles {
        private final Map<String, String> files;
        private final String allContent;

        public ProcessedFiles(Map<String, String> files, String allContent) {
            this.files = files;
            this.allContent = allContent;
        }

        public Map<String, String> getFiles() {
            return files;
        }

        public String getAllContent() {
            return allContent;
        }
    }

    /**
     * Parsed message result
     */
    class ParsedMessage {
        private final String content;
        private final Map<String, String> files;

        public ParsedMessage(String content, Map<String, String> files) {
            this.content = content;
            this.files = files;
        }

        public String getContent() {
            return content;
        }

        public Map<String, String> getFiles() {
            return files;
        }
    }
}
