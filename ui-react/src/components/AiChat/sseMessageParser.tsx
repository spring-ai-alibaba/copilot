/**
 * SSE 消息解析器
 * 基于 Server-Sent Events (SSE) 协议解析结构化消息事件
 * 支持流式处理和多种操作类型（add/edit/delete/cmd）
 * 
 * SSE 与 WebSocket 的主要区别：
 * 1. SSE 是单向通信（服务器到客户端），基于 HTTP
 * 2. SSE 使用 EventSource API，通过 event 字段区分事件类型
 * 3. SSE 自动重连，无需手动实现
 * 4. SSE 消息格式：event: <eventName>\ndata: <json>\n\n
 */

// 复用 WebSocket 解析器的类型定义
export type OperationType = 'add' | 'edit' | 'delete' | 'cmd';
export type EventPhase = 'start' | 'progress' | 'end';
export type EventName = 
  | 'add-start' 
  | 'add-progress' 
  | 'add-end'
  | 'edit-start' 
  | 'edit-progress' 
  | 'edit-end'
  | 'delete-start' 
  | 'delete-progress' 
  | 'delete-end'
  | 'cmd';

export interface BaseOperationData {
  content: string;
  timestamp?: number;
  messageId?: string;
  operationId?: string;
}

export interface FileOperationData extends BaseOperationData {
  type: 'add' | 'edit' | 'delete';
  filePath: string;
  encoding?: string;
  mode?: string;
}

export interface CommandOperationData extends BaseOperationData {
  type: 'cmd';
  command: string;
  workingDir?: string;
  env?: Record<string, string>;
}

export type OperationData = FileOperationData | CommandOperationData;

/**
 * SSE 消息格式
 * 后端通过 event 字段发送事件名，data 字段包含 JSON 数据
 */
export interface SSEMessage {
  event: EventName;
  data: OperationData;
  messageId?: string;
  operationId?: string;
  error?: string;
}

export interface OperationCallbackData {
  event: EventName;
  data: OperationData;
  messageId: string;
  operationId: string;
}

export type OperationCallback = (data: OperationCallbackData) => void | Promise<void>;

export interface ParserCallbacks {
  onAddStart?: OperationCallback;
  onAddProgress?: OperationCallback;
  onAddEnd?: OperationCallback;
  onEditStart?: OperationCallback;
  onEditProgress?: OperationCallback;
  onEditEnd?: OperationCallback;
  onDeleteStart?: OperationCallback;
  onDeleteProgress?: OperationCallback;
  onDeleteEnd?: OperationCallback;
  onCmd?: OperationCallback;
  onError?: (error: Error, message?: SSEMessage) => void;
}

export interface SSEMessageParserOptions {
  callbacks?: ParserCallbacks;
  maxMessageSize?: number;
  maxOperationsPerMessage?: number;
  enableValidation?: boolean;
  timeout?: number;
  allowedFilePaths?: string[];
  blockedFilePaths?: string[];
  allowedCommands?: string[];
  blockedCommands?: string[];
}

interface OperationState {
  operationId: string;
  type: OperationType;
  phase: EventPhase;
  startTime: number;
  data: Partial<OperationData>;
  messageId: string;
}

interface MessageState {
  messageId: string;
  operations: Map<string, OperationState>;
  receivedAt: number;
  operationCount: number;
}

// ==================== 工具函数（复用 WebSocket 解析器的验证逻辑）====================

function validateFilePath(filePath: string, options: SSEMessageParserOptions): { valid: boolean; error?: string } {
  if (!filePath || typeof filePath !== 'string') {
    return { valid: false, error: '文件路径无效' };
  }

  if (filePath.includes('..') || filePath.includes('~')) {
    return { valid: false, error: '不允许使用相对路径或用户目录' };
  }

  if (filePath.startsWith('/') || /^[A-Za-z]:/.test(filePath)) {
    if (options.allowedFilePaths && options.allowedFilePaths.length > 0) {
      const isAllowed = options.allowedFilePaths.some(allowed => 
        filePath.startsWith(allowed) || filePath === allowed
      );
      if (!isAllowed) {
        return { valid: false, error: '文件路径不在允许列表中' };
      }
    }
  }

  if (options.blockedFilePaths && options.blockedFilePaths.length > 0) {
    const isBlocked = options.blockedFilePaths.some(blocked => 
      filePath.startsWith(blocked) || filePath === blocked
    );
    if (isBlocked) {
      return { valid: false, error: '文件路径在阻止列表中' };
    }
  }

  if (filePath.length > 260) {
    return { valid: false, error: '文件路径过长' };
  }

  return { valid: true };
}

function validateCommand(command: string, options: SSEMessageParserOptions): { valid: boolean; error?: string } {
  if (!command || typeof command !== 'string') {
    return { valid: false, error: '命令无效' };
  }

  if (command.length > 10000) {
    return { valid: false, error: '命令过长' };
  }

  const dangerousPatterns = [
    /rm\s+-rf/,
    /del\s+\/s/,
    /format\s+[a-z]:/i,
    /mkfs/,
    /dd\s+if=/,
  ];

  for (const pattern of dangerousPatterns) {
    if (pattern.test(command)) {
      return { valid: false, error: '检测到危险命令' };
    }
  }

  if (options.allowedCommands && options.allowedCommands.length > 0) {
    const isAllowed = options.allowedCommands.some(allowed => 
      command.startsWith(allowed) || command === allowed
    );
    if (!isAllowed) {
      return { valid: false, error: '命令不在允许列表中' };
    }
  }

  if (options.blockedCommands && options.blockedCommands.length > 0) {
    const isBlocked = options.blockedCommands.some(blocked => 
      command.startsWith(blocked) || command === blocked
    );
    if (isBlocked) {
      return { valid: false, error: '命令在阻止列表中' };
    }
  }

  return { valid: true };
}

function validateMessage(message: any, options: SSEMessageParserOptions): { valid: boolean; error?: string } {
  if (!message || typeof message !== 'object') {
    return { valid: false, error: '消息格式无效：必须是对象' };
  }

  if (!message.event || typeof message.event !== 'string') {
    return { valid: false, error: '消息格式无效：缺少 event 字段' };
  }

  if (!message.data || typeof message.data !== 'object') {
    return { valid: false, error: '消息格式无效：缺少 data 字段' };
  }

  const validEvents: EventName[] = [
    'add-start', 'add-progress', 'add-end',
    'edit-start', 'edit-progress', 'edit-end',
    'delete-start', 'delete-progress', 'delete-end',
    'cmd'
  ];

  if (!validEvents.includes(message.event)) {
    return { valid: false, error: `未知的事件类型: ${message.event}` };
  }

  const data = message.data;
  
  if (['add', 'edit', 'delete'].includes(data.type)) {
    if (!data.filePath || typeof data.filePath !== 'string') {
      return { valid: false, error: '文件操作缺少 filePath' };
    }
    
    const pathValidation = validateFilePath(data.filePath, options);
    if (!pathValidation.valid) {
      return pathValidation;
    }

    if (data.content && typeof data.content === 'string') {
      const contentSize = new Blob([data.content]).size;
      if (contentSize > (options.maxMessageSize || 10 * 1024 * 1024)) {
        return { valid: false, error: '文件内容超过最大限制' };
      }
    }
  }

  if (data.type === 'cmd') {
    if (!data.command || typeof data.command !== 'string') {
      return { valid: false, error: '命令操作缺少 command' };
    }
    
    const cmdValidation = validateCommand(data.command, options);
    if (!cmdValidation.valid) {
      return cmdValidation;
    }
  }

  return { valid: true };
}

function generateOperationId(): string {
  return `op_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

// ==================== 解析器类 ====================

export class SSEMessageParser {
  private messages = new Map<string, MessageState>();
  private options: Required<Pick<SSEMessageParserOptions, 'maxMessageSize' | 'maxOperationsPerMessage' | 'enableValidation' | 'timeout'>> & 
                  Omit<SSEMessageParserOptions, 'maxMessageSize' | 'maxOperationsPerMessage' | 'enableValidation' | 'timeout'>;
  private operationTimeouts = new Map<string, ReturnType<typeof setTimeout>>();

  constructor(options: SSEMessageParserOptions = {}) {
    this.options = {
      maxMessageSize: options.maxMessageSize ?? 10 * 1024 * 1024,
      maxOperationsPerMessage: options.maxOperationsPerMessage ?? 100,
      enableValidation: options.enableValidation ?? true,
      timeout: options.timeout ?? 30000,
      ...options,
    };
  }

  /**
   * 解析 SSE 消息
   * SSE 消息格式：event: <eventName>\ndata: <json>\n\n
   * 或者直接传入解析后的对象
   */
  parse(messageId: string, rawMessage: string | object): void {
    try {
      let message: SSEMessage;
      console.log('[SSEMessageParser] 解析消息33:', rawMessage);
      if (typeof rawMessage === 'string') {
        // 检查消息大小
        const messageSize = new Blob([rawMessage]).size;
        if (messageSize > this.options.maxMessageSize) {
          throw new Error(`消息大小超过限制: ${messageSize} 字节`);
        }

        try {
          message = JSON.parse(rawMessage);
        } catch (e) {
          throw new Error(`JSON 解析失败: ${e instanceof Error ? e.message : '未知错误'}`);
        }
      } else {
        message = rawMessage as SSEMessage;
      }

      // 验证消息
      if (this.options.enableValidation) {
        const validation = validateMessage(message, this.options);
        if (!validation.valid) {
          throw new Error(validation.error || '消息验证失败');
        }
      }

      // 获取或创建消息状态
      let state = this.messages.get(messageId);
      if (!state) {
        state = {
          messageId,
          operations: new Map(),
          receivedAt: Date.now(),
          operationCount: 0,
        };
        this.messages.set(messageId, state);
      }

      // 检查操作数量限制
      if (state.operationCount >= this.options.maxOperationsPerMessage) {
        throw new Error(`操作数量超过限制: ${this.options.maxOperationsPerMessage}`);
      }

      // 处理消息
      this.handleMessage(messageId, message, state);

    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      this.options.callbacks?.onError?.(err, typeof rawMessage === 'string' ? JSON.parse(rawMessage) : rawMessage);
      console.error('[SSEMessageParser] 解析错误:', err);
    }
  }

  /**
   * 处理消息
   */
  private handleMessage(messageId: string, message: SSEMessage, state: MessageState): void {
    const { event, data, operationId: providedOperationId } = message;
    const operationId = providedOperationId || message.data.operationId || generateOperationId();

    // 获取或创建操作状态
    let operationState = state.operations.get(operationId);
    
    if (!operationState) {
      operationState = {
        operationId,
        type: this.getOperationType(event),
        phase: this.getEventPhase(event),
        startTime: Date.now(),
        data: {},
        messageId,
      };
      state.operations.set(operationId, operationState);
      state.operationCount++;
    }

    // 更新操作数据
    operationState.data = {
      ...operationState.data,
      ...data,
      operationId,
      messageId,
      timestamp: Date.now(),
    };

    // 设置超时
    this.setOperationTimeout(operationId, operationState);

    // 构建回调数据
    const callbackData: OperationCallbackData = {
      event,
      data: operationState.data as OperationData,
      messageId,
      operationId,
    };
    console.log('[SSEMessageParser] 回调数据:', callbackData);
    // 触发相应的回调
    this.triggerCallback(event, callbackData, operationState);

    // 如果是结束事件，清理状态
    if (event.endsWith('-end') || event === 'cmd') {
      this.cleanupOperation(operationId, state);
    }
  }

  /**
   * 触发回调
   */
  private triggerCallback(event: EventName, data: OperationCallbackData, operationState: OperationState): void {
    console.log('[SSEMessageParser] 触发回调:', event, data, operationState);
    const callbackMap: Record<EventName, keyof ParserCallbacks> = {
      'add-start': 'onAddStart',
      'add-progress': 'onAddProgress',
      'add-end': 'onAddEnd',
      'edit-start': 'onEditStart',
      'edit-progress': 'onEditProgress',
      'edit-end': 'onEditEnd',
      'delete-start': 'onDeleteStart',
      'delete-progress': 'onDeleteProgress',
      'delete-end': 'onDeleteEnd',
      'cmd': 'onCmd',
    };

    const callbackName = callbackMap[event];
    if (callbackName && callbackName !== 'onError' && this.options.callbacks?.[callbackName]) {
      try {
        const callback = this.options.callbacks[callbackName] as OperationCallback;
        const result = callback(data);
        if (result instanceof Promise) {
          result.catch(error => {
            console.error(`[SSEMessageParser] 回调执行错误 (${callbackName}):`, error);
            this.options.callbacks?.onError?.(error instanceof Error ? error : new Error(String(error)), undefined);
          });
        }
      } catch (error) {
        console.error(`[SSEMessageParser] 回调执行错误 (${callbackName}):`, error);
        this.options.callbacks?.onError?.(error instanceof Error ? error : new Error(String(error)), undefined);
      }
    }
  }

  private getOperationType(event: EventName): OperationType {
    if (event.startsWith('add')) return 'add';
    if (event.startsWith('edit')) return 'edit';
    if (event.startsWith('delete')) return 'delete';
    if (event === 'cmd') return 'cmd';
    return 'add';
  }

  private getEventPhase(event: EventName): EventPhase {
    if (event.endsWith('-start')) return 'start';
    if (event.endsWith('-progress')) return 'progress';
    if (event.endsWith('-end')) return 'end';
    return 'start';
  }

  private setOperationTimeout(operationId: string, operationState: OperationState): void {
    const oldTimeout = this.operationTimeouts.get(operationId);
    if (oldTimeout) {
      clearTimeout(oldTimeout);
    }

    const timeout = setTimeout(() => {
      const error = new Error(`操作超时: ${operationId} (${operationState.type})`);
      this.options.callbacks?.onError?.(error, undefined);
      
      const state = Array.from(this.messages.values()).find(s => 
        s.operations.has(operationId)
      );
      if (state) {
        this.cleanupOperation(operationId, state);
      }
    }, this.options.timeout);

    this.operationTimeouts.set(operationId, timeout);
  }

  private cleanupOperation(operationId: string, state: MessageState): void {
    state.operations.delete(operationId);
    
    const timeout = this.operationTimeouts.get(operationId);
    if (timeout) {
      clearTimeout(timeout);
      this.operationTimeouts.delete(operationId);
    }
  }

  reset(): void {
    this.operationTimeouts.forEach(timeout => clearTimeout(timeout));
    this.operationTimeouts.clear();
    this.messages.clear();
  }

  getMessageState(messageId: string): MessageState | undefined {
    return this.messages.get(messageId);
  }

  getOperationState(messageId: string, operationId: string): OperationState | undefined {
    const state = this.messages.get(messageId);
    return state?.operations.get(operationId);
  }

  clearMessage(messageId: string): void {
    const state = this.messages.get(messageId);
    if (state) {
      state.operations.forEach((_, operationId) => {
        const timeout = this.operationTimeouts.get(operationId);
        if (timeout) {
          clearTimeout(timeout);
          this.operationTimeouts.delete(operationId);
        }
      });
      this.messages.delete(messageId);
    }
  }
}

export default SSEMessageParser;

