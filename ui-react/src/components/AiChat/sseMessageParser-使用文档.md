# SSE 消息解析器使用文档

## 📋 概述

`sseMessageParser.tsx` 是一个基于 Server-Sent Events (SSE) 协议的消息解析器，用于解析和处理结构化的操作事件。它支持文件操作（添加/编辑/删除）和命令执行，并提供了完善的安全检查和错误处理机制。

## 🎯 核心特性

1. **多种操作类型**：支持 `add`、`edit`、`delete`、`cmd` 操作
2. **流式处理**：支持 `start`、`progress`、`end` 三个阶段
3. **安全验证**：路径验证、命令验证、大小限制等
4. **错误处理**：完善的错误捕获和回调机制
5. **状态管理**：支持多消息并发解析
6. **超时控制**：自动清理超时操作
7. **自动重连**：基于 EventSource 的自动重连机制

## 📦 安装和使用

### 基本使用

```typescript
import { parseSSEMessage } from './useSseMessageParser';

// 解析 SSE 消息
const messageId = 'msg_123';
const message = {
  event: 'add-start',
  data: {
    type: 'add',
    filePath: 'src/index.js',
    content: 'console.log("Hello");'
  }
};

parseSSEMessage(messageId, message);
```

### SSE 连接示例（推荐使用连接管理器）

```typescript
import { createSSEConnection } from './useSseMessageParser';

// 使用连接管理器（推荐）- 自动处理重连和错误恢复
const connection = createSSEConnection('http://localhost:8080/sse');

// SSE 是单向通信，不需要 send 方法
// 消息通过 EventSource 自动接收
```

### 直接使用 EventSource（不推荐，无统一管理）

```typescript
import { parseSSEMessage } from './useSseMessageParser';

// 直接使用 EventSource（不推荐）
const eventSource = new EventSource('http://localhost:8080/sse');

eventSource.onmessage = (event) => {
  try {
    const message = JSON.parse(event.data);
    const messageId = message.messageId || `msg_${Date.now()}`;
    
    // 从 event.type 获取事件名（如果后端通过 event 字段发送）
    // 或者从 message.event 获取
    const eventName = event.type !== 'message' ? event.type : message.event;
    
    // 解析消息
    parseSSEMessage(messageId, { ...message, event: eventName });
  } catch (error) {
    console.error('解析 SSE 消息失败:', error);
  }
};

eventSource.onerror = (error) => {
  console.error('SSE 错误:', error);
  // EventSource 会自动重连
};

eventSource.close(); // 关闭连接
```

## 📝 消息格式

### SSE 协议格式

SSE 使用文本流格式，后端发送格式如下：

```
event: add-start
data: {"type":"add","filePath":"src/index.js","content":"console.log('Hello');"}

event: add-progress
data: {"type":"add","filePath":"src/index.js","content":"console.log('Hello');\nconsole.log('World');"}

event: add-end
data: {"type":"add","filePath":"src/index.js","content":"console.log('Hello World');\nconsole.log('Done');"}

```

### 文件添加操作

```json
{
  "event": "add-start",
  "messageId": "msg_123",
  "operationId": "op_456",
  "data": {
    "type": "add",
    "filePath": "src/index.js",
    "content": "console.log('Hello World');"
  }
}
```

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

### 文件编辑操作

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

```json
{
  "event": "edit-progress",
  "messageId": "msg_123",
  "operationId": "op_789",
  "data": {
    "type": "edit",
    "filePath": "src/index.js",
    "content": "// 新的内容..."
  }
}
```

```json
{
  "event": "edit-end",
  "messageId": "msg_123",
  "operationId": "op_789",
  "data": {
    "type": "edit",
    "filePath": "src/index.js",
    "content": "console.log('Updated');"
  }
}
```

### 文件删除操作

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

### 命令执行

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
    }
  }
}
```

## 🔒 安全配置

### 路径白名单

```typescript
const parser = new SSEMessageParser({
  allowedFilePaths: [
    'src/',
    'public/',
    'package.json'
  ],
  callbacks: { /* ... */ }
});
```

### 路径黑名单

```typescript
const parser = new SSEMessageParser({
  blockedFilePaths: [
    'node_modules/',
    '.git/',
    'package-lock.json'
  ],
  callbacks: { /* ... */ }
});
```

### 命令白名单

```typescript
const parser = new SSEMessageParser({
  allowedCommands: [
    'npm install',
    'npm run build',
    'git status'
  ],
  callbacks: { /* ... */ }
});
```

### 命令黑名单

```typescript
const parser = new SSEMessageParser({
  blockedCommands: [
    'rm -rf',
    'format',
    'del /s'
  ],
  callbacks: { /* ... */ }
});
```

## ⚙️ 高级配置

### 自定义配置

```typescript
const parser = new SSEMessageParser({
  // 消息大小限制（默认 10MB）
  maxMessageSize: 20 * 1024 * 1024,
  
  // 每条消息最大操作数（默认 100）
  maxOperationsPerMessage: 200,
  
  // 是否启用验证（默认 true）
  enableValidation: true,
  
  // 操作超时时间（默认 30秒）
  timeout: 60000,
  
  // 回调函数
  callbacks: {
    onAddStart: async (data) => {
      console.log('开始添加:', data.data.filePath);
    },
    onAddEnd: async (data) => {
      // 处理文件添加完成
    },
    onError: (error, message) => {
      console.error('错误:', error);
    }
  }
});
```

## 🔄 状态管理

### 获取消息状态

```typescript
import { getSSEMessageState } from './useSseMessageParser';

const state = getSSEMessageState('msg_123');
if (state) {
  console.log('操作数量:', state.operationCount);
  console.log('操作列表:', Array.from(state.operations.keys()));
}
```

### 清理消息状态

```typescript
import { clearSSEMessage, resetSSEParser } from './useSseMessageParser';

// 清理指定消息
clearSSEMessage('msg_123');

// 重置所有状态
resetSSEParser();
```

## 🎨 自定义回调

### 完整回调示例

```typescript
const parser = new SSEMessageParser({
  callbacks: {
    // 文件添加
    onAddStart: async (data) => {
      const fileData = data.data as FileOperationData;
      console.log('开始添加文件:', fileData.filePath);
    },
    
    onAddProgress: async (data) => {
      const fileData = data.data as FileOperationData;
      const progress = (fileData.content?.length || 0) / 1000;
      console.log('添加进度:', progress);
    },
    
    onAddEnd: async (data) => {
      const fileData = data.data as FileOperationData;
      console.log('完成添加:', fileData.filePath);
      await createFileWithContent(fileData.filePath, fileData.content || '');
    },
    
    // 文件编辑
    onEditStart: async (data) => {
      console.log('开始编辑:', data.data.filePath);
    },
    
    onEditProgress: async (data) => {
      console.log('编辑内容:', data.data.content);
    },
    
    onEditEnd: async (data) => {
      await createFileWithContent(data.data.filePath, data.data.content || '');
    },
    
    // 文件删除
    onDeleteStart: async (data) => {
      console.log('开始删除:', data.data.filePath);
    },
    
    onDeleteEnd: async (data) => {
      await deleteFile(data.data.filePath);
    },
    
    // 命令执行
    onCmd: async (data) => {
      const cmdData = data.data as CommandOperationData;
      console.log('执行命令:', cmdData.command);
      await executeCommand(cmdData.command, cmdData.workingDir);
    },
    
    // 错误处理
    onError: (error, message) => {
      console.error('解析错误:', error);
      showErrorNotification(error.message);
    }
  }
});
```

## 🐛 错误处理

解析器会自动捕获和处理以下错误：

1. **JSON 解析错误**：消息格式不正确
2. **验证错误**：路径或命令验证失败
3. **大小限制错误**：消息或内容超过限制
4. **超时错误**：操作超过指定时间未完成
5. **回调执行错误**：回调函数执行失败

所有错误都会通过 `onError` 回调通知，并记录到控制台。

## 📊 流式解析器对比

### 三种解析器对比

| 特性 | StreamingMessageParser | WebSocketMessageParser | SSEMessageParser |
|------|----------------------|----------------------|------------------|
| **协议** | 文本流（SSE/HTTP） | WebSocket | SSE (HTTP) |
| **消息格式** | XML 标签 | JSON | JSON |
| **事件类型** | file/shell/start | add/edit/delete/cmd | add/edit/delete/cmd |
| **通信方向** | 单向（服务器→客户端） | 双向（全双工） | 单向（服务器→客户端） |
| **流式处理** | ✅ | ✅ | ✅ |
| **安全验证** | ❌ | ✅ | ✅ |
| **超时控制** | ❌ | ✅ | ✅ |
| **状态管理** | ✅ | ✅ | ✅ |
| **错误处理** | 基础 | 完善 | 完善 |
| **自动重连** | ❌ | ✅（手动实现） | ✅（浏览器内置） |
| **自定义请求头** | ✅ | ✅ | ❌（原生 EventSource） |
| **双向通信** | ❌ | ✅ | ❌ |

### StreamingMessageParser（现有流式解析器）

- **协议**：基于文本流，通常通过 HTTP/SSE 传输
- **消息格式**：XML 标签格式（`<boltArtifact>`, `<boltAction>`）
- **解析方式**：正则表达式匹配 XML 标签
- **事件类型**：`file`, `shell`, `start`
- **特点**：
  - 简单直接，适合简单的文件操作
  - 无安全验证
  - 无超时控制
  - 需要手动实现重连

### WebSocketMessageParser（WebSocket 解析器）

- **协议**：WebSocket（`ws://` 或 `wss://`）
- **消息格式**：JSON 格式
- **解析方式**：直接解析 JSON
- **事件类型**：`add-start`, `add-progress`, `add-end`, `edit-start`, `edit-progress`, `edit-end`, `delete-start`, `delete-progress`, `delete-end`, `cmd`
- **特点**：
  - 双向通信，可以发送和接收消息
  - 完善的安全验证和错误处理
  - 支持自定义请求头
  - 需要手动实现重连逻辑
  - 适合需要双向交互的场景

### SSEMessageParser（SSE 解析器）

- **协议**：Server-Sent Events（`http://` 或 `https://`）
- **消息格式**：JSON 格式，通过 `event` 字段区分事件类型
- **解析方式**：解析 SSE 事件数据
- **事件类型**：`add-start`, `add-progress`, `add-end`, `edit-start`, `edit-progress`, `edit-end`, `delete-start`, `delete-progress`, `delete-end`, `cmd`
- **特点**：
  - 单向通信（服务器→客户端）
  - 浏览器内置自动重连
  - 完善的安全验证和错误处理
  - 不支持自定义请求头（原生 EventSource）
  - 只支持 GET 请求
  - 适合只需要服务器推送的场景

## 🔄 WebSocket vs SSE 详细对比

### 协议层面

| 特性 | WebSocket | SSE |
|------|-----------|-----|
| **协议** | WebSocket (ws://, wss://) | HTTP/HTTPS |
| **连接方式** | 升级 HTTP 连接 | 标准 HTTP 请求 |
| **通信方向** | 双向（全双工） | 单向（服务器→客户端） |
| **数据格式** | 二进制或文本 | 仅文本 |
| **消息格式** | 自定义（通常是 JSON） | 文本流（event: xxx\ndata: xxx\n\n） |

### 功能特性

| 特性 | WebSocket | SSE |
|------|-----------|-----|
| **自动重连** | 需要手动实现 | 浏览器内置 |
| **自定义请求头** | ✅ | ❌（原生 EventSource） |
| **HTTP 方法** | 升级连接 | 仅 GET |
| **跨域支持** | 需要 CORS | 需要 CORS |
| **认证方式** | 支持多种 | 仅 Cookie/URL 参数 |
| **消息大小** | 无限制 | 受 HTTP 限制 |
| **连接状态** | 需要手动管理 | 浏览器自动管理 |

### 使用场景

**WebSocket 适合：**
- 需要双向通信的场景（如聊天、游戏、实时协作）
- 需要发送大量数据的场景
- 需要自定义请求头的场景
- 需要更灵活的控制

**SSE 适合：**
- 只需要服务器推送的场景（如通知、日志、进度更新）
- 需要简单自动重连的场景
- 不需要发送数据的场景
- 适合简单的实时数据推送

### 代码示例对比

#### WebSocket 连接

```typescript
// WebSocket 需要手动管理连接和重连
const ws = new WebSocket('ws://localhost:8080/ws');

ws.onopen = () => {
  console.log('连接已建立');
  // 可以发送消息
  ws.send(JSON.stringify({ type: 'ping' }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  // 处理消息
};

ws.onerror = (error) => {
  console.error('连接错误:', error);
  // 需要手动实现重连
};

ws.onclose = () => {
  console.log('连接已关闭');
  // 需要手动实现重连
};
```

#### SSE 连接

```typescript
// SSE 自动管理连接和重连
const eventSource = new EventSource('http://localhost:8080/sse');

eventSource.onopen = () => {
  console.log('连接已建立');
  // 无法发送消息（单向通信）
};

eventSource.onmessage = (event) => {
  const message = JSON.parse(event.data);
  // 处理消息
};

eventSource.onerror = (error) => {
  console.error('连接错误:', error);
  // 浏览器自动重连
};

eventSource.close(); // 关闭连接
```

## 🔗 相关文件

- `messae.tsx` - 原有的流式消息解析器（StreamingMessageParser）
- `useMessageParser.tsx` - 原有解析器的使用示例
- `websocketMessageParser.tsx` - WebSocket 解析器
- `useWebSocketMessageParser.tsx` - WebSocket 解析器的使用示例
- `sseMessageParser.tsx` - SSE 解析器
- `useSseMessageParser.tsx` - SSE 解析器的使用示例
- `sseConnectionManager.tsx` - SSE 连接管理器

## 🔄 错误恢复和自动重连

### 使用连接管理器

连接管理器提供了完善的自动重连机制：

```typescript
import { createSSEConnection } from './useSseMessageParser';

// 创建带自动重连的 SSE 连接
const connection = createSSEConnection('http://localhost:8080/sse', (event) => {
  console.log('收到消息:', event.data);
});

// 手动关闭连接
// connection.close();
```

### 重连策略

SSE 使用浏览器内置的自动重连机制：
- **自动重连**：连接断开后自动尝试重连
- **重连间隔**：浏览器自动控制（通常很短）
- **重连次数**：无限重连（直到手动关闭）

### 使用 React Hook

```typescript
import { useSSEConnection } from './useSseMessageParser';

function MyComponent() {
  const { status, connect, disconnect, isConnected } = useSSEConnection(
    'http://localhost:8080/sse',
    {
      autoConnect: true,
      onStatusChange: (status) => {
        console.log('连接状态:', status);
      },
    }
  );

  return (
    <div>
      <p>连接状态: {status}</p>
      <button onClick={() => isConnected() ? disconnect() : connect()}>
        {isConnected() ? '断开' : '连接'}
      </button>
    </div>
  );
}
```

### 连接状态

连接管理器提供以下状态：

- `disconnected` - 已断开
- `connecting` - 正在连接
- `connected` - 已连接
- `reconnecting` - 正在重连
- `failed` - 连接失败（达到最大重连次数）

## 📝 注意事项

1. **消息ID**：每条消息应该有唯一的 `messageId`，用于状态管理
2. **操作ID**：每个操作应该有唯一的 `operationId`，用于跟踪操作状态
3. **内容大小**：注意控制文件内容大小，避免内存溢出
4. **并发处理**：解析器支持多消息并发，但要注意资源限制
5. **错误恢复**：使用连接管理器自动处理网络中断和重连
6. **单向通信**：SSE 只能接收消息，不能发送消息（如果需要发送，使用 WebSocket）
7. **自定义请求头**：原生 EventSource 不支持自定义请求头，如果需要，使用 fetch + ReadableStream 实现
8. **资源清理**：组件卸载时记得调用 `connection.destroy()` 清理资源

## 🚀 最佳实践

1. **使用白名单**：在生产环境中，建议使用路径和命令白名单
2. **监控日志**：记录所有操作，便于调试和审计
3. **错误通知**：实现用户友好的错误通知机制
4. **资源清理**：及时清理完成的消息状态，避免内存泄漏
5. **测试验证**：在集成前充分测试各种场景
6. **使用连接管理器**：始终使用连接管理器而不是直接使用 EventSource，以获得统一的状态管理
7. **监控连接状态**：监听状态变化，向用户显示连接状态
8. **选择合适的协议**：根据实际需求选择 WebSocket（双向）或 SSE（单向）

---

**文档版本：** 1.0  
**最后更新：** 2025-11-13

