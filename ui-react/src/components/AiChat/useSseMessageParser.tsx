/**
 * SSE 消息解析器使用示例
 * 保持与 useWebSocketMessageParser.tsx 和 useMessageParser.tsx 类似的使用方式
 * 包含自动重连和错误恢复机制
 */

import React from 'react';
import { SSEMessageParser, OperationCallbackData, FileOperationData, CommandOperationData, SSEEventType } from './sseMessageParser';
import { SSEConnectionManager, SSEConnectionStatus } from './sseConnectionManager';
import { createFileWithContent } from '../WeIde/components/IDEContent/FileExplorer/utils/fileSystem';
import {useFileStore} from '../WeIde/stores/fileStore';
import useTerminalStore from '@/stores/terminalSlice';
import { eventEmitter } from './utils/EventEmitter';

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
const sseMessageParser = new SSEMessageParser({
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
      console.log('[SSE] 开始添加文件:', fileData.filePath);

      // 创建空文件
      await createFileWithContent(fileData.filePath, '', true);
    },

    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 文件添加进度:', fileData.filePath, fileData.content?.length);

      // 获取当前文件内容并追加新内容
      const currentContent = useFileStore.getState().files[fileData.filePath] || '';
      const newContent = currentContent + (fileData.content || '');

      // 更新文件内容
      await useFileStore.getState().updateContent(fileData.filePath, newContent, false, true);
    },

    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 完成添加文件:', fileData.filePath);
      
      if (fileData.content !== undefined) {
        try {
          await createFileWithContent(
            fileData.filePath,
            fileData.content,
            true // 自动创建目录
          );
        } catch (error) {
          console.error('[SSE] 创建文件失败:', error);
        }
      }
    },

    // 文件编辑操作
    onEditStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 开始编辑文件:', fileData.filePath);

      // 删除现有文件并创建空文件
      await useFileStore.getState().deleteFile(fileData.filePath);
      await createFileWithContent(fileData.filePath, '', false);
    },

    onEditProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 文件编辑进度:', fileData.filePath);

      // 获取当前文件内容并追加新内容
      const currentContent = useFileStore.getState().files[fileData.filePath] || '';
      const newContent = currentContent + (fileData.content || '');

      // 更新文件内容
      await useFileStore.getState().updateContent(fileData.filePath, newContent, false, true);
    },

    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 完成编辑文件:', fileData.filePath);
      
      if (fileData.content !== undefined) {
        try {
          await createFileWithContent(
            fileData.filePath,
            fileData.content,
            false // 编辑时不需要创建目录
          );
        } catch (error) {
          console.error('[SSE] 编辑文件失败:', error);
        }
      }
    },

    // 文件删除操作
    onDeleteStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 开始删除文件:', fileData.filePath);
    },

    onDeleteProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 文件删除进度:', fileData.filePath);
    },

    onDeleteEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 完成删除文件:', fileData.filePath);
      
      // 这里需要实现文件删除逻辑
      // 可以使用文件系统的删除方法
      try {
        // await deleteFile(fileData.filePath);
        console.log('[SSE] 删除文件:', fileData.filePath);
      } catch (error) {
        console.error('[SSE] 删除文件失败:', error);
      }
    },

    // 列表操作
    onListProgress: async (data: OperationCallbackData) => {
      const listData = data.data as FileOperationData;
      console.log('[SSE] 列表进度:', listData.filePath, listData.content?.length);

      // 发送事件更新列表进度状态
      eventEmitter.emit('list-progress-update', {
        operationId: data.operationId,
        filePath: listData.filePath,
        content: listData.content,
        isLoading: true
      });
    },

    // 命令执行
    onCmd: async (data: OperationCallbackData) => {
      const cmdData = data.data as CommandOperationData;
      console.log('[SSE] 执行命令:', cmdData.command);

      if (cmdData.command) {
        queue.push(cmdData.command);
      }
    },

    // 错误处理
    onError: (error: Error, message?: any) => {
      console.error('[SSE] 解析器错误:', error);
      console.error('[SSE] 错误消息:', message);
      // 可以在这里实现错误通知逻辑
    },
  },
});

/**
 * 解析 SSE 消息
 * 与 parseWebSocketMessage 和 parseMessages 类似的使用方式
 * 
 * SSE 消息格式：
 * - 后端通过 event 字段发送事件名（如：add-start, add-progress, add-end）
 * - data 字段包含 JSON 格式的操作数据
 * 
 * 示例：
 * event: add-start
 * data: {"type":"add","filePath":"src/index.js","content":"..."}
 */
export const parseSSEMessage = (messageId: string, message: string | object): void => {
  sseMessageParser.parse(messageId, message);
};

/**
 * 重置解析器状态
 */
export const resetSSEParser = (): void => {
  sseMessageParser.reset();
};

/**
 * 清理指定消息的状态
 */
export const clearSSEMessage = (messageId: string): void => {
  sseMessageParser.clearMessage(messageId);
};

/**
 * 获取消息状态
 */
export const getSSEMessageState = (messageId: string) => {
  return sseMessageParser.getMessageState(messageId);
};

// 导出解析器实例（如果需要直接访问）
export { sseMessageParser };

// ==================== SSE 连接管理器 ====================

/**
 * 创建并配置 SSE 连接管理器
 * 
 * SSE 与 WebSocket 的主要区别：
 * 1. SSE 是单向通信（服务器到客户端），基于 HTTP
 * 2. SSE 使用 EventSource API，自动重连
 * 3. SSE 通过 event 字段区分不同的事件类型
 * 4. SSE 只支持 GET 请求，不支持自定义请求头（原生 EventSource）
 */
export const createSSEConnection = (
  url: string,
  onMessage?: (event: MessageEvent) => void
): SSEConnectionManager => {
  const connectionManager = new SSEConnectionManager(
    {
      url,
      withCredentials: false, // 是否发送凭证
    },
    {
      onOpen: (event) => {
        console.log('[SSE] 连接已建立', event);
      },
      onClose: () => {
        console.log('[SSE] 连接已关闭');
      },
      onError: (error) => {
        console.error('[SSE] 连接错误', error);
      },
      onMessage: (event) => {
        try {
          // SSE 消息格式：
          // - event.data 包含 JSON 字符串
          // - event.type 包含事件类型（如果有 event 字段）
          // - 如果没有 event 字段，event.type 为 'message'
          
          let message: any;
          let eventName: string;

          // 解析消息数据
          if (typeof event.data === 'string') {
            try {
              message = JSON.parse(event.data);
            } catch (e) {
              console.error('[SSE] JSON 解析失败:', e);
              return;
            }
          } else {
            message = event.data;
          }

          // 获取事件名
          // 如果后端通过 event 字段发送，event.type 会包含事件名
          // 否则从 message.event 获取
          eventName = event.type !== 'message' ? event.type : (message.event || 'message');

          // 生成消息ID
          const messageId = message.messageId || `msg_${Date.now()}`;

          // 构建完整的消息对象（包含 event 字段）
          const fullMessage = {
            ...message,
            event: eventName,
          };

          // 解析消息
          parseSSEMessage(messageId, fullMessage);
          
          // 调用自定义消息处理器
          onMessage?.(event);
        } catch (error) {
          console.error('[SSE] 解析消息失败:', error);
        }
      },
      onStatusChange: (status: SSEConnectionStatus) => {
        console.log(`[SSE] 连接状态变更: ${status}`);
      },
    }
  );

  // 设置重连配置
  connectionManager.setMaxReconnectAttempts(Infinity); // 无限重连
  connectionManager.setReconnectInterval(1000); // 1秒重连间隔

  // 自动连接
  connectionManager.connect();

  return connectionManager;
};

/**
 * 使用 SSE 连接管理器的示例 Hook
 */
export const useSSEConnection = (
  url: string,
  options?: {
    autoConnect?: boolean;
    onMessage?: (event: MessageEvent) => void;
    onStatusChange?: (status: SSEConnectionStatus) => void;
  }
) => {
  const [connectionManager, setConnectionManager] = React.useState<SSEConnectionManager | null>(null);
  const [status, setStatus] = React.useState<SSEConnectionStatus>('disconnected');

  React.useEffect(() => {
    const manager = new SSEConnectionManager(
      {
        url,
        withCredentials: false,
      },
      {
        onMessage: (event) => {
          try {
            let message: any;
            let eventName: string;

            if (typeof event.data === 'string') {
              try {
                message = JSON.parse(event.data);
              } catch (e) {
                console.error('[SSE] JSON 解析失败:', e);
                return;
              }
            } else {
              message = event.data;
            }

            eventName = event.type !== 'message' ? event.type : (message.event || 'message');
            const messageId = message.messageId || `msg_${Date.now()}`;

            const fullMessage = {
              ...message,
              event: eventName,
            };

            parseSSEMessage(messageId, fullMessage);
            options?.onMessage?.(event);
          } catch (error) {
            console.error('[SSE] 解析消息失败:', error);
          }
        },
        onStatusChange: (newStatus) => {
          setStatus(newStatus);
          options?.onStatusChange?.(newStatus);
        },
      }
    );

    manager.setMaxReconnectAttempts(Infinity);
    manager.setReconnectInterval(1000);

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
    isConnected: () => connectionManager?.isConnected() ?? false,
  };
};

