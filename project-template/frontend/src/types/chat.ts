/**
 * 聊天相关的TypeScript类型定义
 */

// 消息类型枚举
export enum MessageType {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT',
  SYSTEM = 'SYSTEM'
}

// 响应状态枚举
export enum ResponseStatus {
  SUCCESS = 'SUCCESS',
  PROCESSING = 'PROCESSING',
  ERROR = 'ERROR',
  TIMEOUT = 'TIMEOUT'
}

// 聊天消息接口
export interface ChatMessage {
  id?: string
  message: string
  conversationId?: string
  type: MessageType
  timestamp: string
}

// 聊天响应接口
export interface ChatResponse {
  conversationId: string
  message?: string
  isComplete: boolean
  status: ResponseStatus
  errorMessage?: string
  timestamp: string
}

// 发送消息请求接口
export interface SendMessageRequest {
  message: string
  conversationId?: string
}

// 发送消息响应接口
export interface SendMessageResponse {
  status: string
  conversationId: string
  message: string
  timestamp: number
}

// 会话信息接口
export interface Conversation {
  id: string
  title?: string
  createdAt: string
  updatedAt: string
  messageCount: number
}

// 显示用的消息接口（前端使用）
export interface DisplayMessage {
  id: string
  content: string
  type: MessageType
  timestamp: Date
  isStreaming?: boolean
  error?: string
}

// API响应基础接口
export interface ApiResponse<T = any> {
  status: 'success' | 'error'
  message?: string
  data?: T
  timestamp?: number
}

// 健康检查响应接口
export interface HealthResponse {
  status: string
  message: string
  timestamp: number
  connections: number
}
