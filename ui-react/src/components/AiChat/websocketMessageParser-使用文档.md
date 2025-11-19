# WebSocket 消息解析器使用文档

## 📋 概述

`websocketMessageParser.tsx` 是一个基于 WebSocket 协议的消息解析器，用于解析和处理结构化的操作事件。它支持文件操作（添加/编辑/删除）和命令执行，并提供了完善的安全检查和错误处理机制。

## 🎯 核心特性

1. **多种操作类型**：支持 `add`、`edit`、`delete`、`cmd` 操作
2. **流式处理**：支持 `start`、`progress`、`end` 三个阶段
3. **安全验证**：路径验证、命令验证、大小限制等
4. **错误处理**：完善的错误捕获和回调机制
5. **状态管理**：支持多消息并发解析
6. **超时控制**：自动清理超时操作

## 📦 安装和使用

### 基本使用

```typescript
import { parseWebSocketMessage } from './useWebSocketMessageParser';

// 解析 WebSocket 消息
const messageId = 'msg_123';
const message = {
  event: 'add-start',
  data: {
    type: 'add',
    filePath: 'src/index.js',
    content: 'console.log("Hello");'
  }
};

parseWebSocketMessage(messageId, message);
```

### WebSocket 连接示例（推荐使用连接管理器）

```typescript
import { createWebSocketConnection } from './useWebSocketMessageParser';

// 使用连接管理器（推荐）- 自动处理重连和错误恢复
const connection = createWebSocketConnection('ws://localhost:8080/ws');

// 发送消息
connection.send(JSON.stringify({
  event: 'add-start',
  data: { type: 'add', filePath: 'src/index.js', content: '...' }
}));
```

### 直接使用 WebSocket（不推荐，无自动重连）

```typescript
import { parseWebSocketMessage } from './useWebSocketMessageParser';

// 直接使用 WebSocket（不推荐，需要手动处理重连）
const ws = new WebSocket('ws://localhost:8080/ws');

ws.onmessage = (event) => {
  try {
    const message = JSON.parse(event.data);
    const messageId = message.messageId || `msg_${Date.now()}`;
    
    // 解析消息
    parseWebSocketMessage(messageId, message);
  } catch (error) {
    console.error('解析 WebSocket 消息失败:', error);
  }
};

ws.onerror = (error) => {
  console.error('WebSocket 错误:', error);
  // 需要手动实现重连逻辑
};

ws.onclose = () => {
  console.log('WebSocket 连接已关闭');
  // 需要手动实现重连逻辑
};
```

## 📝 消息格式

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
const parser = new WebSocketMessageParser({
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
const parser = new WebSocketMessageParser({
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
const parser = new WebSocketMessageParser({
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
const parser = new WebSocketMessageParser({
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
const parser = new WebSocketMessageParser({
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
import { getWebSocketMessageState } from './useWebSocketMessageParser';

const state = getWebSocketMessageState('msg_123');
if (state) {
  console.log('操作数量:', state.operationCount);
  console.log('操作列表:', Array.from(state.operations.keys()));
}
```

### 清理消息状态

```typescript
import { clearWebSocketMessage, resetWebSocketParser } from './useWebSocketMessageParser';

// 清理指定消息
clearWebSocketMessage('msg_123');

// 重置所有状态
resetWebSocketParser();
```

## 🎨 自定义回调

### 完整回调示例

```typescript
const parser = new WebSocketMessageParser({
  callbacks: {
    // 文件添加
    onAddStart: async (data) => {
      const fileData = data.data as FileOperationData;
      console.log('开始添加文件:', fileData.filePath);
      // 显示进度条
    },
    
    onAddProgress: async (data) => {
      const fileData = data.data as FileOperationData;
      const progress = (fileData.content?.length || 0) / 1000;
      console.log('添加进度:', progress);
      // 更新进度条
    },
    
    onAddEnd: async (data) => {
      const fileData = data.data as FileOperationData;
      console.log('完成添加:', fileData.filePath);
      // 创建文件
      await createFileWithContent(fileData.filePath, fileData.content || '');
    },
    
    // 文件编辑
    onEditStart: async (data) => {
      console.log('开始编辑:', data.data.filePath);
    },
    
    onEditProgress: async (data) => {
      // 实时预览编辑内容
      console.log('编辑内容:', data.data.content);
    },
    
    onEditEnd: async (data) => {
      // 保存文件
      await createFileWithContent(data.data.filePath, data.data.content || '');
    },
    
    // 文件删除
    onDeleteStart: async (data) => {
      console.log('开始删除:', data.data.filePath);
    },
    
    onDeleteEnd: async (data) => {
      // 执行删除
      await deleteFile(data.data.filePath);
    },
    
    // 命令执行
    onCmd: async (data) => {
      const cmdData = data.data as CommandOperationData;
      console.log('执行命令:', cmdData.command);
      // 执行命令
      await executeCommand(cmdData.command, cmdData.workingDir);
    },
    
    // 错误处理
    onError: (error, message) => {
      console.error('解析错误:', error);
      // 显示错误通知
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

## 📊 与现有解析器的对比

| 特性 | StreamingMessageParser | WebSocketMessageParser |
|------|----------------------|----------------------|
| 协议 | 文本流（SSE） | WebSocket |
| 消息格式 | XML 标签 | JSON |
| 事件类型 | file/shell/start | add/edit/delete/cmd |
| 流式处理 | ✅ | ✅ |
| 安全验证 | ❌ | ✅ |
| 超时控制 | ❌ | ✅ |
| 状态管理 | ✅ | ✅ |
| 错误处理 | 基础 | 完善 |

## 🔗 相关文件

- `messae.tsx` - 原有的流式消息解析器
- `useMessageParser.tsx` - 原有解析器的使用示例
- `useWebSocketMessageParser.tsx` - WebSocket 解析器的使用示例

## 🔄 错误恢复和自动重连

### 使用连接管理器

连接管理器提供了完善的自动重连机制，使用指数退避策略：

```typescript
import { createWebSocketConnection } from './useWebSocketMessageParser';

// 创建带自动重连的 WebSocket 连接
const connection = createWebSocketConnection('ws://localhost:8080/ws', (event) => {
  console.log('收到消息:', event.data);
});

// 手动关闭连接
// connection.close();
```

### 重连策略

连接管理器使用**指数退避策略**，比固定间隔重连更智能：

- **初始重连间隔**：1秒
- **最大重连间隔**：30秒
- **重连间隔增长因子**：1.5倍
- **重连时间线**：
  - 第1次：1秒后重连
  - 第2次：1.5秒后重连
  - 第3次：2.25秒后重连
  - 第4次：3.375秒后重连
  - ...
  - 最大间隔：30秒

### 消息队列

连接断开时，消息会自动加入队列，重连成功后自动发送：

```typescript
const connection = createWebSocketConnection('ws://localhost:8080/ws');

// 即使连接断开，消息也会被缓存
connection.send(JSON.stringify({ event: 'add-start', data: {...} }));

// 重连成功后，队列中的消息会自动发送
```

### 使用 React Hook

```typescript
import { useWebSocketConnection } from './useWebSocketMessageParser';

function MyComponent() {
  const { status, connect, disconnect, send, isConnected } = useWebSocketConnection(
    'ws://localhost:8080/ws',
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

### 心跳机制

连接管理器自动发送心跳消息，保持连接活跃：

- **心跳间隔**：30秒
- **心跳消息**：`ping`
- **自动检测**：服务器应响应 `pong` 或 `ping`

### 高级配置

```typescript
import { WebSocketConnectionManager } from './websocketConnectionManager';

const connection = new WebSocketConnectionManager(
  {
    url: 'ws://localhost:8080/ws',
    reconnectInterval: 1000,        // 初始重连间隔
    maxReconnectInterval: 30000,    // 最大重连间隔
    reconnectDecay: 1.5,           // 重连间隔增长因子
    maxReconnectAttempts: 10,       // 最大重连次数（或 Infinity）
    timeout: 10000,                 // 连接超时
    enableMessageQueue: true,       // 启用消息队列
    maxQueueSize: 100,              // 最大队列大小
    heartbeatInterval: 30000,       // 心跳间隔
    heartbeatMessage: 'ping',      // 心跳消息
  },
  {
    onOpen: (event) => console.log('连接成功'),
    onClose: (event) => console.log('连接关闭'),
    onError: (error) => console.error('连接错误', error),
    onMessage: (event) => console.log('收到消息', event.data),
    onReconnect: (attempt) => console.log(`第 ${attempt} 次重连`),
    onReconnectFailed: () => console.error('重连失败'),
    onStatusChange: (status) => console.log('状态变更', status),
  }
);

connection.connect();
```

## 📝 注意事项

1. **消息ID**：每条消息应该有唯一的 `messageId`，用于状态管理
2. **操作ID**：每个操作应该有唯一的 `operationId`，用于跟踪操作状态
3. **内容大小**：注意控制文件内容大小，避免内存溢出
4. **并发处理**：解析器支持多消息并发，但要注意资源限制
5. **错误恢复**：使用连接管理器自动处理网络中断和重连
6. **消息队列**：连接断开时，消息会自动缓存，重连后自动发送
7. **资源清理**：组件卸载时记得调用 `connection.destroy()` 清理资源

## 🚀 最佳实践

1. **使用白名单**：在生产环境中，建议使用路径和命令白名单
2. **监控日志**：记录所有操作，便于调试和审计
3. **错误通知**：实现用户友好的错误通知机制
4. **资源清理**：及时清理完成的消息状态，避免内存泄漏
5. **测试验证**：在集成前充分测试各种场景
6. **使用连接管理器**：始终使用连接管理器而不是直接使用 WebSocket，以获得自动重连和错误恢复能力
7. **监控连接状态**：监听状态变化，向用户显示连接状态
8. **合理配置重连**：根据实际需求调整重连参数，避免过于频繁的重连

---

**文档版本：** 1.1  
**最后更新：** 2025-01-11
