import { WebSocketMessageParser, OperationCallbackData, FileOperationData, CommandOperationData } from "./websocketMessageParser";
import { createFileWithContent } from "../WeIde/components/IDEContent/FileExplorer/utils/fileSystem";
import { deleteFile } from "../WeIde/components/IDEContent/FileExplorer/utils/fileSystem";
import { updateContent } from "../WeIde/stores/fileStore";
import useTerminalStore from "@/stores/terminalSlice";
import { Message } from "ai/react";

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

// 创建 WebSocket 消息解析器实例
const messageParser = new WebSocketMessageParser({
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
      console.log('[WebSocket] 开始添加文件:', fileData.filePath);
    },

    onAddProgress: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      // 流式更新文件内容（匹配原有的 onActionStream 逻辑）
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await createFileWithContent(fileData.filePath, fileData.content, true);
        } catch (error) {
          console.error('[WebSocket] 添加文件进度更新失败:', error);
        }
      }
    },

    onAddEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 完成添加文件:', fileData.filePath);
      
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await createFileWithContent(fileData.filePath, fileData.content, true);
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
      // 流式更新文件内容
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await updateContent(fileData.filePath, fileData.content, false, false);
        } catch (error) {
          console.error('[WebSocket] 编辑文件进度更新失败:', error);
        }
      }
    },

    onEditEnd: async (data: OperationCallbackData) => {
      const fileData = data.data as FileOperationData;
      console.log('[WebSocket] 完成编辑文件:', fileData.filePath);
      
      if (fileData.content !== undefined && fileData.filePath) {
        try {
          await updateContent(fileData.filePath, fileData.content, false, true);
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
      
      if (fileData.filePath) {
        try {
          deleteFile(fileData.filePath);
        } catch (error) {
          console.error('[WebSocket] 删除文件失败:', error);
        }
      }
    },

    // 命令执行（匹配原有的命令执行逻辑）
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
    },
  },
});

/**
 * 解析消息（兼容原有接口）
 * 如果消息内容包含 JSON 格式的 WebSocket 消息，则解析
 * 否则保持原有逻辑（用于向后兼容）
 */
export const parseMessages = async (messages: Message[]) => {
  for (let i = 0; i < messages.length; i++) {
    const message = messages[i];
    if (message.role === "assistant" && message.content) {
      try {
        // 尝试解析为 JSON 格式的 WebSocket 消息
        const jsonMessage = JSON.parse(message.content);
        if (jsonMessage.event && jsonMessage.data) {
          // 这是 WebSocket 格式的消息
          messageParser.parse(message.id, jsonMessage);
        } else {
          // 不是 WebSocket 格式，可能是其他格式，跳过
          console.warn('[WebSocket] 消息格式不匹配，跳过:', message.id);
        }
      } catch (e) {
        // 不是 JSON 格式，可能是文本内容，跳过
        // 保持向后兼容，不抛出错误
        console.debug('[WebSocket] 消息不是 JSON 格式，跳过解析:', message.id);
      }
    }
  }
};

/**
 * 解析单个 WebSocket 消息（新接口）
 * 用于直接处理 WebSocket 连接接收的消息
 */
export const parseWebSocketMessage = (messageId: string, message: string | object): void => {
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