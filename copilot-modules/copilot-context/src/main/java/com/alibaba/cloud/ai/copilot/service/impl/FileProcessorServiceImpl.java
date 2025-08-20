package com.alibaba.cloud.ai.copilot.service.impl;

import com.alibaba.cloud.ai.copilot.model.Message;
import com.alibaba.cloud.ai.copilot.service.FileProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File processor service implementation
 */
@Slf4j
@Service
public class FileProcessorServiceImpl implements FileProcessorService {

    private static final List<String> EXCLUDE_FILES = Arrays.asList(
        "components/weicon/base64.js",
        "components/weicon/icon.css",
        "components/weicon/index.js",
        "components/weicon/index.json",
        "components/weicon/index.wxml",
        "components/weicon/icondata.js",
        "components/weicon/index.css",
        "/miniprogram/components/weicon/base64.js",
        "/miniprogram/components/weicon/icon.css",
        "/miniprogram/components/weicon/index.js",
        "/miniprogram/components/weicon/index.json",
        "/miniprogram/components/weicon/index.wxml",
        "/miniprogram/components/weicon/icondata.js",
        "/miniprogram/components/weicon/index.css"
    );

    @Override
    public ProcessedFiles processFiles(List<Message> messages, boolean clearText) {
        Map<String, String> files = new HashMap<>();
        StringBuilder allContent = new StringBuilder();

        for (Message message : messages) {
            allContent.append(message.getContent());
            ParsedMessage parsedMessage = parseMessage(message.getContent());
            
            if (clearText) {
                message.setContent(parsedMessage.getContent());
            }
            
            if (parsedMessage.getFiles() != null) {
                // Remove excluded files
                Map<String, String> filteredFiles = new HashMap<>(parsedMessage.getFiles());
                EXCLUDE_FILES.forEach(filteredFiles::remove);
                files.putAll(filteredFiles);
            }
        }

        return new ProcessedFiles(files, allContent.toString());
    }

    @Override
    public ParsedMessage parseMessage(String content) {
        // Pattern to match boltArtifact tags
        Pattern artifactPattern = Pattern.compile("<boltArtifact[^>]*>([\\s\\S]*?)</boltArtifact>");
        
        if (artifactPattern.matcher(content).find()) {
            Matcher artifactMatcher = artifactPattern.matcher(content);
            if (artifactMatcher.find()) {
                String artifactContent = artifactMatcher.group(1).trim();
                
                // Parse file contents from boltAction tags
                Map<String, String> files = new HashMap<>();
                Pattern boltActionPattern = Pattern.compile(
                    "<boltAction type=\"file\" filePath=\"([^\"]+)\">([\\s\\S]*?)</boltAction>");
                
                Matcher boltMatcher = boltActionPattern.matcher(artifactContent);
                while (boltMatcher.find()) {
                    String filePath = boltMatcher.group(1);
                    String fileContent = boltMatcher.group(2).trim();
                    
                    if (!EXCLUDE_FILES.contains(filePath)) {
                        files.put(filePath, fileContent);
                    }
                }
                
                // Replace artifact with summary
                String newContent = content.replaceAll(
                    "<boltArtifact[^>]*>[\\s\\S]*?</boltArtifact>",
                    "已经修改好了的目录" + new ArrayList<>(files.keySet())
                );
                
                return new ParsedMessage(newContent.trim(), files);
            }
        }
        
        // If no boltArtifact found, return original content
        return new ParsedMessage(content, null);
    }
}
