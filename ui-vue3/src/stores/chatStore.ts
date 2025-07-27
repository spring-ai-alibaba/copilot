import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  id?: string
}

export type StatusType = 'success' | 'error' | 'info' | 'warning'

export const useChatStore = defineStore('chat', () => {
  // State
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const statusMessage = ref('')
  const statusType = ref<StatusType>('info')
  const statusTimeout = ref<NodeJS.Timeout>()

  // Getters
  const messageCount = computed(() => messages.value.length)
  const lastMessage = computed(() => messages.value[messages.value.length - 1])
  const hasMessages = computed(() => messages.value.length > 0)

  // Actions
  const addMessage = (role: 'user' | 'assistant', content: string) => {
    const message: ChatMessage = {
      role,
      content,
      timestamp: new Date(),
      id: generateMessageId()
    }
    messages.value.push(message)
  }

  const updateLastMessage = (content: string) => {
    if (messages.value.length > 0) {
      const lastMsg = messages.value[messages.value.length - 1]
      lastMsg.content = content
      lastMsg.timestamp = new Date()
    }
  }

  const clearMessages = () => {
    messages.value = []
    clearStatus()
  }

  const removeMessage = (id: string) => {
    const index = messages.value.findIndex(msg => msg.id === id)
    if (index > -1) {
      messages.value.splice(index, 1)
    }
  }

  const setLoading = (loading: boolean) => {
    isLoading.value = loading
  }

  const setStatus = (message: string, type: StatusType = 'info', duration = 5000) => {
    statusMessage.value = message
    statusType.value = type
    
    // 清除之前的定时器
    if (statusTimeout.value) {
      clearTimeout(statusTimeout.value)
    }
    
    // 设置新的定时器自动清除状态
    if (duration > 0) {
      statusTimeout.value = setTimeout(() => {
        clearStatus()
      }, duration)
    }
  }

  const clearStatus = () => {
    statusMessage.value = ''
    if (statusTimeout.value) {
      clearTimeout(statusTimeout.value)
      statusTimeout.value = undefined
    }
  }

  const generateMessageId = (): string => {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }

  // 流式消息处理
  const startStreamMessage = (role: 'user' | 'assistant', initialContent = '') => {
    const message: ChatMessage = {
      role,
      content: initialContent,
      timestamp: new Date(),
      id: generateMessageId()
    }
    messages.value.push(message)
    return message.id
  }

  const appendToStreamMessage = (id: string, content: string) => {
    const message = messages.value.find(msg => msg.id === id)
    if (message) {
      message.content += content
      message.timestamp = new Date()
    }
  }

  const finishStreamMessage = (id: string) => {
    const message = messages.value.find(msg => msg.id === id)
    if (message) {
      message.timestamp = new Date()
    }
  }

  // 消息搜索
  const searchMessages = (query: string): ChatMessage[] => {
    if (!query.trim()) return []
    
    const lowercaseQuery = query.toLowerCase()
    return messages.value.filter(message =>
      message.content.toLowerCase().includes(lowercaseQuery)
    )
  }

  // 导出消息
  const exportMessages = (): string => {
    const exportData = {
      messages: messages.value,
      exportTime: new Date().toISOString(),
      version: '1.0'
    }
    return JSON.stringify(exportData, null, 2)
  }

  // 导入消息
  const importMessages = (jsonData: string): boolean => {
    try {
      const data = JSON.parse(jsonData)
      if (data.messages && Array.isArray(data.messages)) {
        messages.value = data.messages.map((msg: any) => ({
          ...msg,
          timestamp: new Date(msg.timestamp),
          id: msg.id || generateMessageId()
        }))
        return true
      }
      return false
    } catch (error) {
      console.error('导入消息失败:', error)
      return false
    }
  }

  // 获取消息统计
  const getMessageStats = () => {
    const userMessages = messages.value.filter(msg => msg.role === 'user')
    const assistantMessages = messages.value.filter(msg => msg.role === 'assistant')
    
    return {
      total: messages.value.length,
      userMessages: userMessages.length,
      assistantMessages: assistantMessages.length,
      totalCharacters: messages.value.reduce((sum, msg) => sum + msg.content.length, 0),
      averageMessageLength: messages.value.length > 0 
        ? Math.round(messages.value.reduce((sum, msg) => sum + msg.content.length, 0) / messages.value.length)
        : 0
    }
  }

  return {
    // State
    messages,
    isLoading,
    statusMessage,
    statusType,
    
    // Getters
    messageCount,
    lastMessage,
    hasMessages,
    
    // Actions
    addMessage,
    updateLastMessage,
    clearMessages,
    removeMessage,
    setLoading,
    setStatus,
    clearStatus,
    startStreamMessage,
    appendToStreamMessage,
    finishStreamMessage,
    searchMessages,
    exportMessages,
    importMessages,
    getMessageStats
  }
})

export type ChatStore = ReturnType<typeof useChatStore>
