/**
 * WebSocket 连接管理器
 * 提供自动重连、消息队列、连接状态管理等功能
 */

// ==================== 类型定义 ====================

/**
 * 连接状态
 */
export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'failed';

/**
 * 连接配置选项
 */
export interface WebSocketConnectionOptions {
  url: string;
  protocols?: string | string[];
  reconnectInterval?: number;        // 初始重连间隔（毫秒），默认 1000ms
  maxReconnectInterval?: number;     // 最大重连间隔（毫秒），默认 30000ms
  reconnectDecay?: number;           // 重连间隔增长因子，默认 1.5
  maxReconnectAttempts?: number;     // 最大重连次数，默认 Infinity（无限重连）
  timeout?: number;                  // 连接超时时间（毫秒），默认 10000ms
  enableMessageQueue?: boolean;       // 是否启用消息队列，默认 true
  maxQueueSize?: number;             // 最大队列大小，默认 100
  heartbeatInterval?: number;         // 心跳间隔（毫秒），默认 30000ms
  heartbeatMessage?: string;        // 心跳消息，默认 'ping'
}

/**
 * 连接事件回调
 */
export interface ConnectionCallbacks {
  onOpen?: (event: Event) => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (error: Event) => void;
  onMessage?: (event: MessageEvent) => void;
  onReconnect?: (attempt: number) => void;
  onReconnectFailed?: () => void;
  onStatusChange?: (status: ConnectionStatus) => void;
}

/**
 * 消息队列项
 */
interface QueuedMessage {
  data: string | ArrayBuffer | Blob;
  timestamp: number;
}

// ==================== WebSocket 连接管理器类 ====================

export class WebSocketConnectionManager {
  private ws: WebSocket | null = null;
  private status: ConnectionStatus = 'disconnected';
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private connectionTimer: ReturnType<typeof setTimeout> | null = null;
  private messageQueue: QueuedMessage[] = [];
  private options: Required<Pick<WebSocketConnectionOptions, 
    'reconnectInterval' | 'maxReconnectInterval' | 'reconnectDecay' | 
    'maxReconnectAttempts' | 'timeout' | 'enableMessageQueue' | 
    'maxQueueSize' | 'heartbeatInterval' | 'heartbeatMessage'>> & 
    Omit<WebSocketConnectionOptions, 
    'reconnectInterval' | 'maxReconnectInterval' | 'reconnectDecay' | 
    'maxReconnectAttempts' | 'timeout' | 'enableMessageQueue' | 
    'maxQueueSize' | 'heartbeatInterval' | 'heartbeatMessage'>;
  private callbacks: ConnectionCallbacks = {};
  private shouldReconnect = true;
  private isManualClose = false;

  constructor(
    options: WebSocketConnectionOptions,
    callbacks?: ConnectionCallbacks
  ) {
    this.options = {
      reconnectInterval: options.reconnectInterval ?? 1000,
      maxReconnectInterval: options.maxReconnectInterval ?? 30000,
      reconnectDecay: options.reconnectDecay ?? 1.5,
      maxReconnectAttempts: options.maxReconnectAttempts ?? Infinity,
      timeout: options.timeout ?? 10000,
      enableMessageQueue: options.enableMessageQueue ?? true,
      maxQueueSize: options.maxQueueSize ?? 100,
      heartbeatInterval: options.heartbeatInterval ?? 30000,
      heartbeatMessage: options.heartbeatMessage ?? 'ping',
      ...options,
    };
    this.callbacks = callbacks || {};
  }

  /**
   * 连接 WebSocket
   */
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      console.log('[WebSocket] 已经连接');
      return;
    }

    if (this.ws?.readyState === WebSocket.CONNECTING) {
      console.log('[WebSocket] 正在连接中...');
      return;
    }

    this.isManualClose = false;
    this.shouldReconnect = true;
    this.setStatus('connecting');

    try {
      this.ws = new WebSocket(this.options.url, this.options.protocols);
      this.setupEventHandlers();

      // 设置连接超时
      this.connectionTimer = setTimeout(() => {
        if (this.ws?.readyState !== WebSocket.OPEN) {
          console.warn('[WebSocket] 连接超时');
          this.ws?.close();
          this.handleReconnect();
        }
      }, this.options.timeout);
    } catch (error) {
      console.error('[WebSocket] 连接失败:', error);
      this.setStatus('failed');
      this.callbacks.onError?.(error as Event);
      this.handleReconnect();
    }
  }

  /**
   * 设置事件处理器
   */
  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = (event: Event) => {
      console.log('[WebSocket] 连接成功');
      this.clearConnectionTimer();
      this.reconnectAttempts = 0;
      this.setStatus('connected');
      this.callbacks.onOpen?.(event);

      // 启动心跳
      this.startHeartbeat();

      // 发送队列中的消息
      this.flushMessageQueue();
    };

    this.ws.onclose = (event: CloseEvent) => {
      console.log('[WebSocket] 连接关闭', {
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean,
      });
      this.clearConnectionTimer();
      this.stopHeartbeat();
      this.setStatus('disconnected');
      this.callbacks.onClose?.(event);

      // 如果不是手动关闭，则尝试重连
      if (!this.isManualClose && this.shouldReconnect) {
        this.handleReconnect();
      }
    };

    this.ws.onerror = (error: Event) => {
      console.error('[WebSocket] 连接错误:', error);
      this.setStatus('failed');
      this.callbacks.onError?.(error);
    };

    this.ws.onmessage = (event: MessageEvent) => {
      // 处理心跳响应
      if (event.data === 'pong' || event.data === this.options.heartbeatMessage) {
        return;
      }

      this.callbacks.onMessage?.(event);
    };
  }

  /**
   * 处理重连逻辑
   */
  private handleReconnect(): void {
    if (!this.shouldReconnect) {
      return;
    }

    // 检查是否超过最大重连次数
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.error('[WebSocket] 达到最大重连次数，停止重连');
      this.setStatus('failed');
      this.shouldReconnect = false;
      this.callbacks.onReconnectFailed?.();
      return;
    }

    // 计算重连间隔（指数退避）
    const interval = Math.min(
      this.options.reconnectInterval * Math.pow(this.options.reconnectDecay, this.reconnectAttempts),
      this.options.maxReconnectInterval
    );

    this.reconnectAttempts++;
    this.setStatus('reconnecting');

    console.log(`[WebSocket] ${interval}ms 后尝试第 ${this.reconnectAttempts} 次重连`);

    this.reconnectTimer = setTimeout(() => {
      this.callbacks.onReconnect?.(this.reconnectAttempts);
      this.connect();
    }, interval);
  }

  /**
   * 发送消息
   */
  send(data: string | ArrayBuffer | Blob): boolean {
    if (this.ws?.readyState === WebSocket.OPEN) {
      try {
        this.ws.send(data);
        return true;
      } catch (error) {
        console.error('[WebSocket] 发送消息失败:', error);
        return false;
      }
    } else {
      // 如果连接未打开，将消息加入队列
      if (this.options.enableMessageQueue) {
        this.enqueueMessage(data);
      } else {
        console.warn('[WebSocket] 连接未打开，消息已丢弃');
      }
      return false;
    }
  }

  /**
   * 将消息加入队列
   */
  private enqueueMessage(data: string | ArrayBuffer | Blob): void {
    // 检查队列大小
    if (this.messageQueue.length >= this.options.maxQueueSize) {
      console.warn('[WebSocket] 消息队列已满，丢弃最旧的消息');
      this.messageQueue.shift();
    }

    this.messageQueue.push({
      data,
      timestamp: Date.now(),
    });
  }

  /**
   * 清空并发送队列中的消息
   */
  private flushMessageQueue(): void {
    if (this.messageQueue.length === 0) {
      return;
    }

    console.log(`[WebSocket] 发送队列中的 ${this.messageQueue.length} 条消息`);

    const messages = [...this.messageQueue];
    this.messageQueue = [];

    messages.forEach((item) => {
      // 检查消息是否过期（超过5分钟的消息丢弃）
      const age = Date.now() - item.timestamp;
      if (age > 5 * 60 * 1000) {
        console.warn('[WebSocket] 消息已过期，已丢弃');
        return;
      }

      this.send(item.data);
    });
  }

  /**
   * 启动心跳
   */
  private startHeartbeat(): void {
    this.stopHeartbeat();

    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        try {
          this.ws.send(this.options.heartbeatMessage);
        } catch (error) {
          console.error('[WebSocket] 发送心跳失败:', error);
        }
      }
    }, this.options.heartbeatInterval);
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * 设置连接状态
   */
  private setStatus(status: ConnectionStatus): void {
    if (this.status !== status) {
      this.status = status;
      this.callbacks.onStatusChange?.(status);
    }
  }

  /**
   * 清除连接超时定时器
   */
  private clearConnectionTimer(): void {
    if (this.connectionTimer) {
      clearTimeout(this.connectionTimer);
      this.connectionTimer = null;
    }
  }

  /**
   * 关闭连接
   */
  close(code?: number, reason?: string): void {
    this.isManualClose = true;
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    this.stopHeartbeat();
    this.clearConnectionTimer();

    if (this.ws) {
      this.ws.close(code, reason);
      this.ws = null;
    }

    this.setStatus('disconnected');
  }

  /**
   * 清除重连定时器
   */
  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * 获取连接状态
   */
  getStatus(): ConnectionStatus {
    return this.status;
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * 获取重连次数
   */
  getReconnectAttempts(): number {
    return this.reconnectAttempts;
  }

  /**
   * 获取队列大小
   */
  getQueueSize(): number {
    return this.messageQueue.length;
  }

  /**
   * 清空消息队列
   */
  clearQueue(): void {
    this.messageQueue = [];
  }

  /**
   * 重置重连计数
   */
  resetReconnectAttempts(): void {
    this.reconnectAttempts = 0;
  }

  /**
   * 更新回调函数
   */
  updateCallbacks(callbacks: Partial<ConnectionCallbacks>): void {
    this.callbacks = { ...this.callbacks, ...callbacks };
  }

  /**
   * 销毁连接管理器
   */
  destroy(): void {
    this.close();
    this.clearQueue();
    this.callbacks = {};
  }
}

export default WebSocketConnectionManager;

