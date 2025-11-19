# SSE 流式传输协议文档

## 📋 协议概述

本文档定义了基于 Server-Sent Events (SSE) 的前后端流式传输协议，用于实时传输文件操作和命令执行事件。

### 协议特点

- **协议类型**：Server-Sent Events (SSE)
- **通信方向**：单向（服务器 → 客户端）
- **传输方式**：HTTP/HTTPS 长连接
- **数据格式**：JSON
- **编码方式**：UTF-8

### 适用场景

- 文件操作流式传输（添加/编辑/删除）
- 命令执行结果流式输出
- 实时进度更新
- 服务器主动推送事件

---

## 🔌 连接建立

### 前端连接方式

```typescript
// 使用 EventSource API
const eventSource = new EventSource('http://localhost:8080/api/sse', {
  withCredentials: true  // 可选：发送 cookies
});

// 监听消息
eventSource.onmessage = (event) => {
  // 处理消息
};

// 监听错误
eventSource.onerror = (error) => {
  // 处理错误（浏览器会自动重连）
};

// 关闭连接
eventSource.close();
```

### 后端响应要求

1. **Content-Type**：`text/event-stream`
2. **Cache-Control**：`no-cache`
3. **Connection**：`keep-alive`
4. **CORS**：需要设置适当的 CORS 头（如果跨域）

### 后端示例（Node.js/Express）

```javascript
app.get('/api/sse', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('Access-Control-Allow-Origin', '*');
  
  // 发送心跳保持连接
  const heartbeat = setInterval(() => {
    res.write(': heartbeat\n\n');
  }, 30000);
  
  // 清理资源
  req.on('close', () => {
    clearInterval(heartbeat);
    res.end();
  });
});
```

---

## 📨 消息格式规范

### SSE 协议格式

SSE 使用文本流格式，每条消息由以下部分组成：

```
event: <事件名称>
data: <JSON 数据>
id: <可选的消息ID>
retry: <可选的重连间隔（毫秒）>

```

**格式说明：**
- `event`：事件类型（必需）
- `data`：JSON 格式的数据（必需）
- `id`：消息ID（可选，用于断线重连）
- `retry`：重连间隔（可选，单位：毫秒）
- 消息之间用**两个换行符**（`\n\n`）分隔

### 消息结构

每条消息的 `data` 字段包含以下 JSON 结构：

```typescript
interface SSEMessage {
  event: EventName;           // 事件名称
  data: OperationData;        // 操作数据
  messageId?: string;         // 消息ID（可选）
  operationId?: string;       // 操作ID（可选）
  error?: string;             // 错误信息（可选）
}
```

---

## 🎯 事件类型定义

### 支持的事件类型

| 事件名称 | 说明 | 阶段 |
|---------|------|------|
| `add-start` | 文件添加开始 | start |
| `add-progress` | 文件添加进度 | progress |
| `add-end` | 文件添加结束 | end |
| `edit-start` | 文件编辑开始 | start |
| `edit-progress` | 文件编辑进度 | progress |
| `edit-end` | 文件编辑结束 | end |
| `delete-start` | 文件删除开始 | start |
| `delete-progress` | 文件删除进度 | progress |
| `delete-end` | 文件删除结束 | end |
| `cmd` | 命令执行 | - |

### 事件命名规则

- 文件操作事件：`{操作类型}-{阶段}`
  - 操作类型：`add`、`edit`、`delete`
  - 阶段：`start`、`progress`、`end`
- 命令执行事件：`cmd`（无阶段区分）

---

## 📦 数据格式规范

### 基础数据结构

```typescript
interface BaseOperationData {
  type: 'add' | 'edit' | 'delete' | 'cmd';  // 操作类型
  content?: string;                          // 内容（文件内容或命令输出）
  timestamp?: number;                         // 时间戳（毫秒）
  messageId?: string;                        // 消息ID
  operationId?: string;                      // 操作ID
}
```

### 文件操作数据

```typescript
interface FileOperationData extends BaseOperationData {
  type: 'add' | 'edit' | 'delete';
  filePath: string;        // 文件路径（必需）
  content: string;         // 文件内容（必需，删除操作可为空）
  encoding?: string;       // 文件编码（可选，默认：utf-8）
  mode?: string;           // 文件权限（可选）
}
```

### 命令操作数据

```typescript
interface CommandOperationData extends BaseOperationData {
  type: 'cmd';
  command: string;                    // 命令（必需）
  workingDir?: string;                // 工作目录（可选）
  env?: Record<string, string>;       // 环境变量（可选）
  exitCode?: number;                  // 退出码（可选）
  stdout?: string;                     // 标准输出（可选）
  stderr?: string;                     // 标准错误（可选）
}
```

---

## 📝 完整消息示例

### 1. 文件添加操作

#### add-start 事件

**SSE 格式：**
```
event: add-start
data: {"event":"add-start","messageId":"msg_123","operationId":"op_456","data":{"type":"add","filePath":"src/index.js","content":""}}

```

**JSON 数据：**
```json
{
  "event": "add-start",
  "messageId": "msg_123",
  "operationId": "op_456",
  "data": {
    "type": "add",
    "filePath": "src/index.js",
    "content": ""
  }
}
```

#### add-progress 事件

**SSE 格式：**
```
event: add-progress
data: {"event":"add-progress","messageId":"msg_123","operationId":"op_456","data":{"type":"add","filePath":"src/index.js","content":"console.log('Hello');\nconsole.log('World');"}}

```

**JSON 数据：**
```json
{
  "event": "add-progress",
  "messageId": "msg_123",
  "operationId": "op_456",
  "data": {
    "type": "add",
    "filePath": "src/index.js",
    "content": "console.log('Hello');\nconsole.log('World');"
  }
}
```

#### add-end 事件

**SSE 格式：**
```
event: add-end
data: {"event":"add-end","messageId":"msg_123","operationId":"op_456","data":{"type":"add","filePath":"src/index.js","content":"console.log('Hello World');\nconsole.log('Done');"}}

```

**JSON 数据：**
```json
{
  "event": "add-end",
  "messageId": "msg_123",
  "operationId": "op_456",
  "data": {
    "type": "add",
    "filePath": "src/index.js",
    "content": "console.log('Hello World');\nconsole.log('Done');"
  }
}
```

### 2. 文件编辑操作

#### edit-start 事件

```json
{
  "event": "edit-start",
  "messageId": "msg_123",
  "operationId": "op_789",
  "data": {
    "type": "edit",
    "filePath": "src/index.js",
    "content": ""
  }
}
```

#### edit-progress 事件

```json
{
  "event": "edit-progress",
  "messageId": "msg_123",
  "operationId": "op_789",
  "data": {
    "type": "edit",
    "filePath": "src/index.js",
    "content": "// 新的内容...\nconsole.log('Updated');"
  }
}
```

#### edit-end 事件

```json
{
  "event": "edit-end",
  "messageId": "msg_123",
  "operationId": "op_789",
  "data": {
    "type": "edit",
    "filePath": "src/index.js",
    "content": "console.log('Updated');\nconsole.log('Done');"
  }
}
```

### 3. 文件删除操作

#### delete-start 事件

```json
{
  "event": "delete-start",
  "messageId": "msg_123",
  "operationId": "op_101",
  "data": {
    "type": "delete",
    "filePath": "src/old.js"
  }
}
```

#### delete-progress 事件

```json
{
  "event": "delete-progress",
  "messageId": "msg_123",
  "operationId": "op_101",
  "data": {
    "type": "delete",
    "filePath": "src/old.js"
  }
}
```

#### delete-end 事件

```json
{
  "event": "delete-end",
  "messageId": "msg_123",
  "operationId": "op_101",
  "data": {
    "type": "delete",
    "filePath": "src/old.js"
  }
}
```

### 4. 命令执行

#### cmd 事件

```json
{
  "event": "cmd",
  "messageId": "msg_123",
  "operationId": "op_202",
  "data": {
    "type": "cmd",
    "command": "npm install",
    "workingDir": "/project",
    "env": {
      "NODE_ENV": "development"
    },
    "stdout": "Installing packages...",
    "stderr": "",
    "exitCode": 0
  }
}
```

---

## 🔄 完整流程示例

### 场景：创建并编辑文件

**后端发送的事件序列：**

```
event: add-start
data: {"event":"add-start","messageId":"msg_001","operationId":"op_001","data":{"type":"add","filePath":"src/app.js","content":""}}

event: add-progress
data: {"event":"add-progress","messageId":"msg_001","operationId":"op_001","data":{"type":"add","filePath":"src/app.js","content":"const express = require('express');"}}

event: add-progress
data: {"event":"add-progress","messageId":"msg_001","operationId":"op_001","data":{"type":"add","filePath":"src/app.js","content":"const express = require('express');\nconst app = express();"}}

event: add-end
data: {"event":"add-end","messageId":"msg_001","operationId":"op_001","data":{"type":"add","filePath":"src/app.js","content":"const express = require('express');\nconst app = express();\n\napp.listen(3000);"}}

event: edit-start
data: {"event":"edit-start","messageId":"msg_001","operationId":"op_002","data":{"type":"edit","filePath":"src/app.js","content":""}}

event: edit-progress
data: {"event":"edit-progress","messageId":"msg_001","operationId":"op_002","data":{"type":"edit","filePath":"src/app.js","content":"const express = require('express');\nconst app = express();\n\n// 添加路由\napp.get('/', (req, res) => {\n  res.send('Hello World');\n});\n\napp.listen(3000);"}}

event: edit-end
data: {"event":"edit-end","messageId":"msg_001","operationId":"op_002","data":{"type":"edit","filePath":"src/app.js","content":"const express = require('express');\nconst app = express();\n\n// 添加路由\napp.get('/', (req, res) => {\n  res.send('Hello World');\n});\n\napp.listen(3000);"}}

```

---

## 🔑 字段说明

### messageId

- **类型**：`string`
- **必需**：否（建议提供）
- **说明**：标识一条完整的消息或会话
- **用途**：
  - 关联同一会话中的所有操作
  - 状态管理和追踪
  - 错误恢复和重试
- **生成规则**：建议使用唯一标识符，如 `msg_${timestamp}_${random}`

### operationId

- **类型**：`string`
- **必需**：否（建议提供）
- **说明**：标识一个具体的操作
- **用途**：
  - 关联同一操作的多个阶段（start/progress/end）
  - 操作状态追踪
  - 超时和错误处理
- **生成规则**：建议使用唯一标识符，如 `op_${timestamp}_${random}`
- **关联规则**：同一操作的 `start`、`progress`、`end` 事件应使用相同的 `operationId`

### filePath

- **类型**：`string`
- **必需**：是（文件操作）
- **说明**：文件路径
- **规则**：
  - 使用相对路径或绝对路径
  - 路径分隔符使用 `/`（Unix 风格）
  - 避免使用 `..` 和 `~`（安全考虑）
- **示例**：
  - `src/index.js`
  - `public/index.html`
  - `/project/src/utils.ts`

### content

- **类型**：`string`
- **必需**：是（add/edit 操作），否（delete 操作）
- **说明**：文件内容或命令输出
- **编码**：UTF-8
- **规则**：
  - 文件内容使用换行符 `\n` 分隔行
  - 支持多行文本
  - 删除操作时可为空字符串

---

## ⚠️ 错误处理

### 错误消息格式

```json
{
  "event": "error",
  "messageId": "msg_123",
  "operationId": "op_456",
  "error": "文件路径无效",
  "data": {
    "type": "add",
    "filePath": "../invalid/path.js"
  }
}
```

### 常见错误类型

1. **文件路径错误**
   - 路径不存在
   - 路径无效（包含 `..` 或 `~`）
   - 权限不足

2. **内容错误**
   - 内容过大（超过限制）
   - 编码错误
   - 格式错误

3. **命令执行错误**
   - 命令不存在
   - 执行失败
   - 超时

### 错误处理建议

- 所有错误都应通过 `error` 字段返回
- 错误消息应清晰明确
- 建议包含错误码和错误描述
- 操作失败时，应发送对应的 `-end` 事件，标记操作结束

---

## 🚀 最佳实践

### 后端实现建议

1. **消息发送**
   - 及时发送 `start` 事件，通知客户端操作开始
   - 定期发送 `progress` 事件，更新进度
   - 操作完成后必须发送 `end` 事件

2. **ID 管理**
   - 为每个消息生成唯一的 `messageId`
   - 为每个操作生成唯一的 `operationId`
   - 同一操作的多个阶段使用相同的 `operationId`

3. **内容传输**
   - 大文件内容建议分块传输（多次 `progress` 事件）
   - 避免单次传输过大的内容（建议 < 1MB）
   - 使用增量更新（只传输变化部分）

4. **连接管理**
   - 定期发送心跳（`:` 开头的注释行）保持连接
   - 处理客户端断开连接的情况
   - 及时清理资源

### 前端实现建议

1. **消息解析**
   - 解析 `event` 字段获取事件类型
   - 解析 `data` 字段获取 JSON 数据
   - 处理 `messageId` 和 `operationId` 进行状态管理

2. **状态管理**
   - 使用 `messageId` 管理消息状态
   - 使用 `operationId` 管理操作状态
   - 及时清理完成的操作状态

3. **错误处理**
   - 监听 `error` 事件
   - 处理网络错误和解析错误
   - 实现重连机制（EventSource 自动重连）

4. **性能优化**
   - 避免频繁更新 UI
   - 使用防抖/节流处理 `progress` 事件
   - 及时清理不需要的状态

---

## 📊 消息大小限制

### 建议限制

- **单条消息**：< 1MB
- **文件内容**：< 10MB（建议分块传输）
- **命令输出**：< 5MB（建议流式输出）

### 超限处理

- 大文件内容应分多次 `progress` 事件传输
- 命令输出应实时流式传输，避免一次性传输
- 超过限制时，应返回错误消息

---

## 🔐 安全考虑

### 路径验证

- 禁止使用相对路径（`..`）
- 禁止使用用户目录（`~`）
- 建议使用白名单机制限制可操作路径
- 验证路径长度（建议 < 260 字符）

### 命令验证

- 禁止执行危险命令（如 `rm -rf`、`format` 等）
- 建议使用白名单机制限制可执行命令
- 验证命令长度（建议 < 10000 字符）

### 内容验证

- 验证文件内容大小
- 验证文件编码
- 防止路径遍历攻击

---

## 📚 参考实现

### 前端实现

- `sseMessageParser.tsx` - SSE 消息解析器
- `useSseMessageParser.tsx` - 使用示例
- `sseConnectionManager.tsx` - 连接管理器

### 后端实现示例（Node.js）

```javascript
function sendSSEEvent(res, event, data) {
  const message = JSON.stringify({
    event,
    ...data
  });
  
  res.write(`event: ${event}\n`);
  res.write(`data: ${message}\n\n`);
}

// 文件添加示例
function addFile(res, filePath, content) {
  const messageId = `msg_${Date.now()}`;
  const operationId = `op_${Date.now()}`;
  
  // 发送 start 事件
  sendSSEEvent(res, 'add-start', {
    messageId,
    operationId,
    data: {
      type: 'add',
      filePath,
      content: ''
    }
  });
  
  // 分块发送内容（模拟流式传输）
  const chunks = content.match(/.{1,100}/g) || [];
  chunks.forEach((chunk, index) => {
    const currentContent = chunks.slice(0, index + 1).join('');
    sendSSEEvent(res, 'add-progress', {
      messageId,
      operationId,
      data: {
        type: 'add',
        filePath,
        content: currentContent
      }
    });
  });
  
  // 发送 end 事件
  sendSSEEvent(res, 'add-end', {
    messageId,
    operationId,
    data: {
      type: 'add',
      filePath,
      content
    }
  });
}
```

---

## 📝 版本历史

- **v1.0** (2025-11-14)
  - 初始版本
  - 定义基础事件类型和数据格式
  - 支持文件操作和命令执行

---

**文档维护者**：ruoyi-ai开发团队  
**最后更新**：2025-11-14  
**协议版本**：1.0

