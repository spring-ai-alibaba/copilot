# AI编码助手 (Alibaba Copilot)

基于Spring AI Alibaba框架构建的智能编码助手，集成MCP工具协议，支持项目分析、代码生成、智能编辑和项目脚手架等功能。通过自然语言交互，帮助开发者快速完成各种编程任务。

## 📺 演示视频

![演示视频](docs/demo.gif)

## 技术架构
- **Spring Boot 3.4.5**: 应用框架
- **Spring AI 1.0.0**: AI集成框架，支持多种LLM模型
- **MCP Client**: Model Context Protocol客户端集成
- **AspectJ**: AOP切面编程，用于工具调用监控
- **Jackson**: JSON处理
- **Java Diff Utils**: 文件差异比较

## 系统要求
- **Java 17+**: 核心运行环境
- **Maven 3.6+**: 项目构建工具
- **Node.js 20+**: 前端开发环境
- **MySQL 8.0+**: 数据库
- **Git**: 版本控制（可选）
- **阿里云通义千问 API Key**（或 OpenAI API Key）

## 🚀 快速入门

### 第一步：克隆项目

```bash
git clone https://github.com/alibaba/spring-ai-alibaba-copilot.git
cd spring-ai-alibaba-copilot
```

### 第二步：配置数据库

```sql
-- 创建数据库
CREATE DATABASE spring_ai_copilot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入初始化脚本（如果有）
mysql -u root -p spring_ai_copilot < scripts/sql/init.sql
```

### 第三步：配置后端

编辑 `copilot-admin/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spring_ai_copilot
    username: root
    password: your_password

  ai:
    dashscope:
      api-key: sk-your-dashscope-api-key  # 替换为你的 API Key
```

### 第四步：启动后端

```bash
# 编译并启动
mvn clean install
mvn spring-boot:run -pl copilot-admin
```

看到以下信息表示启动成功：

```
(♥◠‿◠)ﾉﾞ  Alibaba Copilot启动成功   ლ(´ڡ`ლ)ﾞ
```

后端地址：`http://localhost:6039`

### 第五步：启动前端

```bash
cd ui-react

# 安装依赖
pnpm install

# 启动开发服务器
pnpm run dev
```

前端地址：`http://localhost:5173`

**默认登录账号：**
- 用户名：`admin`
- 密码：`admin123`

## 📁 项目结构

```
spring-ai-alibaba-copilot/
├── copilot-admin/          # 🚪 启动入口
├── copilot-modules/        # 📦 业务模块
│   ├── copilot-conversation/   # 💬 对话管理
│   ├── copilot-context/        # 🔍 上下文分析
│   ├── copilot-rag/            # 📚 知识库
│   └── copilot-prompt/         # 📝 提示词管理
├── copilot-common/         # 🛠️ 通用工具
└── ui-react/               # 🎨 前端界面
    ├── src/components/AiChat/  # 聊天组件
    ├── src/components/WeIde/   # IDE 组件
    └── src/api/                # API 接口
```

## 🐛 常见问题

### Q1: 后端启动失败，提示数据库连接错误

**A:** 检查以下几点：
1. MySQL 是否已启动
2. 数据库名称、用户名、密码是否正确
3. 数据库是否已创建

```bash
# 检查 MySQL 状态
systemctl status mysql

# 或
mysql -u root -p -e "SHOW DATABASES;"
```

### Q2: 前端无法连接后端

**A:** 确认：
1. 后端是否已启动（访问 http://localhost:6039）
2. 检查浏览器控制台是否有 CORS 错误
3. 检查 `.env.local` 配置

```env
# ui-react/.env.local
APP_BASE_URL=http://localhost:6039
```

### Q3: AI 不响应或响应很慢

**A:** 可能原因：
1. API Key 无效或额度不足
2. 网络连接问题
3. 模型服务繁忙

检查日志：
```bash
tail -f copilot-admin/logs/spring.log
```

### Q4: WebContainer 无法启动

**A:** WebContainer 需要：
1. 使用 Chrome/Edge 浏览器（最新版本）
2. 启用 SharedArrayBuffer（需要特定的 HTTP headers）

开发环境已配置，如果仍有问题，检查浏览器控制台错误。

## 贡献指南

### 贡献流程
1. Fork 项目到您的GitHub账户
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI集成框架
- [Model Context Protocol](https://modelcontextprotocol.io/) - 工具协议标准

---

<p align="center">
  <strong>🌟 如果这个项目对您有帮助，请给我们一个Star！</strong>
</p>
