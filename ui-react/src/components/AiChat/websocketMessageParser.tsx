/**
 * WebSocket 消息解析器
 * 基于 WebSocket 协议解析结构化消息事件
 * 支持流式处理和多种操作类型（add/edit/delete/cmd）
 */

// ==================== 类型定义 ====================

/**
 * 操作类型
 */
export type OperationType = 'add' | 'edit' | 'delete' | 'cmd';

/**
 * 事件阶段
 */
export type EventPhase = 'start' | 'progress' | 'end';

/**
 * 事件名称
 */
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

/**
 * 基础操作数据
 */
export interface BaseOperationData {
  content: string;
  timestamp?: number;
  messageId?: string;
  operationId?: string;
}

/**
 * 文件操作数据
 */
export interface FileOperationData extends BaseOperationData {
  type: 'add' | 'edit' | 'delete';
  filePath: string;
  encoding?: string;
  mode?: string;
}

/**
 * 命令操作数据
 */
export interface CommandOperationData extends BaseOperationData {
  type: 'cmd';
  command: string;
  workingDir?: string;
  env?: Record<string, string>;
}

/**
 * 操作数据联合类型
 */
export type OperationData = FileOperationData | CommandOperationData;

/**
 * WebSocket 消息格式
 */
export interface WebSocketMessage {
  event: EventName;
  data: OperationData;
  messageId?: string;
  operationId?: string;
  error?: string;
}

/**
 * 回调数据
 */
export interface OperationCallbackData {
  event: EventName;
  data: OperationData;
  messageId: string;
  operationId: string;
}

/**
 * 解析器回调函数
 */
export type OperationCallback = (data: OperationCallbackData) => void | Promise<void>;

/**
 * 解析器回调配置
 */
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
  onError?: (error: Error, message?: WebSocketMessage) => void;
}

/**
 * 解析器配置选项
 */
export interface WebSocketMessageParserOptions {
  callbacks?: ParserCallbacks;
  maxMessageSize?: number;           // 最大消息大小（字节），默认 10MB
  maxOperationsPerMessage?: number;   // 每条消息最大操作数，默认 100
  enableValidation?: boolean;        // 是否启用验证，默认 true
  timeout?: number;                   // 操作超时时间（毫秒），默认 30秒
  allowedFilePaths?: string[];        // 允许的文件路径白名单（可选）
  blockedFilePaths?: string[];       // 阻止的文件路径黑名单（可选）
  allowedCommands?: string[];         // 允许的命令白名单（可选）
  blockedCommands?: string[];         // 阻止的命令黑名单（可选）
}

/**
 * 操作状态
 */
interface OperationState {
  operationId: string;
  type: OperationType;
  phase: EventPhase;
  startTime: number;
  data: Partial<OperationData>;
  messageId: string;
}

/**
 * 消息状态
 */
interface MessageState {
  messageId: string;
  operations: Map<string, OperationState>;
  receivedAt: number;
  operationCount: number;
}

// ==================== 工具函数 ====================

/**
 * 验证文件路径安全性
 */
function validateFilePath(filePath: string, options: WebSocketMessageParserOptions): { valid: boolean; error?: string } {
  if (!filePath || typeof filePath !== 'string') {
    return { valid: false, error: '文件路径无效' };
  }

  // 检查路径遍历攻击
  if (filePath.includes('..') || filePath.includes('~')) {
    return { valid: false, error: '不允许使用相对路径或用户目录' };
  }

  // 检查绝对路径（Windows 和 Unix）
  if (filePath.startsWith('/') || /^[A-Za-z]:/.test(filePath)) {
    // 如果配置了白名单，只允许白名单路径
    if (options.allowedFilePaths && options.allowedFilePaths.length > 0) {
      const isAllowed = options.allowedFilePaths.some(allowed => 
        filePath.startsWith(allowed) || filePath === allowed
      );
      if (!isAllowed) {
        return { valid: false, error: '文件路径不在允许列表中' };
      }
    }
  }

  // 检查黑名单
  if (options.blockedFilePaths && options.blockedFilePaths.length > 0) {
    const isBlocked = options.blockedFilePaths.some(blocked => 
      filePath.startsWith(blocked) || filePath === blocked
    );
    if (isBlocked) {
      return { valid: false, error: '文件路径在阻止列表中' };
    }
  }

  // 检查文件名长度
  if (filePath.length > 260) {
    return { valid: false, error: '文件路径过长' };
  }

  return { valid: true };
}

/**
 * 验证命令安全性
 */
function validateCommand(command: string, options: WebSocketMessageParserOptions): { valid: boolean; error?: string } {
  if (!command || typeof command !== 'string') {
    return { valid: false, error: '命令无效' };
  }

  // 检查命令长度
  if (command.length > 10000) {
    return { valid: false, error: '命令过长' };
  }

  // 检查危险命令模式
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

  // 检查白名单
  if (options.allowedCommands && options.allowedCommands.length > 0) {
    const isAllowed = options.allowedCommands.some(allowed => 
      command.startsWith(allowed) || command === allowed
    );
    if (!isAllowed) {
      return { valid: false, error: '命令不在允许列表中' };
    }
  }

  // 检查黑名单
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

/**
 * 验证消息格式
 */
function validateMessage(message: any, options: WebSocketMessageParserOptions): { valid: boolean; error?: string } {
  if (!message || typeof message !== 'object') {
    return { valid: false, error: '消息格式无效：必须是对象' };
  }

  if (!message.event || typeof message.event !== 'string') {
    return { valid: false, error: '消息格式无效：缺少 event 字段' };
  }

  if (!message.data || typeof message.data !== 'object') {
    return { valid: false, error: '消息格式无效：缺少 data 字段' };
  }

  // 验证事件名称
  const validEvents: EventName[] = [
    'add-start', 'add-progress', 'add-end',
    'edit-start', 'edit-progress', 'edit-end',
    'delete-start', 'delete-progress', 'delete-end',
    'cmd'
  ];

  if (!validEvents.includes(message.event)) {
    return { valid: false, error: `未知的事件类型: ${message.event}` };
  }

  // 验证数据内容
  const data = message.data;
  
  // 文件操作验证
  if (['add', 'edit', 'delete'].includes(data.type)) {
    if (!data.filePath || typeof data.filePath !== 'string') {
      return { valid: false, error: '文件操作缺少 filePath' };
    }
    
    const pathValidation = validateFilePath(data.filePath, options);
    if (!pathValidation.valid) {
      return pathValidation;
    }

    // 检查内容大小
    if (data.content && typeof data.content === 'string') {
      const contentSize = new Blob([data.content]).size;
      if (contentSize > (options.maxMessageSize || 10 * 1024 * 1024)) {
        return { valid: false, error: '文件内容超过最大限制' };
      }
    }
  }

  // 命令操作验证
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

/**
 * 生成操作ID
 */
function generateOperationId(): string {
  return `op_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

// ==================== 解析器类 ====================

export class WebSocketMessageParser {
  private messages = new Map<string, MessageState>();
  private options: Required<Pick<WebSocketMessageParserOptions, 'maxMessageSize' | 'maxOperationsPerMessage' | 'enableValidation' | 'timeout'>> & 
                  Omit<WebSocketMessageParserOptions, 'maxMessageSize' | 'maxOperationsPerMessage' | 'enableValidation' | 'timeout'>;
  private operationTimeouts = new Map<string, ReturnType<typeof setTimeout>>();

  constructor(options: WebSocketMessageParserOptions = {}) {
    this.options = {
      maxMessageSize: options.maxMessageSize ?? 10 * 1024 * 1024, // 10MB
      maxOperationsPerMessage: options.maxOperationsPerMessage ?? 100,
      enableValidation: options.enableValidation ?? true,
      timeout: options.timeout ?? 30000, // 30秒
      ...options,
    };
  }

  /**
   * 解析 WebSocket 消息
   */
  parse(messageId: string, rawMessage: string | object): void {
    try {
      // 解析 JSON
      let message: WebSocketMessage;
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
        message = rawMessage as WebSocketMessage;
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
      console.error('[WebSocketMessageParser] 解析错误:', err);
    }
  }

  /**
   * 处理消息
   */
  private handleMessage(messageId: string, message: WebSocketMessage, state: MessageState): void {
    const { event, data, operationId: providedOperationId } = message;
    const operationId = providedOperationId || message.data.operationId || generateOperationId();

    // 获取或创建操作状态
    let operationState = state.operations.get(operationId);
    
    if (!operationState) {
      // 新操作
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
    if (callbackName && this.options.callbacks?.[callbackName]) {
      try {
        const result = this.options.callbacks[callbackName]!(data);
        // 如果返回 Promise，捕获错误
        if (result instanceof Promise) {
          result.catch(error => {
            console.error(`[WebSocketMessageParser] 回调执行错误 (${callbackName}):`, error);
            this.options.callbacks?.onError?.(error instanceof Error ? error : new Error(String(error)));
          });
        }
      } catch (error) {
        console.error(`[WebSocketMessageParser] 回调执行错误 (${callbackName}):`, error);
        this.options.callbacks?.onError?.(error instanceof Error ? error : new Error(String(error)));
      }
    }
  }

  /**
   * 获取操作类型
   */
  private getOperationType(event: EventName): OperationType {
    if (event.startsWith('add')) return 'add';
    if (event.startsWith('edit')) return 'edit';
    if (event.startsWith('delete')) return 'delete';
    if (event === 'cmd') return 'cmd';
    return 'add'; // 默认
  }

  /**
   * 获取事件阶段
   */
  private getEventPhase(event: EventName): EventPhase {
    if (event.endsWith('-start')) return 'start';
    if (event.endsWith('-progress')) return 'progress';
    if (event.endsWith('-end')) return 'end';
    return 'start'; // cmd 默认为 start
  }

  /**
   * 设置操作超时
   */
  private setOperationTimeout(operationId: string, operationState: OperationState): void {
    // 清除旧的超时
    const oldTimeout = this.operationTimeouts.get(operationId);
    if (oldTimeout) {
      clearTimeout(oldTimeout);
    }

    // 设置新的超时
    const timeout = setTimeout(() => {
      const error = new Error(`操作超时: ${operationId} (${operationState.type})`);
      this.options.callbacks?.onError?.(error);
      
      // 清理状态
      const state = Array.from(this.messages.values()).find(s => 
        s.operations.has(operationId)
      );
      if (state) {
        this.cleanupOperation(operationId, state);
      }
    }, this.options.timeout);

    this.operationTimeouts.set(operationId, timeout);
  }

  /**
   * 清理操作状态
   */
  private cleanupOperation(operationId: string, state: MessageState): void {
    state.operations.delete(operationId);
    
    const timeout = this.operationTimeouts.get(operationId);
    if (timeout) {
      clearTimeout(timeout);
      this.operationTimeouts.delete(operationId);
    }
  }

  /**
   * 重置所有状态
   */
  reset(): void {
    // 清除所有超时
    this.operationTimeouts.forEach(timeout => clearTimeout(timeout));
    this.operationTimeouts.clear();
    
    // 清除所有消息状态
    this.messages.clear();
  }

  /**
   * 获取消息状态
   */
  getMessageState(messageId: string): MessageState | undefined {
    return this.messages.get(messageId);
  }

  /**
   * 获取操作状态
   */
  getOperationState(messageId: string, operationId: string): OperationState | undefined {
    const state = this.messages.get(messageId);
    return state?.operations.get(operationId);
  }

  /**
   * 清理指定消息的状态
   */
  clearMessage(messageId: string): void {
    const state = this.messages.get(messageId);
    if (state) {
      // 清理该消息的所有操作超时
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

// ==================== 导出 ====================

export default WebSocketMessageParser;

