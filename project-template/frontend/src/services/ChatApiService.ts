import type {ChatResponse, HealthResponse, SendMessageRequest, SendMessageResponse} from '@/types/chat'

/**
 * 聊天API服务类
 * 处理与后端的所有聊天相关通信
 */
export class ChatApiService {
  private static readonly BASE_URL = '/api/chat'

  /**
   * 健康检查
   */
  static async healthCheck(): Promise<HealthResponse> {
    const response = await fetch(`${this.BASE_URL}/health`)
    if (!response.ok) {
      throw new Error(`健康检查失败: ${response.status}`)
    }
    return await response.json()
  }

  /**
   * 发送消息（同步方式）
   */
  static async sendMessage(request: SendMessageRequest): Promise<ChatResponse> {
    const response = await fetch(`${this.BASE_URL}/send`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`发送消息失败: ${response.status}`)
    }

    return await response.json()
  }

  /**
   * 发送消息（异步流式方式）
   */
  static async sendMessageStream(request: SendMessageRequest): Promise<SendMessageResponse> {
    const response = await fetch(`${this.BASE_URL}/send-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`发送流式消息失败: ${response.status}`)
    }

    return await response.json()
  }

  /**
   * 创建新会话
   */
  static async createConversation(): Promise<{ conversationId: string; timestamp: number }> {
    const response = await fetch(`${this.BASE_URL}/conversation/new`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`创建会话失败: ${response.status}`)
    }

    return await response.json()
  }

  /**
   * 创建SSE连接
   */
  static createSseConnection(conversationId: string): EventSource {
    const url = `${this.BASE_URL}/stream/${conversationId}`
    return new EventSource(url)
  }
}

/**
 * SSE连接管理器
 */
export class SseConnectionManager {
  private eventSource: EventSource | null = null
  private conversationId: string | null = null

  /**
   * 连接到SSE流
   */
  connect(
    conversationId: string,
    onMessage: (data: ChatResponse) => void,
    onError: (error: Event) => void,
    onConnected?: () => void
  ): void {
    this.disconnect() // 先断开现有连接

    this.conversationId = conversationId
    this.eventSource = ChatApiService.createSseConnection(conversationId)

    // 连接建立
    this.eventSource.addEventListener('connected', (event) => {
      console.log('SSE连接已建立:', event.data)
      onConnected?.()
    })

    // 接收消息
    this.eventSource.addEventListener('message', (event) => {
      try {
        const data: ChatResponse = JSON.parse(event.data)
        onMessage(data)
      } catch (error) {
        console.error('解析SSE消息失败:', error)
        onError(new Event('parse-error'))
      }
    })

    // 连接错误
    this.eventSource.addEventListener('error', (event) => {
      console.error('SSE连接错误:', event)
      onError(event)
    })

    // 连接关闭
    this.eventSource.addEventListener('close', () => {
      console.log('SSE连接已关闭')
      this.eventSource = null
    })
  }

  /**
   * 断开SSE连接
   */
  disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
      this.conversationId = null
    }
  }

  /**
   * 获取连接状态
   */
  isConnected(): boolean {
    return this.eventSource !== null && this.eventSource.readyState === EventSource.OPEN
  }

  /**
   * 获取当前会话ID
   */
  getCurrentConversationId(): string | null {
    return this.conversationId
  }
}
