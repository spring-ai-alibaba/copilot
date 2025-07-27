# 🤖 AI编码助手 (Spring AI Alibaba Copilot)

基于Spring AI框架构建的智能编码助手，集成MCP工具协议，支持项目分析、代码生成、智能编辑和项目脚手架等功能。通过自然语言交互，帮助开发者快速完成各种编程任务。

## ✨ 项目亮点

- 🧠 **智能项目分析**: 自动识别项目类型、分析依赖关系和代码结构
- 🛠️ **多工具集成**: 集成文件操作、项目脚手架、智能编辑等多种工具
- 🔄 **连续任务执行**: 支持复杂任务的分步执行和状态跟踪
- 📡 **实时反馈**: 基于SSE的实时日志流和任务状态更新
- 🎯 **模板驱动**: 支持多种项目类型的快速脚手架生成

## 🚀 核心功能

### 🔍 项目分析工具
- **项目类型检测**: 自动识别Java Maven、Spring Boot、Node.js、React、Python等项目类型
- **依赖分析**: 解析pom.xml、package.json、requirements.txt等依赖文件
- **结构分析**: 分析项目目录结构、文件统计和代码度量
- **配置文件发现**: 自动发现和分析各类配置文件

### 📝 智能编辑工具
- **文件操作**: 读取、写入、编辑文件，支持大文件分页处理
- **智能编辑**: 基于自然语言描述进行精确的代码修改
- **差异预览**: 提供编辑前后的详细差异对比
- **目录管理**: 递归目录列表、结构浏览

## 🏗️ 技术架构

### 后端技术栈
- **Spring Boot 3.4.5**: 应用框架
- **Spring AI 1.0.0**: AI集成框架，支持多种LLM模型
- **MCP Client**: Model Context Protocol客户端集成
- **AspectJ**: AOP切面编程，用于工具调用监控
- **Jackson**: JSON处理
- **Java Diff Utils**: 文件差异比较

### 前端技术栈
- **原生JavaScript**: 轻量级前端实现
- **Server-Sent Events**: 实时数据推送
- **CSS3**: 现代化UI样式
- **Thymeleaf**: 服务端模板引擎


### 🖼️ 界面展示(演示效果基于qwen-plus模型)

#### 聊天交互界面
<p align="center">
    <img src="./docs/imgs/chat-01.png" alt="聊天界面-主页" width="45%" />
    <img src="./docs/imgs/chat-02.png" alt="聊天界面-对话" width="45%" />
</p>

<p align="center">
    <img src="./docs/imgs/chat-03.png" alt="聊天界面-工具执行" width="45%" />
    <img src="./docs/imgs/chat-04.png" alt="聊天界面-结果展示" width="45%" />
</p>

## 📋 系统要求

- **Java 17+**: 核心运行环境
- **Maven 3.6+**: 项目构建工具
- **Git**: 版本控制（可选）
- **Node.js 16+**: MCP工具运行环境（可选）

## 🛠️ 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/springaialibaba/spring-ai-alibaba-copilot.git
cd spring-ai-alibaba-copilot
```

### 2. 配置AI模型
编辑 `src/main/resources/application.yml` 配置文件：

```yaml
spring:
  ai:
    openai:
      # 配置您的AI模型API
      base-url: https://dashscope.aliyuncs.com
      api-key: your-api-key-here
      chat:
        options:
          model: your-model-name  # 如: qwen-plus, deepseek-v3等
```

### 3. 配置工作目录
```yaml
app:
  workspace:
    root-directory: ${user.dir}/workspace  # 工作目录路径
    max-file-size: 10485760  # 最大文件大小 (10MB)
    allowed-extensions:  # 允许的文件扩展名
      - .txt
      - .md
      - .java
      - .js
      - .json
      # ... 更多扩展名
```

### 4. 启动应用
```bash
# 使用Maven启动
mvn spring-boot:run

# 或者先编译再运行
mvn clean package
java -jar target/spring-ai-alibaba-copilot-1.0.0.jar
```

### 5. 访问应用
- 应用会自动在浏览器中打开: http://localhost:8080
- 如果未自动打开，请手动访问上述地址

## 🎯 使用指南

### 📝 使用示例

#### 项目分析
```
分析当前工作目录下的项目结构和依赖关系
```

#### 创建新项目
```
创建一个Spring Boot项目，包含REST API和数据库配置
```

#### 文件操作
```
读取src/main/java/Application.java文件的内容
```

#### 智能编辑
```
在Application.java中添加一个新的REST控制器方法
```

## ⚙️ 详细配置

### 📁 工作目录配置
```yaml
app:
  workspace:
    root-directory: ${user.dir}/workspace  # 工作目录
    max-file-size: 10485760  # 最大文件大小限制
    allowed-extensions:  # 允许操作的文件类型
      - .txt
      - .md
      - .java
      - .js
      - .ts
      - .json
      - .xml
      - .yml
      - .yaml
      - .properties
      - .html
      - .css
      - .sql
```

### 🛠️ 工具配置
```yaml
app:
  tools:
    read-file:
      enabled: true
      max-lines-per-read: 1000  # 单次读取最大行数
    write-file:
      enabled: true
      backup-enabled: true  # 是否启用文件备份
    edit-file:
      enabled: true
      diff-context-lines: 3  # 差异显示上下文行数
    list-directory:
      enabled: true
      max-depth: 5  # 目录遍历最大深度
```

### 🔒 安全配置
```yaml
app:
  security:
    approval-mode: DEFAULT  # DEFAULT, AUTO_EDIT, YOLO
    dangerous-commands:  # 危险命令列表
      - rm
      - del
      - format
      - fdisk
      - mkfs
```

### 🌐 浏览器配置
```yaml
app:
  browser:
    auto-open: true  # 启动后自动打开浏览器
    url: http://localhost:${server.port:8080}
    delay-seconds: 2  # 延迟打开时间
```

## 🔍 项目类型支持

| 项目类型 | 检测文件 | 依赖分析 | 脚手架支持 |
|---------|---------|---------|-----------|
| Java Maven | pom.xml | ✅ | ✅ |
| Spring Boot | pom.xml + @SpringBootApplication | ✅ | ✅ |
| Node.js | package.json | ✅ | ✅ |
| React | package.json + react依赖 | ✅ | ✅ |
| Vue | package.json + vue依赖 | ✅ | ✅ |
| Python | requirements.txt/setup.py | ✅ | ✅ |
| Django | manage.py | ✅ | ❌ |
| Flask | app.py | ✅ | ❌ |
| Go | go.mod | ✅ | ❌ |
| Rust | Cargo.toml | ✅ | ❌ |


## 🤝 贡献指南

### 贡献流程
1. Fork 项目到您的GitHub账户
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI集成框架
- [Model Context Protocol](https://modelcontextprotocol.io/) - 工具协议标准

---

<p align="center">
  <strong>🌟 如果这个项目对您有帮助，请给我们一个Star！</strong>
</p>
