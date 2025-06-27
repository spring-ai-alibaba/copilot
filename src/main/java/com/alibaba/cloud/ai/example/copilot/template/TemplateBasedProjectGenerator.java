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
     * 使用AI和MCP工具定制项目
     */
    private void customizeProject(String projectPath, String projectName, String projectDescription, String customRequirements) {
        try {
            logger.info("开始定制项目，项目路径: {}", projectPath);

            // 验证项目路径是否在允许的目录内
            if (!isPathAllowed(projectPath)) {
                throw new SecurityException("项目路径不在允许的目录内: " + projectPath);
            }

            // 构建AI提示词
            String prompt = buildCustomizationPrompt(projectPath, projectName, projectDescription, customRequirements);

            logger.info("调用AI进行项目定制...");

            // 调用AI进行项目定制
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.info("AI项目定制完成，结果: {}", result);

        } catch (Exception e) {
            logger.error("项目定制失败，项目路径: {}", projectPath, e);

            // 检查是否是路径访问错误
            if (e.getMessage() != null && e.getMessage().contains("Access denied")) {
                throw new RuntimeException("项目定制失败: AI尝试访问不被允许的目录。请确保所有操作都在项目目录内进行。", e);
            } else if (e.getMessage() != null && e.getMessage().contains("path outside allowed directories")) {
                throw new RuntimeException("项目定制失败: 路径访问被拒绝。AI只能在指定的项目目录内操作文件。", e);
            } else {
                throw new RuntimeException("项目定制失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 验证路径是否在允许的目录内
     */
    private boolean isPathAllowed(String projectPath) {
        try {
            Path path = Paths.get(projectPath).toAbsolutePath().normalize();
            Path allowedBase = Paths.get("C:\\project\\spring-ai-alibaba-copilot").toAbsolutePath().normalize();
            return path.startsWith(allowedBase);
        } catch (Exception e) {
            logger.warn("路径验证失败: {}", projectPath, e);
            return false;
        }
    }

    /**
     * 构建项目定制的AI提示词
     */
    private String buildCustomizationPrompt(String projectPath, String projectName, String projectDescription, String customRequirements) {
        return String.format("""
            你是一个专业的项目生成助手。我已经为你准备了一个基础的Spring AI + Vue3聊天应用模板项目。
            现在需要你基于用户的需求对这个模板项目进行定制。

            ## 项目信息
            - 项目名称: %s
            - 项目描述: %s
            - 项目路径: %s
            - 自定义需求: %s

            ## 🚨 重要安全限制 🚨
            **你只能在以下目录内操作文件**：
            - 工作目录：C:\\project\\spring-ai-alibaba-copilot
            - 项目目录：%s

            **绝对禁止**：
            - 不要在 C: 盘创建任何文件或目录
            - 不要在桌面或其他系统目录操作
            - 所有文件操作必须在指定的项目路径内进行
            - 使用相对路径，基于项目根目录

            ## 你的任务
            请使用可用的MCP工具（特别是文件系统工具）来定制这个项目：

            1. **更新项目配置**:
               - 修改 %s/backend/pom.xml 中的项目名称和描述
               - 更新 %s/frontend/package.json 中的项目信息
               - 修改 %s/README.md 文件，添加项目特定的说明

            2. **定制代码内容**:
               - 根据项目描述修改后端的主类名和包名
               - 更新前端的标题和界面文本
               - 根据自定义需求添加或修改功能

            3. **添加特定功能**:
               - 如果用户有特殊需求，请相应地修改代码
               - 保持Spring AI + Vue3的基础架构不变
               - 确保聊天功能正常工作

            ## 可用工具
            你可以使用以下MCP工具来操作文件：
            - read_file: 读取文件内容（使用完整路径：%s/文件名）
            - write_file: 写入文件内容（使用完整路径：%s/文件名）
            - edit_file: 编辑文件内容（使用完整路径：%s/文件名）
            - list_directory: 列出目录内容（使用完整路径：%s）
            - create_directory: 创建目录（仅在项目目录内）
            - move_file: 移动或重命名文件（仅在项目目录内）

            ## 路径示例
            正确的路径格式：
            - %s/backend/pom.xml
            - %s/frontend/package.json
            - %s/README.md

            ## 注意事项
            - 保持项目的基本结构和功能不变
            - 确保所有修改都是有意义的和正确的
            - 如果某些需求不合理或无法实现，请说明原因
            - 完成后请总结你做了哪些修改
            - **严格遵守路径限制，不要尝试访问项目目录外的任何文件**

            请开始定制项目，并在完成后提供详细的修改报告。
            """, projectName, projectDescription, projectPath, customRequirements,
                projectPath, projectPath, projectPath, projectPath,
                projectPath, projectPath, projectPath, projectPath,
                projectPath, projectPath, projectPath);
    }

    /**
     * 获取所有生成的项目列表
     */
    public List<String> getGeneratedProjects() {
        try {
            Path generatedProjectsDir = Paths.get(GENERATED_PROJECTS_DIR);
            if (!Files.exists(generatedProjectsDir)) {
                return List.of();
            }

            return Files.list(generatedProjectsDir)
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();

        } catch (IOException e) {
            logger.error("获取生成项目列表失败", e);
            return List.of();
        }
    }

    /**
     * 删除生成的项目
     */
    public boolean deleteGeneratedProject(String projectName) {
        try {
            Path projectPath = Paths.get(GENERATED_PROJECTS_DIR).resolve(projectName);
            if (Files.exists(projectPath)) {
                deleteDirectory(projectPath);
                logger.info("删除生成的项目: {}", projectPath.toAbsolutePath());
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.error("删除项目失败: {}", projectName, e);
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
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
