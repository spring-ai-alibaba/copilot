/**
 * SSE 连接管理器
 * 基于 EventSource API 管理 Server-Sent Events 连接
 * 提供自动重连、状态管理和错误处理
 */

export type SSEConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'failed';

export interface SSEConnectionOptions {
  url: string;
  withCredentials?: boolean; // 是否发送凭证（cookies）
  headers?: Record<string, string>; // 自定义请求头（注意：EventSource 原生不支持，需要 fetch + ReadableStream 实现）
}

export interface SSEConnectionCallbacks {
  onOpen?: (event: Event) => void;
  onMessage?: (event: MessageEvent) => void;
  onError?: (error: Event | Error) => void;
  onClose?: () => void;
  onStatusChange?: (status: SSEConnectionStatus) => void;
}

/**
 * SSE 连接管理器
 * 
 * 注意：原生 EventSource API 的限制：
 * 1. 只支持 GET 请求
 * 2. 不支持自定义请求头（除了 withCredentials）
 * 3. 自动重连（浏览器内置）
 * 4. 只支持文本数据
 * 
 * 如果需要自定义请求头，需要使用 fetch + ReadableStream 实现
 */
export class SSEConnectionManager {
  private eventSource: EventSource | null = null;
  private status: SSEConnectionStatus = 'disconnected';
  private options: SSEConnectionOptions;
  private callbacks: SSEConnectionCallbacks;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = Infinity;
  private reconnectInterval = 1000;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isManualClose = false;

  constructor(options: SSEConnectionOptions, callbacks: SSEConnectionCallbacks = {}) {
    this.options = options;
    this.callbacks = callbacks;
  }

  /**
   * 连接 SSE
   */
  connect(): void {
    if (this.eventSource && this.eventSource.readyState !== EventSource.CLOSED) {
      console.warn('[SSE] 连接已存在，无需重复连接');
      return;
    }

    this.isManualClose = false;
    this.setStatus('connecting');

    try {
      // 创建 EventSource 连接
      this.eventSource = new EventSource(this.options.url, {
        withCredentials: this.options.withCredentials ?? false,
      });

      // 监听连接打开
      this.eventSource.onopen = (event) => {
        this.reconnectAttempts = 0;
        this.setStatus('connected');
        this.callbacks.onOpen?.(event);
        console.log('[SSE] 连接已建立');
      };

      // 监听消息
      // EventSource 会通过 event 字段区分不同的事件类型
      // 后端发送格式：event: <eventName>\ndata: <json>\n\n
      this.eventSource.onmessage = (event) => {
        // 默认消息（没有 event 字段）会触发 onmessage
        // 有 event 字段的消息会触发对应的事件监听器
        this.callbacks.onMessage?.(event);
      };

      // 监听错误
      this.eventSource.onerror = (error) => {
        console.error('[SSE] 连接错误:', error);
        this.callbacks.onError?.(error);

        // 如果连接关闭且不是手动关闭，尝试重连
        if (this.eventSource?.readyState === EventSource.CLOSED && !this.isManualClose) {
          this.handleReconnect();
        } else if (this.eventSource?.readyState === EventSource.CONNECTING) {
          this.setStatus('reconnecting');
        }
      };

      // 监听自定义事件（后端通过 event 字段发送的事件）
      // 这些事件会通过 addEventListener 监听
      // 例如：eventSource.addEventListener('add-start', handler)
      // 但这里我们统一通过 onmessage 处理，因为解析器会处理 event 字段

    } catch (error) {
      console.error('[SSE] 创建连接失败:', error);
      this.setStatus('failed');
      this.callbacks.onError?.(error instanceof Error ? error : new Error(String(error)));
    }
  }

  /**
   * 处理重连
   */
  private handleReconnect(): void {
    if (this.isManualClose) {
      return;
    }

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.setStatus('failed');
      console.error('[SSE] 达到最大重连次数，停止重连');
      return;
    }

    this.reconnectAttempts++;
    this.setStatus('reconnecting');

    // 清除旧的连接
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    // 延迟重连
    this.reconnectTimer = setTimeout(() => {
      console.log(`[SSE] 尝试第 ${this.reconnectAttempts} 次重连...`);
      this.connect();
    }, this.reconnectInterval);
  }

  /**
   * 关闭连接
   */
  close(): void {
    this.isManualClose = true;
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    this.setStatus('disconnected');
    this.callbacks.onClose?.();
    console.log('[SSE] 连接已关闭');
  }

  /**
   * 销毁连接（清理资源）
   */
  destroy(): void {
    this.close();
    this.callbacks = {};
  }

  /**
   * 设置状态
   */
  private setStatus(status: SSEConnectionStatus): void {
    if (this.status !== status) {
      this.status = status;
      this.callbacks.onStatusChange?.(status);
    }
  }

  /**
   * 获取当前状态
   */
  getStatus(): SSEConnectionStatus {
    return this.status;
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.status === 'connected' && 
           this.eventSource?.readyState === EventSource.OPEN;
  }

  /**
   * 添加事件监听器（用于监听特定的 SSE 事件）
   * 注意：EventSource 原生支持通过 addEventListener 监听自定义事件
   */
  addEventListener(event: string, handler: (event: MessageEvent) => void): void {
    if (this.eventSource) {
      this.eventSource.addEventListener(event, handler);
    }
  }

  /**
   * 移除事件监听器
   */
  removeEventListener(event: string, handler: (event: MessageEvent) => void): void {
    if (this.eventSource) {
      this.eventSource.removeEventListener(event, handler);
    }
  }

  /**
   * 设置最大重连次数
   */
  setMaxReconnectAttempts(maxAttempts: number): void {
    this.maxReconnectAttempts = maxAttempts;
  }

  /**
   * 设置重连间隔
   */
  setReconnectInterval(interval: number): void {
    this.reconnectInterval = interval;
  }
}

