package com.alibaba.cloud.ai.example.copilot.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 基于模板的项目生成器
 * 负责复制模板项目并基于模板生成新项目
 */
@Service
public class TemplateBasedProjectGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TemplateBasedProjectGenerator.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private List<io.modelcontextprotocol.client.McpSyncClient> mcpSyncClients;

    private ChatClient chatClient;

    // 模板项目路径
    private static final String TEMPLATE_PATH = "project-template";

    // 生成的项目存放目录
    private static final String GENERATED_PROJECTS_DIR = "generated-projects";

    /**
     * 初始化ChatClient（带MCP工具支持）
     */
    private void initChatClient() {
        if (chatClient == null) {
            chatClient = chatClientBuilder
                    .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClients))
                    .build();
            logger.info("TemplateBasedProjectGenerator ChatClient初始化完成，已集成MCP工具");
        }
    }

    /**
     * 基于模板生成新项目
     *
     * @param projectName 项目名称
     * @param projectDescription 项目描述
     * @param customRequirements 自定义需求
     * @return 生成的项目路径
     */
    public String generateProjectFromTemplate(String projectName, String projectDescription, String customRequirements) {
        try {
            initChatClient();

            logger.info("开始基于模板生成项目: {}", projectName);

            // 1. 复制模板项目
            String newProjectPath = copyTemplateProject(projectName);
            logger.info("模板项目复制完成: {}", newProjectPath);

            // 2. 定制项目（使用安全的本地方法）
            customizeProjectSafely(newProjectPath, projectName, projectDescription, customRequirements);
            logger.info("项目定制完成: {}", newProjectPath);

            return newProjectPath;

        } catch (Exception e) {
            logger.error("基于模板生成项目失败: {}", projectName, e);
            throw new RuntimeException("生成项目失败: " + e.getMessage(), e);
        }
    }

    /**
     * 复制模板项目到新位置（公共方法，供TaskCoordinator调用）
     *
     * @param projectName 新项目名称
     * @return 新项目的绝对路径
     */
    public String copyTemplateProject(String projectName) throws IOException {
        // 创建时间戳，确保项目名称唯一
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uniqueProjectName = projectName + "-" + timestamp;

        Path templatePath = Paths.get(TEMPLATE_PATH);
        Path generatedProjectsDir = Paths.get(GENERATED_PROJECTS_DIR);
        Path newProjectPath = generatedProjectsDir.resolve(uniqueProjectName);

        // 确保生成项目的目录存在
        if (!Files.exists(generatedProjectsDir)) {
            Files.createDirectories(generatedProjectsDir);
            logger.info("创建生成项目目录: {}", generatedProjectsDir.toAbsolutePath());
        }

        // 检查模板项目是否存在
        if (!Files.exists(templatePath)) {
            throw new IOException("模板项目不存在: " + templatePath.toAbsolutePath());
        }

        // 复制整个模板目录
        copyDirectory(templatePath, newProjectPath);

        logger.info("模板项目复制成功: {} -> {}", templatePath.toAbsolutePath(), newProjectPath.toAbsolutePath());
        return newProjectPath.toAbsolutePath().toString();
    }

    /**
     * 递归复制目录（确保按正确顺序创建目录）
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                // 确保父目录存在后再创建子目录
                ensureDirectoryExists(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                // 确保文件的父目录存在
                ensureDirectoryExists(targetFile.getParent());
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 确保目录存在，如果不存在则逐级创建
     */
    private void ensureDirectoryExists(Path directory) throws IOException {
        if (directory == null || Files.exists(directory)) {
            return;
        }

        // 确保父目录先存在
        Path parent = directory.getParent();
        if (parent != null && !Files.exists(parent)) {
            ensureDirectoryExists(parent);
        }

        // 创建当前目录
        if (!Files.exists(directory)) {
            Files.createDirectory(directory);
            logger.debug("创建目录: {}", directory);
        }
    }

    /**
     * 安全的项目定制方法（不使用AI的MCP工具调用）
     * 直接在本地进行文件修改，避免路径访问问题
     */
    private void customizeProjectSafely(String projectPath, String projectName, String projectDescription, String customRequirements) {
        try {
            logger.info("开始安全定制项目，项目路径: {}", projectPath);

            Path projectDir = Paths.get(projectPath);

            // 1. 更新后端 pom.xml
            updatePomXml(projectDir, projectName, projectDescription);

            // 2. 更新前端 package.json
            updatePackageJson(projectDir, projectName, projectDescription);

            // 3. 更新 README.md
            updateReadme(projectDir, projectName, projectDescription, customRequirements);

            // 4. 更新后端主类
            updateMainClass(projectDir, projectName);

            // 5. 更新前端标题
            updateFrontendTitle(projectDir, projectName);

            logger.info("项目定制完成，所有文件已更新");

        } catch (Exception e) {
            logger.error("安全项目定制失败", e);
            throw new RuntimeException("项目定制失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新 pom.xml 文件
     */
    private void updatePomXml(Path projectDir, String projectName, String projectDescription) throws IOException {
        Path pomPath = projectDir.resolve("backend/pom.xml");
        if (Files.exists(pomPath)) {
            String content = Files.readString(pomPath);
            content = content.replace("spring-ai-chat-backend", projectName + "-backend");
            content = content.replace("基于Spring AI的基础聊天后端服务", projectDescription);
            Files.writeString(pomPath, content);
            logger.info("已更新 pom.xml");
        }
    }

    /**
     * 更新 package.json 文件
     */
    private void updatePackageJson(Path projectDir, String projectName, String projectDescription) throws IOException {
        Path packagePath = projectDir.resolve("frontend/package.json");
        if (Files.exists(packagePath)) {
            String content = Files.readString(packagePath);
            content = content.replace("spring-ai-chat-frontend", projectName + "-frontend");
            content = content.replace("基于Vue3的Spring AI聊天前端应用", projectDescription);
            Files.writeString(packagePath, content);
            logger.info("已更新 package.json");
        }
    }

    /**
     * 更新 README.md 文件
     */
    private void updateReadme(Path projectDir, String projectName, String projectDescription, String customRequirements) throws IOException {
        Path readmePath = projectDir.resolve("README.md");
        if (Files.exists(readmePath)) {
            String content = Files.readString(readmePath);
            content = content.replace("Spring AI + Vue3 基础对话模板", projectName);
            content = content.replace("这是一个基于 Spring AI + Vue3 的基础对话功能模板项目，提供了最简单的AI聊天功能实现。", projectDescription);

            if (customRequirements != null && !customRequirements.trim().isEmpty()) {
                content = content.replace("- **易于扩展**: 简洁的代码结构，便于在此基础上扩展更多功能",
                        "- **易于扩展**: 简洁的代码结构，便于在此基础上扩展更多功能\n- **定制需求**: " + customRequirements);
            }

            Files.writeString(readmePath, content);
            logger.info("已更新 README.md");
        }
    }

    /**
     * 更新后端主类
     */
    private void updateMainClass(Path projectDir, String projectName) throws IOException {
        Path mainClassPath = projectDir.resolve("backend/src/main/java/com/example/chat/ChatApplication.java");
        if (Files.exists(mainClassPath)) {
            String content = Files.readString(mainClassPath);
            String className = toCamelCase(projectName) + "Application";
            content = content.replace("ChatApplication", className);
            content = content.replace("Spring AI Chat Backend Started!", projectName + " Backend Started!");
            Files.writeString(mainClassPath, content);
            logger.info("已更新主类: {}", className);
        }
    }

    /**
     * 更新前端标题
     */
    private void updateFrontendTitle(Path projectDir, String projectName) throws IOException {
        Path indexPath = projectDir.resolve("frontend/index.html");
        if (Files.exists(indexPath)) {
            String content = Files.readString(indexPath);
            content = content.replace("Spring AI Chat - 基础聊天应用", projectName + " - AI聊天应用");
            content = content.replace("基于Spring AI和Vue3的基础聊天应用", projectName + "聊天应用");
            Files.writeString(indexPath, content);
            logger.info("已更新前端标题");
        }
    }

    /**
     * 转换为驼峰命名
     */
    private String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split("[\\s\\-_]+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    /**
     * 基础项目定制（公共方法，供TaskCoordinator调用）
     * 只进行基础的项目信息定制，不涉及复杂的AI功能扩展
     */
    public void customizeProjectBasics(String projectPath, String projectName, String projectDescription, String customRequirements) {
        try {
            logger.info("开始基础项目定制，项目路径: {}", projectPath);

            Path projectDir = Paths.get(projectPath);

            // 1. 更新后端 pom.xml
            updatePomXml(projectDir, projectName, projectDescription);

            // 2. 更新前端 package.json
            updatePackageJson(projectDir, projectName, projectDescription);

            // 3. 更新 README.md
            updateReadme(projectDir, projectName, projectDescription, customRequirements);

            // 4. 更新后端主类
            updateMainClass(projectDir, projectName);

            // 5. 更新前端标题
            updateFrontendTitle(projectDir, projectName);

            logger.info("基础项目定制完成");

        } catch (Exception e) {
            logger.error("基础项目定制失败", e);
            throw new RuntimeException("基础项目定制失败: " + e.getMessage(), e);
        }
    }
}
