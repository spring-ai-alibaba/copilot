package com.alibaba.cloud.ai.copilot.service;

import java.util.List;
import java.util.Map;

/**
 * 文件系统服务接口
 * 用于管理AI生成文件的真实存储和访问
 */
public interface FileSystemService {

    /**
     * 为会话创建工作目录
     */
    String createSessionWorkspace(String conversationId, String userId);

    /**
     * 保存文件到工作目录
     */
    void saveFile(String workspacePath, String filePath, String content);

    /**
     * 批量保存文件
     */
    void saveFiles(String workspacePath, Map<String, String> files);

    /**
     * 读取文件内容
     */
    String readFile(String workspacePath, String filePath);

    /**
     * 获取工作目录下的所有文件
     */
    Map<String, String> getAllFiles(String workspacePath);

    /**
     * 删除工作目录
     */
    void deleteWorkspace(String workspacePath);

    /**
     * 获取工作目录信息
     */
    WorkspaceInfo getWorkspaceInfo(String workspacePath);

    /**
     * 检查文件系统功能是否启用
     */
    boolean isFileSystemEnabled();

    /**
     * 工作目录信息
     */
    class WorkspaceInfo {
        private final String path;
        private final List<String> files;
        private final long totalSize;
        private final long createTime;

        public WorkspaceInfo(String path, List<String> files, long totalSize, long createTime) {
            this.path = path;
            this.files = files;
            this.totalSize = totalSize;
            this.createTime = createTime;
        }

        public String getPath() {
            return path;
        }

        public List<String> getFiles() {
            return files;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getCreateTime() {
            return createTime;
        }
    }
}