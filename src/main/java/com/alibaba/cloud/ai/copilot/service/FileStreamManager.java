package com.alibaba.cloud.ai.copilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件流式写入管理器
 * 负责管理文件的流式写入过程，包括创建文件、分块写入、进度通知等
 */
@Service
public class FileStreamManager {

    private static final Logger logger = LoggerFactory.getLogger(FileStreamManager.class);

    // 默认块大小（字符数）
    private static final int DEFAULT_CHUNK_SIZE = 1024;

    // 进度通知间隔（字节数）
    private static final long PROGRESS_NOTIFICATION_INTERVAL = 4096;

    @Autowired
    private LogStreamService logStreamService;

    // 活跃的文件写入会话 filePath -> FileWriteSession
    private final Map<String, FileWriteSession> activeWriteSessions = new ConcurrentHashMap<>();

    /**
     * 开始流式文件写入
     * 先创建空文件，然后返回写入会话ID
     */
    public String startStreamingWrite(String taskId, String filePath, long estimatedTotalBytes) throws IOException {
        logger.info("🚀 开始流式文件写入: taskId={}, filePath={}, estimatedBytes={}", taskId, filePath, estimatedTotalBytes);

        Path path = Paths.get(filePath);
        boolean isNewFile = !Files.exists(path);

        // 确保父目录存在
        Files.createDirectories(path.getParent());

        // 创建空文件（如果是新文件）或清空现有文件
        Files.writeString(path, "", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // 创建写入会话
        FileWriteSession session = new FileWriteSession(taskId, filePath, estimatedTotalBytes);
        activeWriteSessions.put(filePath, session);

        // 通知前端文件已创建
        String message = isNewFile ?
            String.format("已创建新文件: %s", getRelativePath(path)) :
            String.format("已清空现有文件: %s", getRelativePath(path));
       // logStreamService.pushFileCreated(taskId, filePath, message);

        logger.info("✅ 文件创建成功: {}", filePath);
        return filePath; // 使用文件路径作为会话ID
    }

    /**
     * 写入内容块
     */
    public void writeContentChunk(String sessionId, String content) throws IOException {
        FileWriteSession session = activeWriteSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("写入会话不存在: " + sessionId);
        }

        logger.debug("📝 写入内容块: sessionId={}, chunkSize={}", sessionId, content.length());

        Path path = Paths.get(session.getFilePath());

        // 追加内容到文件
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        // 更新会话状态
        session.addWrittenBytes(content.getBytes(StandardCharsets.UTF_8).length);
        session.incrementChunkIndex();

        // 通知前端内容块写入
        logStreamService.pushFileContentChunk(
            session.getTaskId(),
            session.getFilePath(),
            content,
            session.getChunkIndex(),
            session.getEstimatedTotalBytes(),
            session.getWrittenBytes()
        );

        // 检查是否需要发送进度通知
        if (session.shouldSendProgressNotification()) {
            double progressPercent = session.getProgressPercent();
            logStreamService.pushFileWriteProgress(
                session.getTaskId(),
                session.getFilePath(),
                session.getEstimatedTotalBytes(),
                session.getWrittenBytes(),
                progressPercent
            );
            session.updateLastProgressNotification();
        }
    }

    /**
     * 完成流式写入
     */
    public void completeStreamingWrite(String sessionId) {
        FileWriteSession session = activeWriteSessions.remove(sessionId);
        if (session == null) {
            logger.warn("⚠️ 写入会话不存在: {}", sessionId);
            return;
        }

        long executionTime = System.currentTimeMillis() - session.getStartTime();

        logger.info("✅ 流式文件写入完成: sessionId={}, totalBytes={}, executionTime={}ms",
            sessionId, session.getWrittenBytes(), executionTime);

        // 通知前端写入完成
        logStreamService.pushFileWriteComplete(
            session.getTaskId(),
            session.getFilePath(),
            session.getWrittenBytes(),
            executionTime
        );
    }

    /**
     * 处理写入错误
     */
    public void handleWriteError(String sessionId, String errorMessage) {
        FileWriteSession session = activeWriteSessions.remove(sessionId);
        if (session == null) {
            logger.warn("⚠️ 写入会话不存在: {}", sessionId);
            return;
        }

        long executionTime = System.currentTimeMillis() - session.getStartTime();

        logger.error("❌ 流式文件写入失败: sessionId={}, error={}", sessionId, errorMessage);

        // 通知前端写入错误
        logStreamService.pushFileWriteError(
            session.getTaskId(),
            session.getFilePath(),
            errorMessage,
            executionTime
        );
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(Path filePath) {
        try {
            Path workspaceRoot = Paths.get(System.getProperty("user.dir"), "workspace");
            return workspaceRoot.relativize(filePath).toString();
        } catch (Exception e) {
            return filePath.toString();
        }
    }

    /**
     * 文件写入会话
     */
    private static class FileWriteSession {
        private final String taskId;
        private final String filePath;
        private final long estimatedTotalBytes;
        private final long startTime;
        private long writtenBytes = 0;
        private int chunkIndex = 0;
        private long lastProgressNotificationBytes = 0;

        public FileWriteSession(String taskId, String filePath, long estimatedTotalBytes) {
            this.taskId = taskId;
            this.filePath = filePath;
            this.estimatedTotalBytes = estimatedTotalBytes;
            this.startTime = System.currentTimeMillis();
        }

        public String getTaskId() { return taskId; }
        public String getFilePath() { return filePath; }
        public long getEstimatedTotalBytes() { return estimatedTotalBytes; }
        public long getStartTime() { return startTime; }
        public long getWrittenBytes() { return writtenBytes; }
        public int getChunkIndex() { return chunkIndex; }

        public void addWrittenBytes(long bytes) { this.writtenBytes += bytes; }
        public void incrementChunkIndex() { this.chunkIndex++; }

        public double getProgressPercent() {
            if (estimatedTotalBytes <= 0) return 0.0;
            return Math.min(100.0, (double) writtenBytes / estimatedTotalBytes * 100.0);
        }

        public boolean shouldSendProgressNotification() {
            return writtenBytes - lastProgressNotificationBytes >= PROGRESS_NOTIFICATION_INTERVAL;
        }

        public void updateLastProgressNotification() {
            this.lastProgressNotificationBytes = writtenBytes;
        }
    }
}
