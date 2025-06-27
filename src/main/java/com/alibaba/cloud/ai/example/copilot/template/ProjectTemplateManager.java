package com.alibaba.cloud.ai.example.copilot.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 项目模板管理器
 * 负责管理和生成各种项目模板
 */
@Component
public class ProjectTemplateManager {

    private static final Logger logger = LoggerFactory.getLogger(ProjectTemplateManager.class);

    private static final String GENERATED_PROJECTS_DIR = "generated-projects";

    /**
     * 生成Spring Boot + Vue3项目模板
     * @param projectName 项目名称
     * @param packageName 包名
     * @return 生成的项目路径
     */
    public String generateSpringVueTemplate(String projectName, String packageName) {
        logger.info("生成Spring Boot + Vue3项目模板: {}", projectName);

        try {
            Path projectPath = createProjectDirectory(projectName);

            // 生成后端结构
            generateBackendStructure(projectPath, projectName, packageName);

            // 生成前端结构
            generateFrontendStructure(projectPath, projectName);

            // 生成配置文件
            generateConfigFiles(projectPath, projectName, packageName);

            logger.info("项目模板生成完成: {}", projectPath.toAbsolutePath());
            return projectPath.toAbsolutePath().toString();

        } catch (Exception e) {
            logger.error("生成项目模板失败", e);
            throw new RuntimeException("生成项目模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建项目目录
     */
    private Path createProjectDirectory(String projectName) throws IOException {
        Path baseDir = Paths.get(GENERATED_PROJECTS_DIR);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        Path projectPath = baseDir.resolve(projectName);
        if (Files.exists(projectPath)) {
            // 如果项目已存在，添加时间戳
            String timestamp = String.valueOf(System.currentTimeMillis());
            projectPath = baseDir.resolve(projectName + "-" + timestamp);
        }

        Files.createDirectories(projectPath);
        return projectPath;
    }

    /**
     * 生成后端项目结构
     */
    private void generateBackendStructure(Path projectPath, String projectName, String packageName) throws IOException {
        String packagePath = packageName.replace(".", "/");

        // 创建目录结构
        Path srcMain = projectPath.resolve("src/main");
        Path srcMainJava = srcMain.resolve("java").resolve(packagePath);
        Path srcMainResources = srcMain.resolve("resources");

        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);
        Files.createDirectories(projectPath.resolve("src/test/java").resolve(packagePath));

        // 生成主类
        generateMainClass(srcMainJava, projectName, packageName);

        // 生成控制器
        generateController(srcMainJava, packageName);

        // 生成服务类
        generateService(srcMainJava, packageName);

        // 生成配置文件
        generateApplicationProperties(srcMainResources);
    }

    /**
     * 生成前端项目结构
     */
    private void generateFrontendStructure(Path projectPath, String projectName) throws IOException {
        Path frontendPath = projectPath.resolve("frontend");
        Files.createDirectories(frontendPath);

        // 生成package.json
        generatePackageJson(frontendPath, projectName);

        // 生成基础前端文件
        generateFrontendFiles(frontendPath);
    }

    /**
     * 生成配置文件
     */
    private void generateConfigFiles(Path projectPath, String projectName, String packageName) throws IOException {
        // 生成pom.xml
        generatePomXml(projectPath, projectName, packageName);

        // 生成README.md
        generateReadme(projectPath, projectName);

        // 生成.gitignore
        generateGitignore(projectPath);
    }

    /**
     * 生成主类
     */
    private void generateMainClass(Path javaPath, String projectName, String packageName) throws IOException {
        String className = toCamelCase(projectName) + "Application";
        String content = String.format("""
            package %s;
            
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            
            @SpringBootApplication
            public class %s {
                public static void main(String[] args) {
                    SpringApplication.run(%s.class, args);
                }
            }
            """, packageName, className, className);

        Files.writeString(javaPath.resolve(className + ".java"), content);
    }

    /**
     * 生成控制器
     */
    private void generateController(Path javaPath, String packageName) throws IOException {
        Path controllerPath = javaPath.resolve("controller");
        Files.createDirectories(controllerPath);

        String content = String.format("""
            package %s.controller;
            
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            @RequestMapping("/api")
            public class HelloController {
                
                @GetMapping("/hello")
                public String hello() {
                    return "Hello from AI Generated Project!";
                }
            }
            """, packageName);

        Files.writeString(controllerPath.resolve("HelloController.java"), content);
    }

    /**
     * 生成服务类
     */
    private void generateService(Path javaPath, String packageName) throws IOException {
        Path servicePath = javaPath.resolve("service");
        Files.createDirectories(servicePath);

        String content = String.format("""
            package %s.service;
            
            import org.springframework.stereotype.Service;
            
            @Service
            public class HelloService {
                
                public String getGreeting() {
                    return "Hello from AI Generated Service!";
                }
            }
            """, packageName);

        Files.writeString(servicePath.resolve("HelloService.java"), content);
    }

    /**
     * 生成application.properties
     */
    private void generateApplicationProperties(Path resourcesPath) throws IOException {
        String content = """
            # Server Configuration
            server.port=8080
            
            # Application Configuration
            spring.application.name=ai-generated-project
            
            # Logging Configuration
            logging.level.root=INFO
            logging.level.com.example=DEBUG
            """;

        Files.writeString(resourcesPath.resolve("application.properties"), content);
    }

    /**
     * 生成package.json
     */
    private void generatePackageJson(Path frontendPath, String projectName) throws IOException {
        String content = String.format("""
            {
              "name": "%s-frontend",
              "version": "1.0.0",
              "description": "Frontend for %s",
              "scripts": {
                "dev": "vite",
                "build": "vite build",
                "preview": "vite preview"
              },
              "dependencies": {
                "vue": "^3.4.0",
                "ant-design-vue": "^4.0.0",
                "axios": "^1.6.0"
              },
              "devDependencies": {
                "@vitejs/plugin-vue": "^5.0.0",
                "vite": "^5.0.0"
              }
            }
            """, projectName, projectName);

        Files.writeString(frontendPath.resolve("package.json"), content);
    }

    /**
     * 生成前端文件
     */
    private void generateFrontendFiles(Path frontendPath) throws IOException {
        // 创建src目录
        Path srcPath = frontendPath.resolve("src");
        Files.createDirectories(srcPath);

        // 生成main.js
        String mainJs = """
            import { createApp } from 'vue'
            import Antd from 'ant-design-vue'
            import 'ant-design-vue/dist/reset.css'
            import App from './App.vue'
            
            const app = createApp(App)
            app.use(Antd)
            app.mount('#app')
            """;
        Files.writeString(srcPath.resolve("main.js"), mainJs);

        // 生成App.vue
        String appVue = """
            <template>
              <div id="app">
                <a-layout style="min-height: 100vh">
                  <a-layout-header>
                    <h1 style="color: white; margin: 0;">AI Generated Project</h1>
                  </a-layout-header>
                  <a-layout-content style="padding: 24px">
                    <a-card title="Welcome">
                      <p>This is an AI generated project!</p>
                      <a-button type="primary" @click="sayHello">Say Hello</a-button>
                    </a-card>
                  </a-layout-content>
                </a-layout>
              </div>
            </template>
            
            <script setup>
            import { message } from 'ant-design-vue'
            
            const sayHello = () => {
              message.success('Hello from AI Generated Frontend!')
            }
            </script>
            """;
        Files.writeString(srcPath.resolve("App.vue"), appVue);

        // 生成index.html
        String indexHtml = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>AI Generated Project</title>
              </head>
              <body>
                <div id="app"></div>
                <script type="module" src="/src/main.js"></script>
              </body>
            </html>
            """;
        Files.writeString(frontendPath.resolve("index.html"), indexHtml);
    }

    /**
     * 生成pom.xml
     */
    private void generatePomXml(Path projectPath, String projectName, String packageName) throws IOException {
        String content = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <name>%s</name>
                <description>AI Generated Spring Boot Project</description>
                
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                    <relativePath/>
                </parent>
                
                <properties>
                    <java.version>17</java.version>
                </properties>
                
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """, packageName, projectName, projectName);

        Files.writeString(projectPath.resolve("pom.xml"), content);
    }

    /**
     * 生成README.md
     */
    private void generateReadme(Path projectPath, String projectName) throws IOException {
        String content = String.format("""
            # %s
            
            This is an AI generated project created by AI Copilot.
            
            ## Getting Started
            
            ### Backend
            ```bash
            mvn spring-boot:run
            ```
            
            ### Frontend
            ```bash
            cd frontend
            npm install
            npm run dev
            ```
            
            ## Features
            
            - Spring Boot backend
            - Vue3 + Ant Design Vue frontend
            - RESTful API
            - Modern development stack
            
            ## API Endpoints
            
            - GET /api/hello - Hello endpoint
            
            ## Development
            
            This project was generated using AI Copilot, an intelligent coding assistant.
            """, projectName);

        Files.writeString(projectPath.resolve("README.md"), content);
    }

    /**
     * 生成.gitignore
     */
    private void generateGitignore(Path projectPath) throws IOException {
        String content = """
            # Compiled class file
            *.class
            
            # Log file
            *.log
            
            # Maven
            target/
            
            # IDE
            .idea/
            *.iml
            .vscode/
            
            # OS
            .DS_Store
            Thumbs.db
            
            # Node.js
            node_modules/
            npm-debug.log*
            yarn-debug.log*
            yarn-error.log*
            
            # Build output
            dist/
            build/
            """;

        Files.writeString(projectPath.resolve(".gitignore"), content);
    }

    /**
     * 转换为驼峰命名
     */
    private String toCamelCase(String str) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : str.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            } else {
                capitalizeNext = true;
            }
        }

        return result.toString();
    }
}
