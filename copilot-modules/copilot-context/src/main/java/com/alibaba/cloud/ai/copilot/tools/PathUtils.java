package com.alibaba.cloud.ai.copilot.tools;

/**
 * 路径工具类 - 统一处理不同操作系统的文件路径
 * 将绝对路径转换为相对于 workspace 目录的标准路径格式
 */
public class PathUtils {

    /**
     * 将文件路径转换为统一格式
     *
     * 示例：
     * - Windows: D:\project\alicode\copilot\workspace\index.html → workspace/index.html
     * - Linux: /pro/aicode/workspace/src/app.vue → workspace/src/app.vue
     *
     * @param filePath 原始文件路径（可能包含绝对路径）
     * @return 统一格式的路径（使用 / 分隔符，从 workspace 开始）
     */
    public static String normalizeWorkspacePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        // 1. 统一路径分隔符：将所有反斜杠替换为正斜杠
        String normalizedPath = filePath.replace("\\", "/");

        // 2. 查找 workspace 目录的位置
        int workspaceIndex = normalizedPath.indexOf("workspace");

        // 3. 如果包含 workspace，提取 workspace 及其之后的部分
        if (workspaceIndex != -1) {
            // 确保提取的是 "workspace" 而不是包含 "workspace" 的其他单词
            // 检查 workspace 前面的字符（应该是 / 或者在开头）
            if (workspaceIndex == 0 || normalizedPath.charAt(workspaceIndex - 1) == '/') {
                String result = normalizedPath.substring(workspaceIndex);
                // 移除开头的多余斜杠
                while (result.startsWith("/")) {
                    result = result.substring(1);
                }
                return result;
            }
        }

        // 4. 如果没找到 workspace，直接返回标准化后的路径
        return normalizedPath;
    }

    /**
     * 检查路径是否已经是标准格式（workspace/...）
     *
     * @param path 路径
     * @return true 如果已经是标准格式，false 否则
     */
    public static boolean isNormalizedPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return path.startsWith("workspace/") || path.equals("workspace");
    }

    /**
     * 获取 workspace 相对路径
     * 与 normalizeWorkspacePath 功能相同，但提供更语义化的方法名
     *
     * @param absolutePath 绝对路径
     * @return workspace 相对路径
     */
    public static String extractWorkspacePath(String absolutePath) {
        return normalizeWorkspacePath(absolutePath);
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 测试用例
        String[] testPaths = {
            "D:\\project\\alicode\\copilot\\workspace\\package.json",
            "D:\\project\\alicode\\copilot\\workspace\\index.html",
            "D:\\project\\alicode\\copilot\\workspace\\src\\main.js",
            "D:\\project\\alicode\\copilot\\workspace\\src\\App.vue",
            "/pro/aicode/workspace/index.html",
            "/pro/aicode/workspace/src/app.vue",
            "/workspace/index.html",
            "workspace/index.html",
            "/path/workspace/file.txt",
            "workspace",
            "/workspace"
        };

        System.out.println("路径转换测试：");
        for (String path : testPaths) {
            String normalized = normalizeWorkspacePath(path);
            System.out.println("原始: " + path);
            System.out.println("转换: " + normalized);
            System.out.println();
        }
    }
}