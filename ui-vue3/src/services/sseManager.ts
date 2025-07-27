import { ref, reactive } from 'vue'

export interface SSEEvent {
  type: string
  taskId: string
  message?: string
  timestamp?: string
  toolName?: string
  filePath?: string
  icon?: string
  status?: string
  executionTime?: number
  summary?: string
}

export interface SSECallbacks {
  onOpen?: () => void
  onMessage?: (event: SSEEvent) => void
  onError?: (error: Event) => void
  onClose?: () => void
}

class SSEManager {
  private activeConnections = new Map<string, EventSource>()
  private callbacks = new Map<string, SSECallbacks>()

  /**
   * 建立SSE连接
   */
  async startLogStream(taskId: string, callbacks: SSECallbacks): Promise<void> {
    if (this.activeConnections.has(taskId)) {
      console.log('SSE连接已存在:', taskId)
      return
    }

    console.log('🔗 建立SSE连接:', taskId)

    // 保存回调函数
    this.callbacks.set(taskId, callbacks)

    // 建立EventSource连接
    const eventSource = new EventSource(`/api/logs/stream/${taskId}`)

    eventSource.onopen = () => {
      console.log('✅ SSE连接建立成功:', taskId)
      callbacks.onOpen?.()
    }

    eventSource.onmessage = (event) => {
      try {
        const logEvent: SSEEvent = JSON.parse(event.data)
        console.log('📨 收到日志事件:', logEvent)
        this.handleLogEvent(taskId, logEvent)
      } catch (error) {
        console.error('解析日志事件失败:', error)
      }
    }

    // 监听特定的 "log" 事件
    eventSource.addEventListener('log', (event) => {
      try {
        const logEvent: SSEEvent = JSON.parse(event.data)
        console.log('📨 收到log事件:', logEvent)
        this.handleLogEvent(taskId, logEvent)
      } catch (error) {
        console.error('解析log事件失败:', error)
      }
    })

    eventSource.onerror = (error) => {
      console.error('❌ SSE连接错误:', error)
      callbacks.onError?.(error)
      this.handleConnectionError(taskId)
    }

    this.activeConnections.set(taskId, eventSource)
  }

  /**
   * 处理日志事件
   */
  private handleLogEvent(taskId: string, logEvent: SSEEvent) {
    const callbacks = this.callbacks.get(taskId)
    if (!callbacks) {
      console.warn('找不到回调函数:', taskId)
      return
    }

    // 调用消息回调
    callbacks.onMessage?.(logEvent)

    // 处理特殊事件
    switch (logEvent.type) {
      case 'TASK_COMPLETE':
        this.handleTaskComplete(taskId)
        break
      default:
        // 其他事件类型的处理
        break
    }
  }

  /**
   * 处理任务完成
   */
  private async handleTaskComplete(taskId: string) {
    try {
      // 延迟关闭连接，让最后的事件有时间处理
      setTimeout(() => {
        this.closeConnection(taskId)
      }, 2000)
    } catch (error) {
      console.error('处理任务完成失败:', error)
    }
  }

  /**
   * 关闭SSE连接
   */
  closeConnection(taskId: string) {
    const eventSource = this.activeConnections.get(taskId)
    if (eventSource) {
      eventSource.close()
      this.activeConnections.delete(taskId)
      console.log('🔚 关闭SSE连接:', taskId)
    }

    // 调用关闭回调
    const callbacks = this.callbacks.get(taskId)
    if (callbacks) {
      callbacks.onClose?.()
      this.callbacks.delete(taskId)
    }
  }

  /**
   * 处理连接错误
   */
  private handleConnectionError(taskId: string) {
    console.log('处理连接错误:', taskId)
    
    // 可以实现重连逻辑
    setTimeout(() => {
      if (!this.activeConnections.has(taskId)) {
        console.log('尝试重连:', taskId)
        const callbacks = this.callbacks.get(taskId)
        if (callbacks) {
          this.startLogStream(taskId, callbacks)
        }
      }
    }, 3000)
  }

  /**
   * 关闭所有连接
   */
  closeAllConnections() {
    for (const [taskId] of this.activeConnections) {
      this.closeConnection(taskId)
    }
  }

  /**
   * 获取活跃连接数
   */
  getActiveConnectionCount(): number {
    return this.activeConnections.size
  }

  /**
   * 获取活跃连接列表
   */
  getActiveConnections(): string[] {
    return Array.from(this.activeConnections.keys())
  }

  /**
   * 检查连接是否存在
   */
  hasConnection(taskId: string): boolean {
    return this.activeConnections.has(taskId)
  }

  /**
   * 获取连接状态
   */
  getConnectionState(taskId: string): number | null {
    const eventSource = this.activeConnections.get(taskId)
    return eventSource ? eventSource.readyState : null
  }
}

// 创建单例实例
const sseManagerInstance = new SSEManager()

// 导出 composable 函数
export function useSSEManager() {
  return sseManagerInstance
}

// 导出类型和实例
export { SSEManager }
export default sseManagerInstance
