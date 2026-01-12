import { SSEMessageParser, OperationCallbackData, FileOperationData, CommandOperationData } from "./sseMessageParser";
import { createFileWithContent } from "../WeIde/components/IDEContent/FileExplorer/utils/fileSystem";
import { deleteFile } from "../WeIde/components/IDEContent/FileExplorer/utils/fileSystem";
import { useFileStore } from "../WeIde/stores/fileStore";
import useTerminalStore from "@/stores/terminalSlice";
import { Message } from "ai/react";
import { WebSocketConnectionManager, ConnectionStatus } from "./websocketConnectionManager";
import { SSEConnectionManager, SSEConnectionStatus } from "./sseConnectionManager";
import { useEffect, useState, useCallback, useRef } from "react";

class Queue {
  private queue: string[] = [];
  private processing: boolean = false;

  // 添加命令到队列
  push(command: string) {
    this.queue.push(command);
    this.process();
  }

  // 获取队列中的下一个命令
  private getNext(): string | undefined {
    return this.queue.shift();
  }

  // 处理队列
  private async process() {
    if (this.processing || this.queue.length === 0) {
      return;
    }

    this.processing = true;
    try {
      while (this.queue.length > 0) {
        const command = this.getNext();
        if (command) {
          console.log("执行命令", command);
          await useTerminalStore.getState().getTerminal(0).executeCommand(command);
        }
      }
    } finally {
      this.processing = false;
    }
  }
}

export const queue = new Queue();


class List {
  private isRunArray: string[] = [];
  private nowArray: string[] = [];

  // 添加命令到队列
  run(commands: string[]) {
    this.nowArray = commands
    this.process();
  }

  private getCommand(number: number) {
    return this.nowArray?.[number];
  }

  // 判断命令是否已经执行
  private getIsRun(number: number) {
    return this.isRunArray?.[number];
  }

  // 处理队列
  private async process() {
    for (let i = 0; i < this.nowArray.length; i++) {
      const command = this.getCommand(i);
      const isRuned = this.getIsRun(i);
      if (command && command !== isRuned) {
        this.isRunArray[i] = command;
        queue.push(command);
      }
    }
  }

  // 清空队列
  clear() {
    this.nowArray = [];
    this.isRunArray = [];
  }
}

export const execList = new List();

// 创建 SSE 消息解析器实例
const messageParser = new SSEMessageParser({
  // 安全配置
  maxMessageSize: 10 * 1024 * 1024, // 10MB
  maxOperationsPerMessage: 100,
  enableValidation: true,
  timeout: 30000, // 30秒

  // 回调函数 - 匹配原有的操作逻辑
  callbacks: {
    // 文件添加操作
    onAddStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 开始添加文件:', fileData.filePath);
    },

    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      // 流式更新文件内容（匹配原有的 onActionStream 逻辑）
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await createFileWithContent(fileData.filePath, fileData.content, true);
          // 文件创建/更新后，选择当前文件并在预览区域展示
          const { setSelectedPath } = useFileStore.getState();
          setSelectedPath(fileData.filePath);
          console.log('[SSE] 文件已选择并在预览区域展示:', fileData.filePath);
        } catch (error) {
          console.error('[SSE] 添加文件进度更新失败:', error);
        }
      }
    },

    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 完成添加文件:', fileData.filePath);

      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await createFileWithContent(fileData.filePath, fileData.content, true);
          // 文件创建完成，选择当前文件并在预览区域展示
          const { setSelectedPath } = useFileStore.getState();
          setSelectedPath(fileData.filePath);
          console.log('[SSE] 文件已选择并在预览区域展示:', fileData.filePath);
        } catch (error) {
          console.error('[SSE] 创建文件失败:', error);
        }
      }
    },

    // 文件编辑操作
    onEditStart: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 开始编辑文件:', fileData.filePath);
    },

    onEditProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      // 流式更新文件内容
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await useFileStore.getState().updateContent(fileData.filePath, fileData.content, false, false);
          // 文件编辑时，选择当前文件并在预览区域展示
          const { setSelectedPath } = useFileStore.getState();
          setSelectedPath(fileData.filePath);
        } catch (error) {
          console.error('[SSE] 编辑文件进度更新失败:', error);
        }
      }
    },

    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[SSE] 完成编辑文件:', fileData.filePath);

      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await useFileStore.getState().updateContent(fileData.filePath, fileData.content, false, true);
          // 文件编辑完成，选择当前文件并在预览区域展示
          const { setSelectedPath } = useFileStore.getState();
          setSelectedPath(fileData.filePath);
          console.log('[SSE] 文件已选择并在预览区域展示:', fileData.filePath);
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
      
      if (fileData.filePath) {
        try {
          deleteFile(fileData.filePath);
        } catch (error) {
          console.error('[SSE] 删除文件失败:', error);
        }
      }
    },

    // 命令执行（匹配原有的命令执行逻辑）
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
    },
  },
});

/**
 * 解析消息（兼容原有接口）
 * 如果消息内容包含 JSON 格式的 WebSocket 消息，则解析
 * 否则保持原有逻辑（用于向后兼容）
 */
export const parseMessages = async (messages: Message[]) => {
  console.log('[SSE] 解析消息11:', messages);
  for (let i = 0; i < messages.length; i++) {
    const message = messages[i];
    if (message.role === "assistant" && message.content) {
      try {
        console.log('[SSE] 解析消息22:', message.content);
        // 尝试解析为 JSON 格式的 WebSocket 消息
        const jsonMessage = JSON.parse(message.content);
        console.log('[SSE] 解析消息33:', jsonMessage);
        if (jsonMessage.event && jsonMessage.data) {
          // 这是 WebSocket 格式的消息
          messageParser.parse(message.id, jsonMessage);
        } else {
          // 不是 WebSocket 格式，可能是其他格式，跳过
          console.warn('[SSE] 消息格式不匹配，跳过:', message.id);
        }
      } catch (e) {
        // 不是 JSON 格式，可能是文本内容，跳过
        // 保持向后兼容，不抛出错误
        console.debug('[SSE] 消息不是 JSON 格式，跳过解析:', message.id);
      }
    }
  }
};

/**
 * 解析单个 SSE 消息（新接口）
 * 用于直接处理 SSE 连接接收的消息
 */
export const parseSSEMessage = (messageId: string, message: string | object): void => {
  console.log('[SSE] 解析消息:', message);
  messageParser.parse(messageId, message);
};

/**
 * 重置解析器状态
 */
export const resetParser = (): void => {
  messageParser.reset();
};

/**
 * 清理指定消息的状态
 */
export const clearMessage = (messageId: string): void => {
  messageParser.clearMessage(messageId);
};

/**
 * 获取消息状态
 */
export const getMessageState = (messageId: string) => {
  return messageParser.getMessageState(messageId);
};

// ==================== 连接管理功能 ====================

/**
 * 创建并配置 WebSocket 连接管理器（带自动重连）
 * 集成了消息解析器，自动解析接收到的消息
 */
export const createWebSocketConnection = (
  url: string,
  options?: {
    autoConnect?: boolean;
    reconnectInterval?: number;
    maxReconnectInterval?: number;
    maxReconnectAttempts?: number;
    heartbeatInterval?: number;
    onStatusChange?: (status: ConnectionStatus) => void;
    onError?: (error: Event) => void;
  }
): WebSocketConnectionManager => {
  const connectionManager = new WebSocketConnectionManager(
    {
      url,
      reconnectInterval: options?.reconnectInterval ?? 1000,
      maxReconnectInterval: options?.maxReconnectInterval ?? 30000,
      reconnectDecay: 1.5,
      maxReconnectAttempts: options?.maxReconnectAttempts ?? Infinity,
      timeout: 10000,
      enableMessageQueue: true,
      maxQueueSize: 100,
      heartbeatInterval: options?.heartbeatInterval ?? 30000,
      heartbeatMessage: 'ping',
    },
    {
      onOpen: (event) => {
        console.log('[SSE] 连接已建立', event);
      },
      onClose: (event) => {
        console.log('[SSE] 连接已关闭', {
          code: event.code,
          reason: event.reason,
          wasClean: event.wasClean,
        });
      },
      onError: (error) => {
        console.error('[SSE] 连接错误', error);
        options?.onError?.(error);
      },
      onMessage: (event) => {
        try {
          // 解析消息并传递给解析器
          const message = JSON.parse(event.data);
          const messageId = message.messageId || `msg_${Date.now()}`;
          
          // 解析消息
          parseSSEMessage(messageId, message);
        } catch (error) {
          console.error('[SSE] 解析消息失败:', error);
        }
      },
      onReconnect: (attempt) => {
        console.log(`[SSE] 正在尝试第 ${attempt} 次重连...`);
      },
      onReconnectFailed: () => {
        console.error('[SSE] 重连失败，已达到最大重连次数');
      },
      onStatusChange: (status) => {
        console.log(`[SSE] 连接状态变更: ${status}`);
        options?.onStatusChange?.(status);
      },
    }
  );

  // 自动连接
  if (options?.autoConnect !== false) {
    connectionManager.connect();
  }

  return connectionManager;
};

/**
 * 创建并配置 SSE 连接管理器（带自动重连）
 * 集成了消息解析器，自动解析接收到的消息
 */
export const createSSEConnection = (
  url: string,
  options?: {
    autoConnect?: boolean;
    reconnectInterval?: number;
    maxReconnectAttempts?: number;
    onStatusChange?: (status: SSEConnectionStatus) => void;
    onError?: (error: Event | Error) => void;
  }
): SSEConnectionManager => {
  const connectionManager = new SSEConnectionManager(
    {
      url,
      withCredentials: false,
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
        options?.onError?.(error);
      },
      onMessage: (event) => {
        try {
          // 解析 SSE 消息
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

          // 获取事件名
          eventName = event.type !== 'message' ? event.type : (message.event || 'message');
          const messageId = message.messageId || `msg_${Date.now()}`;

          // 构建完整的消息对象（包含 event 字段）
          const fullMessage = {
            ...message,
            event: eventName,
          };

          // 解析消息
          parseSSEMessage(messageId, fullMessage);
        } catch (error) {
          console.error('[SSE] 解析消息失败:', error);
        }
      },
      onStatusChange: (status) => {
        console.log(`[SSE] 连接状态变更: ${status}`);
        options?.onStatusChange?.(status);
      },
    }
  );

  // 设置重连配置
  if (options?.reconnectInterval) {
    connectionManager.setReconnectInterval(options.reconnectInterval);
  }
  if (options?.maxReconnectAttempts !== undefined) {
    connectionManager.setMaxReconnectAttempts(options.maxReconnectAttempts);
  } else {
    connectionManager.setMaxReconnectAttempts(Infinity); // 默认无限重连
  }

  // 自动连接
  if (options?.autoConnect !== false) {
    connectionManager.connect();
  }

  return connectionManager;
};

/**
 * React Hook: 使用 WebSocket 连接管理器
 * 提供自动重连、状态管理等功能
 */
export const useWebSocketConnection = (
  url: string | null,
  options?: {
    autoConnect?: boolean;
    reconnectInterval?: number;
    maxReconnectInterval?: number;
    maxReconnectAttempts?: number;
    heartbeatInterval?: number;
    onStatusChange?: (status: ConnectionStatus) => void;
    onError?: (error: Event) => void;
  }
) => {
  const [connectionManager, setConnectionManager] = useState<WebSocketConnectionManager | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');

  useEffect(() => {
    if (!url) {
      return;
    }

    const manager = createWebSocketConnection(url, {
      ...options,
      autoConnect: options?.autoConnect !== false,
      onStatusChange: (newStatus) => {
        setStatus(newStatus);
        options?.onStatusChange?.(newStatus);
      },
    });

    setConnectionManager(manager);

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

/**
 * React Hook: 使用 SSE 连接管理器
 * 提供自动重连、状态管理等功能
 */
export const useSSEConnection = (
  url: string | null,
  options?: {
    autoConnect?: boolean;
    reconnectInterval?: number;
    maxReconnectAttempts?: number;
    onStatusChange?: (status: SSEConnectionStatus) => void;
    onError?: (error: Event | Error) => void;
  }
) => {
  const [connectionManager, setConnectionManager] = useState<SSEConnectionManager | null>(null);
  const [status, setStatus] = useState<SSEConnectionStatus>('disconnected');

  useEffect(() => {
    if (!url) {
      return;
    }

    const manager = createSSEConnection(url, {
      ...options,
      autoConnect: options?.autoConnect !== false,
      onStatusChange: (newStatus) => {
        setStatus(newStatus);
        options?.onStatusChange?.(newStatus);
      },
    });

    setConnectionManager(manager);

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

// ==================== 聊天 SSE Hook ====================

/**
 * React Hook: 直接使用 SSE 发送和接收聊天消息
 * 流程：用户点击发送 -> 建立/使用 SSE 连接 -> 通过 SSE 发送消息 -> 接收服务端推送的消息
 */
export const useChatSSE = (
  sseUrl: string,
  options?: {
    onMessage?: (message: Message) => void;
    onStatusChange?: (status: SSEConnectionStatus) => void;
    onError?: (error: Error | Event) => void;
    reconnectInterval?: number;
    maxReconnectAttempts?: number;
  }
) => {
  const [connectionManager, setConnectionManager] = useState<SSEConnectionManager | null>(null);
  const [status, setStatus] = useState<SSEConnectionStatus>('disconnected');
  const [isConnecting, setIsConnecting] = useState(false);
  const currentAssistantMessageRef = useRef<Message | null>(null);
  const connectionManagerRef = useRef<SSEConnectionManager | null>(null);

  // 初始化连接管理器（只在首次创建）
  useEffect(() => {
    if (connectionManager) {
      return; // 已经创建过了
    }

    const manager = new SSEConnectionManager(
      {
        url: sseUrl,
        withCredentials: false,
      },
      {
        onOpen: (event) => {
          console.log('[ChatSSE] 连接已建立', event);
          setIsConnecting(false);
        },
        onClose: () => {
          console.log('[ChatSSE] 连接已关闭');
          setIsConnecting(false);
        },
        onError: (error) => {
          console.error('[ChatSSE] SSE 错误:', error);
          setIsConnecting(false);
          options?.onError?.(error);
        },
        onMessage: (event: MessageEvent) => {
          try {
            const data = JSON.parse(event.data);

            // 检查是否是文本消息（用于更新 UI）
            if (data.type === 'text' || data.content) {
              const textContent = data.content || data.text || '';

              // 创建或更新助手消息
              if (!currentAssistantMessageRef.current) {
                currentAssistantMessageRef.current = {
                  id: data.messageId || `msg_${Date.now()}`,
                  role: 'assistant',
                  content: textContent,
                };
              } else {
                currentAssistantMessageRef.current = {
                  ...currentAssistantMessageRef.current,
                  content: (currentAssistantMessageRef.current.content || '') + textContent,
                };
              }

              // 通知外部组件更新消息
              if (options?.onMessage && currentAssistantMessageRef.current) {
                options.onMessage(currentAssistantMessageRef.current);
              }
            }

            // 检查是否是完成消息
            if (data.type === 'done' || data.finishReason) {
              if (currentAssistantMessageRef.current && options?.onMessage) {
                options.onMessage(currentAssistantMessageRef.current);
              }
              currentAssistantMessageRef.current = null;
            }

            // 同时调用原有的消息解析逻辑（处理文件操作等）
            const messageId = data.messageId || `msg_${Date.now()}`;
            parseSSEMessage(messageId, data);
          } catch (error) {
            console.error('[ChatSSE] 解析消息失败:', error);
          }
        },
        onStatusChange: (newStatus) => {
          setStatus(newStatus);
          options?.onStatusChange?.(newStatus);
        },
      }
    );

    // 设置重连配置
    if (options?.reconnectInterval) {
      manager.setReconnectInterval(options.reconnectInterval);
    }
    if (options?.maxReconnectAttempts !== undefined) {
      manager.setMaxReconnectAttempts(options.maxReconnectAttempts);
    } else {
      manager.setMaxReconnectAttempts(Infinity); // 默认无限重连
    }

    setConnectionManager(manager);
    connectionManagerRef.current = manager;
  }, [sseUrl, options]);

  // SSE 方式发送消息（通过 URL 参数或初始请求）
  const sendMessage = useCallback(async (
    requestBody: {
      messages: Message[];
      model?: string;
      mode?: string;
      otherConfig?: any;
      tools?: any[];
    }
  ) => {
    // 使用 ref 获取最新的连接管理器
    const manager = connectionManagerRef.current;

    // 如果连接管理器不存在，等待它创建
    if (!manager) {
      console.warn('[ChatSSE] 连接管理器尚未初始化，等待创建...');
      // 等待连接管理器创建（最多等待 2 秒）
      let attempts = 0;
      const maxAttempts = 20; // 20 * 100ms = 2秒
      while (!connectionManagerRef.current && attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
      }

      const currentManager = connectionManagerRef.current;
      if (!currentManager) {
        console.error('[ChatSSE] 连接管理器初始化超时');
        options?.onError?.(new Error('连接管理器初始化超时'));
        return;
      }

      // 如果未连接，尝试连接
      if (!currentManager.isConnected()) {
        setIsConnecting(true);
        currentManager.connect();

        // 等待连接建立（最多等待 5 秒）
        let connectAttempts = 0;
        const maxConnectAttempts = 50; // 50 * 100ms = 5秒
        while (!currentManager.isConnected() && connectAttempts < maxConnectAttempts) {
          await new Promise(resolve => setTimeout(resolve, 100));
          connectAttempts++;
        }

        if (!currentManager.isConnected()) {
          console.error('[ChatSSE] 连接超时，无法发送消息');
          setIsConnecting(false);
          options?.onError?.(new Error('SSE 连接超时'));
          return;
        }
      }
      return;
    }

    // 如果未连接，尝试连接
    if (!manager.isConnected()) {
      setIsConnecting(true);
      manager.connect();

      // 等待连接建立（最多等待 5 秒）
      let attempts = 0;
      const maxAttempts = 50; // 50 * 100ms = 5秒
      while (!manager.isConnected() && attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
      }

      if (!manager.isConnected()) {
        console.error('[ChatSSE] 连接超时，无法发送消息');
        setIsConnecting(false);
        options?.onError?.(new Error('SSE 连接超时'));
        return;
      }
    }

    // SSE 是单向的，消息通过 URL 参数或服务器配置发送
    // 这里我们记录发送意图，但实际消息通过 SSE 流接收
    console.log('[ChatSSE] SSE 连接已建立，等待服务器推送消息');
  }, [options]);

  // 清理函数
  useEffect(() => {
    return () => {
      if (connectionManagerRef.current) {
        connectionManagerRef.current.close();
        connectionManagerRef.current = null;
      }
    };
  }, []);

  return {
    connectionManager,
    status,
    isConnecting,
    sendMessage,
    disconnect: () => {
      if (connectionManagerRef.current) {
        connectionManagerRef.current.close();
        connectionManagerRef.current = null;
      }
      setConnectionManager(null);
      setStatus('disconnected');
      currentAssistantMessageRef.current = null;
    },
    isConnected: () => connectionManagerRef.current?.isConnected() ?? false,
  };
};