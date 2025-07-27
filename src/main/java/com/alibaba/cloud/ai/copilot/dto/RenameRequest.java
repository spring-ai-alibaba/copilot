package com.alibaba.cloud.ai.copilot.dto;

/**
 * 重命名请求 DTO
 */
public class RenameRequest {
    private String oldPath;
    private String newPath;

    public RenameRequest() {}

    public RenameRequest(String oldPath, String newPath) {
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public void setNewPath(String newPath) {
        this.newPath = newPath;
    }
}
