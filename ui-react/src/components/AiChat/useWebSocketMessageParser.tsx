/**
 * WebSocket 消息解析器使用示例
 * 保持与 useMessageParser.tsx 类似的使用方式
 * 包含自动重连和错误恢复机制
 */

import React from 'react';
import { WebSocketMessageParser, OperationCallbackData, FileOperationData, CommandOperationData } from './websocketMessageParser';
import { WebSocketConnectionManager, ConnectionStatus } from './websocketConnectionManager';
import { createFileWithContent } from '../WeIde/components/IDEContent/FileExplorer/utils/fileSystem';
import useTerminalStore from '@/stores/terminalSlice';

// 命令队列（复用现有实现）
class Queue {
  private queue: string[] = [];
  private processing: boolean = false;

  push(command: string) {
    this.queue.push(command);
    this.process();
  }

  private getNext(): string | undefined {
    return this.queue.shift();
  }

  private async process() {
    if (this.processing || this.queue.length === 0) {
      return;
    }

    this.processing = true;
    try {
      while (this.queue.length > 0) {
        const command = this.getNext();
        if (command) {
          console.log('执行命令', command);
          await useTerminalStore.getState().getTerminal(0).executeCommand(command);
        }
      }
    } finally {
      this.processing = false;
    }
  }
}

export const queue = new Queue();

// 创建解析器实例
const websocketMessageParser = new WebSocketMessageParser({
  // 安全配置
  maxMessageSize: 10 * 1024 * 1024, // 10MB
  maxOperationsPerMessage: 100,
  enableValidation: true,
  timeout: 30000, // 30秒

  // 回调函数
  callbacks: {
    // 文件添加操作
    onAddStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 开始添加文件:', fileData.filePath);
    },

    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 文件添加进度:', fileData.filePath, fileData.content?.length);
      // 可以在这里实现进度更新逻辑
    },

    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 完成添加文件:', fileData.filePath);
      
      if (fileData.content !== undefined) {
        try {
          await createFileWithContent(
            fileData.filePath,
            fileData.content,
            true // 自动创建目录
          );
        } catch (error) {
          console.error('[WebSocket] 创建文件失败:', error);
        }
      }
    },

    // 文件编辑操作
    onEditStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 开始编辑文件:', fileData.filePath);
    },

    onEditProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 文件编辑进度:', fileData.filePath);
      // 可以在这里实现实时编辑预览
    },

    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 完成编辑文件:', fileData.filePath);
      
      if (fileData.content !== undefined) {
        try {
          await createFileWithContent(
            fileData.filePath,
            fileData.content,
            false // 编辑时不需要创建目录
          );
        } catch (error) {
          console.error('[WebSocket] 编辑文件失败:', error);
        }
      }
    },

    // 文件删除操作
    onDeleteStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 开始删除文件:', fileData.filePath);
    },

    onDeleteProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 文件删除进度:', fileData.filePath);
    },

    onDeleteEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 完成删除文件:', fileData.filePath);
      
      // 这里需要实现文件删除逻辑
      // 可以使用文件系统的删除方法
      try {
        // await deleteFile(fileData.filePath);
        console.log('[WebSocket] 删除文件:', fileData.filePath);
      } catch (error) {
        console.error('[WebSocket] 删除文件失败:', error);
      }
    },

    // 命令执行
    onCmd: async (data: OperationCallbackData) => {
      const cmdData = data.data as CommandOperationData;
      console.log('[WebSocket] 执行命令:', cmdData.command);
      
      if (cmdData.command) {
        queue.push(cmdData.command);
      }
    },

    // 错误处理
    onError: (error: Error, message?: any) => {
      console.error('[WebSocket] 解析器错误:', error);
      console.error('[WebSocket] 错误消息:', message);
      // 可以在这里实现错误通知逻辑
    },
  },
});

/**
 * 解析 WebSocket 消息
 * 与 parseMessages 类似的使用方式
 */
export const parseWebSocketMessage = (messageId: string, message: string | object): void => {
  websocketMessageParser.parse(messageId, message);
};

/**
 * 重置解析器状态
 */
export const resetWebSocketParser = (): void => {
  websocketMessageParser.reset();
};

/**
 * 清理指定消息的状态
 */
export const clearWebSocketMessage = (messageId: string): void => {
  websocketMessageParser.clearMessage(messageId);
};

/**
 * 获取消息状态
 */
export const getWebSocketMessageState = (messageId: string) => {
  return websocketMessageParser.getMessageState(messageId);
};

// 导出解析器实例（如果需要直接访问）
export { websocketMessageParser };

// ==================== WebSocket 连接管理器 ====================

/**
 * 创建并配置 WebSocket 连接管理器
 */
export const createWebSocketConnection = (
  url: string,
  onMessage?: (event: MessageEvent) => void
): WebSocketConnectionManager => {
  const connectionManager = new WebSocketConnectionManager(
    {
      url,
      // 重连配置：使用指数退避策略
      reconnectInterval: 1000,        // 初始重连间隔 1秒
      maxReconnectInterval: 30000,    // 最大重连间隔 30秒
      reconnectDecay: 1.5,            // 每次重连间隔增长 1.5 倍
      maxReconnectAttempts: Infinity, // 无限重连（可根据需要调整）
      timeout: 10000,                 // 连接超时 10秒
      enableMessageQueue: true,       // 启用消息队列
      maxQueueSize: 100,              // 最大队列大小 100
      heartbeatInterval: 30000,       // 心跳间隔 30秒
      heartbeatMessage: 'ping',       // 心跳消息
    },
    {
      onOpen: (event) => {
        console.log('[WebSocket] 连接已建立', event);
      },
      onClose: (event) => {
        console.log('[WebSocket] 连接已关闭', {
          code: event.code,
          reason: event.reason,
          wasClean: event.wasClean,
        });
      },
      onError: (error) => {
        console.error('[WebSocket] 连接错误', error);
      },
      onMessage: (event) => {
        try {
          // 解析消息并传递给解析器
          const message = JSON.parse(event.data);
          const messageId = message.messageId || `msg_${Date.now()}`;
          
          // 解析消息
          parseWebSocketMessage(messageId, message);
          
          // 调用自定义消息处理器
          onMessage?.(event);
        } catch (error) {
          console.error('[WebSocket] 解析消息失败:', error);
        }
      },
      onReconnect: (attempt) => {
        console.log(`[WebSocket] 正在尝试第 ${attempt} 次重连...`);
      },
      onReconnectFailed: () => {
        console.error('[WebSocket] 重连失败，已达到最大重连次数');
      },
      onStatusChange: (status: ConnectionStatus) => {
        console.log(`[WebSocket] 连接状态变更: ${status}`);
      },
    }
  );

  // 自动连接
  connectionManager.connect();

  return connectionManager;
};

/**
 * 使用 WebSocket 连接管理器的示例 Hook
 */
export const useWebSocketConnection = (
  url: string,
  options?: {
    autoConnect?: boolean;
    onMessage?: (event: MessageEvent) => void;
    onStatusChange?: (status: ConnectionStatus) => void;
  }
) => {
  const [connectionManager, setConnectionManager] = React.useState<WebSocketConnectionManager | null>(null);
  const [status, setStatus] = React.useState<ConnectionStatus>('disconnected');

  React.useEffect(() => {
    const manager = new WebSocketConnectionManager(
      {
        url,
        reconnectInterval: 1000,
        maxReconnectInterval: 30000,
        reconnectDecay: 1.5,
        maxReconnectAttempts: Infinity,
        timeout: 10000,
        enableMessageQueue: true,
        maxQueueSize: 100,
        heartbeatInterval: 30000,
        heartbeatMessage: 'ping',
      },
      {
        onMessage: (event) => {
          try {
            const message = JSON.parse(event.data);
            const messageId = message.messageId || `msg_${Date.now()}`;
            parseWebSocketMessage(messageId, message);
            options?.onMessage?.(event);
          } catch (error) {
            console.error('[WebSocket] 解析消息失败:', error);
          }
        },
        onStatusChange: (newStatus) => {
          setStatus(newStatus);
          options?.onStatusChange?.(newStatus);
        },
      }
    );

    setConnectionManager(manager);

    if (options?.autoConnect !== false) {
      manager.connect();
    }

    return () => {
      manager.destroy();
    };
  }, [url]);

  return {
    connectionManager,
    status,
    connect: () => connectionManager?.connect(),
    disconnect: () => connectionManager?.close(),
    send: (data: string | ArrayBuffer | Blob) => connectionManager?.send(data),
    isConnected: () => connectionManager?.isConnected() ?? false,
  };
};

// 注意：如果项目中没有 React，需要移除 useWebSocketConnection Hook
// 或者添加 React 导入

