# Spring AI + Vue3 基础对话模板

这是一个基于 Spring AI + Vue3 的基础对话功能模板项目，提供了最简单的AI聊天功能实现。

## 🚀 项目特性

### 核心功能
- **基础AI对话**: 支持与AI模型进行简单的问答对话
- **流式响应**: 通过SSE提供实时的流式响应
- **现代化界面**: 基于Vue3 + Ant Design Vue的响应式聊天界面
- **易于扩展**: 简洁的代码结构，便于在此基础上扩展更多功能

### 技术架构
- **后端**: Spring Boot 3.x + Spring AI
- **前端**: Vue3 + Ant Design Vue + TypeScript
- **通信**: RESTful API + Server-Sent Events (SSE)
- **AI模型**: 支持多种LLM模型（通过Spring AI）

## 📋 系统要求

- Java 17+
- Node.js 16+
- Maven 3.6+

## 🛠️ 快速开始

### 1. 克隆项目
```bash
git clone <your-repository-url>
cd spring-ai-vue3-chat-template
```

### 2. 配置AI模型
在 `backend/src/main/resources/application.properties` 中配置AI模型：

```properties
# 阿里云通义千问配置
spring.ai.openai.base-url=https://dashscope.aliyuncs.com/compatible-mode
spring.ai.openai.api-key=your-api-key
spring.ai.openai.chat.options.model=qwen-plus

# 或者使用OpenAI
# spring.ai.openai.base-url=https://api.openai.com
# spring.ai.openai.api-key=your-openai-api-key
# spring.ai.openai.chat.options.model=gpt-3.5-turbo
```

### 3. 启动后端服务
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

### 4. 启动前端应用
```bash
cd frontend
npm install
npm run dev
```

前端应用将在 http://localhost:5173 启动

### 5. 访问应用
打开浏览器访问 http://localhost:5173，即可开始与AI进行对话。

## 📁 项目结构

```
spring-ai-vue3-chat-template/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/
│   │   └── com/example/chat/
│   │       ├── ChatApplication.java
│   │       ├── controller/
│   │       │   └── ChatController.java
│   │       ├── service/
│   │       │   ├── ChatService.java
│   │       │   └── SseService.java
│   │       └── model/
│   │           ├── ChatMessage.java
│   │           └── ChatResponse.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
├── frontend/                   # Vue3 前端
│   ├── src/
│   │   ├── components/
│   │   │   ├── ChatComponent.vue
│   │   │   ├── MessageList.vue
│   │   │   └── MessageInput.vue
│   │   ├── services/
│   │   │   └── ChatApiService.ts
│   │   ├── types/
│   │   │   └── chat.ts
│   │   ├── App.vue
│   │   └── main.ts
│   ├── package.json
│   └── vite.config.ts
└── README.md
```

## 🔧 API接口

### 发送消息
```http
POST /api/chat/send
Content-Type: application/json

{
  "message": "你好，请介绍一下自己"
}
```

### SSE流式响应
```http
GET /api/chat/stream/{conversationId}
```

## 🎯 功能说明

### 后端功能
1. **ChatController**: 处理聊天请求和SSE连接
2. **ChatService**: 核心聊天逻辑，调用Spring AI
3. **SseService**: 管理SSE连接和消息推送
4. **数据模型**: 定义消息和响应的数据结构

### 前端功能
1. **ChatComponent**: 主聊天界面组件
2. **MessageList**: 消息列表显示组件
3. **MessageInput**: 消息输入组件
4. **ChatApiService**: API调用服务
5. **类型定义**: TypeScript类型定义

## 🔍 扩展指南

### 添加新功能
1. **消息历史**: 可以添加数据库存储聊天历史
2. **多轮对话**: 支持上下文相关的多轮对话
3. **文件上传**: 支持图片、文档等文件上传
4. **用户管理**: 添加用户认证和会话管理
5. **主题切换**: 支持明暗主题切换

### 自定义配置
- 修改 `application.properties` 中的AI模型配置
- 在 `ChatService` 中自定义提示词和参数
- 在前端组件中自定义UI样式和交互逻辑

## 📞 技术支持

如果您在使用过程中遇到问题，请：
1. 检查Java和Node.js版本是否符合要求
2. 确认AI模型配置是否正确
3. 查看控制台日志获取错误信息
4. 参考Spring AI官方文档

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。
