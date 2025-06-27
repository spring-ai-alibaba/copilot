<template>
  <div class="chat-component">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <div class="header-content">
        <div class="title-section">
          <h2>
            <RobotOutlined />
            AI助手
          </h2>
          <div class="status-indicator" :class="{ connected: isConnected }">
            <span class="status-dot"></span>
            {{ isConnected ? '已连接' : '未连接' }}
          </div>
        </div>
        <div class="header-actions">
          <a-button @click="handleNewConversation" :loading="isCreatingConversation">
            <template #icon>
              <PlusOutlined />
            </template>
            新对话
          </a-button>
        </div>
      </div>
    </div>

    <!-- 消息列表 -->
    <MessageList
      ref="messageListRef"
      :messages="messages"
      :auto-scroll="true"
    />

    <!-- 消息输入 -->
    <MessageInput
      ref="messageInputRef"
      :loading="isSending"
      :disabled="!isConnected"
      placeholder="请输入您的消息..."
      @send="handleSendMessage"
    />

    <!-- 连接状态提示 -->
    <div v-if="showConnectionTip" class="connection-tip">
      <a-alert
        :message="connectionTipMessage"
        :type="connectionTipType"
        show-icon
        closable
        @close="showConnectionTip = false"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {message as antMessage} from 'ant-design-vue'
import {PlusOutlined, RobotOutlined} from '@ant-design/icons-vue'

import MessageList from './MessageList.vue'
import MessageInput from './MessageInput.vue'
import {ChatApiService, SseConnectionManager} from '@/services/ChatApiService'
import type {ChatResponse, DisplayMessage} from '@/types/chat'
import {MessageType, ResponseStatus} from '@/types/chat'

// 响应式数据
const messages = ref<DisplayMessage[]>([])
const isConnected = ref(false)
const isSending = ref(false)
const isCreatingConversation = ref(false)
const currentConversationId = ref<string>('')
const showConnectionTip = ref(false)
const connectionTipMessage = ref('')
const connectionTipType = ref<'success' | 'info' | 'warning' | 'error'>('info')

// 组件引用
const messageListRef = ref<InstanceType<typeof MessageList>>()
const messageInputRef = ref<InstanceType<typeof MessageInput>>()

// SSE连接管理器
const sseManager = new SseConnectionManager()

// 生成消息ID
const generateMessageId = (): string => {
  return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

// 添加消息到列表
const addMessage = (content: string, type: MessageType, options?: Partial<DisplayMessage>): DisplayMessage => {
  const message: DisplayMessage = {
    id: generateMessageId(),
    content,
    type,
    timestamp: new Date(),
    ...options
  }
  messages.value.push(message)
  return message
}

// 更新最后一条消息
const updateLastMessage = (updates: Partial<DisplayMessage>): void => {
  const lastMessage = messages.value[messages.value.length - 1]
  if (lastMessage) {
    Object.assign(lastMessage, updates)
  }
}

// 创建新对话
const handleNewConversation = async (): Promise<void> => {
  try {
    isCreatingConversation.value = true

    // 断开现有连接
    sseManager.disconnect()

    // 创建新会话
    const response = await ChatApiService.createConversation()
    currentConversationId.value = response.conversationId

    // 清空消息列表
    messages.value = []

    // 建立新的SSE连接
    await connectToSse(currentConversationId.value)

    // 添加欢迎消息
    addMessage('您好！我是AI助手，有什么可以帮助您的吗？', MessageType.ASSISTANT)

    antMessage.success('新对话已创建')

  } catch (error) {
    console.error('创建新对话失败:', error)
    antMessage.error('创建新对话失败，请重试')
  } finally {
    isCreatingConversation.value = false
  }
}

// 连接到SSE
const connectToSse = async (conversationId: string): Promise<void> => {
  return new Promise((resolve, reject) => {
    sseManager.connect(
      conversationId,
      handleSseMessage,
      handleSseError,
      () => {
        isConnected.value = true
        showConnectionTip.value = true
        connectionTipMessage.value = 'SSE连接已建立'
        connectionTipType.value = 'success'
        resolve()
      }
    )

    // 设置连接超时
    setTimeout(() => {
      if (!isConnected.value) {
        reject(new Error('SSE连接超时'))
      }
    }, 10000)
  })
}

// 处理SSE消息
const handleSseMessage = (data: ChatResponse): void => {
  console.log('收到SSE消息:', data)

  if (data.status === ResponseStatus.ERROR) {
    // 处理错误
    updateLastMessage({
      error: data.errorMessage || '处理消息时发生错误',
      isStreaming: false
    })
    isSending.value = false
    return
  }

  if (data.message) {
    const lastMessage = messages.value[messages.value.length - 1]

    if (lastMessage && lastMessage.type === MessageType.ASSISTANT && lastMessage.isStreaming) {
      // 更新流式消息
      lastMessage.content += data.message
      if (data.isComplete) {
        lastMessage.isStreaming = false
        isSending.value = false
      }
    } else {
      // 创建新的助手消息
      addMessage(data.message, MessageType.ASSISTANT, {
        isStreaming: !data.isComplete
      })
      if (data.isComplete) {
        isSending.value = false
      }
    }
  }

  if (data.isComplete) {
    isSending.value = false
  }
}

// 处理SSE错误
const handleSseError = (error: Event): void => {
  console.error('SSE连接错误:', error)
  isConnected.value = false
  isSending.value = false

  showConnectionTip.value = true
  connectionTipMessage.value = 'SSE连接已断开，请刷新页面重试'
  connectionTipType.value = 'error'
}

// 发送消息
const handleSendMessage = async (content: string): Promise<void> => {
  if (!currentConversationId.value || !isConnected.value) {
    antMessage.warning('请先创建对话')
    return
  }

  try {
    isSending.value = true

    // 添加用户消息
    addMessage(content, MessageType.USER)

    // 添加AI助手的占位消息
    addMessage('', MessageType.ASSISTANT, { isStreaming: true })

    // 发送流式消息请求
    await ChatApiService.sendMessageStream({
      message: content,
      conversationId: currentConversationId.value
    })

  } catch (error) {
    console.error('发送消息失败:', error)
    isSending.value = false

    // 更新最后一条消息为错误状态
    updateLastMessage({
      content: '发送消息失败，请重试',
      error: error instanceof Error ? error.message : '未知错误',
      isStreaming: false
    })

    antMessage.error('发送消息失败，请重试')
  }
}

// 初始化
const initialize = async (): Promise<void> => {
  try {
    // 检查后端健康状态
    await ChatApiService.healthCheck()

    // 创建初始对话
    await handleNewConversation()

  } catch (error) {
    console.error('初始化失败:', error)
    showConnectionTip.value = true
    connectionTipMessage.value = '无法连接到后端服务，请检查服务是否正常运行'
    connectionTipType.value = 'error'
  }
}

// 生命周期
onMounted(() => {
  initialize()
})

onUnmounted(() => {
  sseManager.disconnect()
})
</script>

<style scoped>
.chat-component {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #fff;
}

.chat-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 24px;
  flex-shrink: 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 800px;
  margin: 0 auto;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-section h2 {
  margin: 0;
  color: #262626;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  transition: background-color 0.3s;
}

.status-indicator.connected .status-dot {
  background: #52c41a;
}

.connection-tip {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  max-width: 400px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-header {
    padding: 12px 16px;
  }

  .header-content {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .title-section {
    justify-content: center;
  }

  .header-actions {
    display: flex;
    justify-content: center;
  }
}
</style>
