<template>
  <div class="ai-chat-container h-full flex flex-col bg-gray-50 dark:bg-gray-900">
    <!-- Header -->
    <div class="chat-header bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 p-3">
      <div class="flex items-center justify-between min-w-0">
        <div class="flex items-center space-x-3 min-w-0 flex-shrink">
          <div class="w-8 h-8 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full flex items-center justify-center flex-shrink-0">
            <span class="text-white text-sm">🤖</span>
          </div>
          <div class="min-w-0">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white truncate">AI编程助手</h2>
            <!-- <p class="text-sm text-gray-500 dark:text-gray-400">AI助手将分析您的需求，制定执行计划，并逐步完成任务</p> -->
          </div>
        </div>
        <div class="flex items-center space-x-1 flex-shrink-0">
          <button
            @click="openToolConfig"
            class="w-7 h-7 flex items-center justify-center bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
            :disabled="isLoading"
            title="配置动态工具"
          >
            ⚙️
          </button>
          <button
            @click="clearHistory"
            class="w-7 h-7 flex items-center justify-center bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
            :disabled="isLoading"
            title="清除历史"
          >
            🗑️
          </button>
          <div class="flex items-center space-x-1 ml-1">
            <div
              :class="{
                'w-2 h-2 rounded-full': true,
                'bg-green-500': connectionStatus === 'connected',
                'bg-yellow-500': connectionStatus === 'connecting',
                'bg-red-500': connectionStatus === 'disconnected',
                'animate-pulse': connectionStatus === 'connecting'
              }"
            ></div>
            <span class="text-xs text-gray-500 whitespace-nowrap">
              {{ connectionStatusText }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Messages Area -->
    <div class="messages-area flex-1 overflow-y-auto p-4 space-y-4" ref="messagesContainer">
      <!-- Welcome Message -->
      <div v-if="messages.length === 0" class="message assistant">
        <div class="message-content bg-white dark:bg-gray-800 rounded-lg p-4 shadow-sm border border-gray-200 dark:border-gray-700">
          <div class="message-role text-xs font-semibold text-gray-500 dark:text-gray-400 mb-3">Assistant</div>
          <div class="text-gray-700 dark:text-gray-300 leading-relaxed">
            <div class="mb-4">
              👋 Hello! I'm your AI file operations assistant. I can help you:
            </div>

            <div class="space-y-2 mb-4">
              <div class="flex items-start space-x-2">
                <span>📁</span>
                <div>
                  <strong>Read files</strong> - View file contents with pagination support
                </div>
              </div>
              <div class="flex items-start space-x-2">
                <span>✏️</span>
                <div>
                  <strong>Write files</strong> - Create new files or overwrite existing ones
                </div>
              </div>
              <div class="flex items-start space-x-2">
                <span>🔧</span>
                <div>
                  <strong>Edit files</strong> - Make precise edits with diff preview
                </div>
              </div>
              <div class="flex items-start space-x-2">
                <span>📋</span>
                <div>
                  <strong>List directories</strong> - Browse directory structure
                </div>
              </div>
            </div>

            <div class="mb-4">
              Try asking me to create a simple project, read a file, or explore the workspace!
            </div>

            <div class="text-sm text-gray-500 dark:text-gray-400">
              <em>Workspace: /workspace</em>
            </div>
          </div>
        </div>
      </div>

      <!-- Chat Messages -->
      <ChatMessage
        v-for="(message, index) in messages"
        :key="index"
        :message="message"
      />

      <!-- Tool Log Display -->
      <ToolLogDisplay
        v-if="currentTaskId"
        :task-id="currentTaskId"
        :log-events="toolLogEvents"
        :connection-status="sseConnectionStatus"
      />

      <!-- Loading Indicator -->
      <div v-if="isLoading" class="loading-indicator flex items-center justify-center p-4">
        <div class="flex items-center space-x-2 text-gray-500 dark:text-gray-400">
          <div class="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-500"></div>
          <span>🤔 AI is thinking...</span>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-area bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 p-4">
      <ChatInput
        v-model="inputMessage"
        :is-loading="isLoading"
        @send="sendMessage"
        @quick-action="handleQuickAction"
        @abort="abortCurrentRequest"
      />
    </div>

    <!-- Status Bar -->
    <div v-if="statusMessage" class="status-bar px-4 py-2 text-sm" :class="statusClass">
      {{ statusMessage }}
    </div>

    <!-- Tool Config Modal -->
    <ToolConfig v-model:visible="showToolConfig" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'
import ToolLogDisplay from './ToolLogDisplay.vue'
import ToolConfig from './ToolConfig.vue'
import { useChatStore } from '../stores/chatStore'
import { useSSEManager } from '../services/sseManager'

// Props
interface Props {
  initialMessage?: string
  autoSend?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  initialMessage: '',
  autoSend: false
})

// Store
const chatStore = useChatStore()

// Reactive data
const inputMessage = ref('')
const messagesContainer = ref<HTMLElement>()
const currentTaskId = ref<string>()
const toolLogEvents = ref<any[]>([])
const sseConnectionStatus = ref<'disconnected' | 'connecting' | 'connected' | 'error'>('disconnected')
const showToolConfig = ref(false)
const currentAbortController = ref<AbortController | null>(null)
const pendingFileSelection = ref<string | null>(null) // 待选中的文件路径
const streamingFiles = ref<Map<string, string>>(new Map()) // 正在流式写入的文件内容

// Computed
const messages = computed(() => chatStore.messages)
const isLoading = computed(() => chatStore.isLoading)
const statusMessage = computed(() => chatStore.statusMessage)
const statusClass = computed(() => {
  const type = chatStore.statusType
  return {
    'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200': type === 'success',
    'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200': type === 'error',
    'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200': type === 'info',
  }
})

const connectionStatus = computed(() => {
  if (sseConnectionStatus.value === 'connected') return 'connected'
  if (sseConnectionStatus.value === 'connecting') return 'connecting'
  return 'disconnected'
})

const connectionStatusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connected': return '已连接'
    case 'connecting': return '连接中'
    default: return '未连接'
  }
})

// SSE Manager
const sseManager = useSSEManager()

// Methods
const sendMessage = async (message: string) => {
  if (!message.trim() || isLoading.value) return

  console.log('🚀 发送消息:', message)

  // 创建新的AbortController
  currentAbortController.value = new AbortController()

  try {
    chatStore.addMessage('user', message)
    chatStore.setLoading(true)

    console.log('📡 发送API请求到:', '/api/chat/message')

    const response = await fetch('/api/chat/message', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ message: message }),
      signal: currentAbortController.value.signal
    })

    console.log('📥 收到响应:', response.status, response.statusText)

    if (!response.ok) {
      const errorText = await response.text()
      console.error('❌ HTTP错误:', response.status, errorText)
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    const data = await response.json()
    console.log('📦 解析响应数据:', data)

    if (data.success) {
      if (data.taskId && data.asyncTask) {
        // 异步任务，建立SSE连接
        console.log('🔧 异步任务模式，taskId:', data.taskId)
        currentTaskId.value = data.taskId
        await startSSEConnection(data.taskId)
        chatStore.setStatus('任务已启动，正在建立实时连接...', 'success')
      } else if (data.streamResponse) {
        // 流式响应
        console.log('🌊 流式响应模式')
        await handleStreamResponse(message)
        chatStore.setStatus('开始流式对话...', 'success')
      } else {
        // 同步响应
        console.log('💬 同步响应模式')
        chatStore.addMessage('assistant', data.message)
        let statusMsg = 'Message sent successfully'
        if (data.totalTurns && data.totalTurns > 1) {
          statusMsg += ` (${data.totalTurns} turns`
          if (data.totalDurationMs) {
            statusMsg += `, ${(data.totalDurationMs / 1000).toFixed(1)}s`
          }
          statusMsg += ')'
          if (data.reachedMaxTurns) {
            statusMsg += ' - Reached max turns limit'
          }
          if (data.stopReason) {
            statusMsg += ` - ${data.stopReason}`
          }
        }
        chatStore.setStatus(statusMsg, 'success')
      }
    } else {
      console.error('❌ API返回失败:', data)
      chatStore.addMessage('assistant', data.message || 'Unknown error occurred')
      chatStore.setStatus('Error: ' + (data.message || 'Unknown error'), 'error')
    }
  } catch (error) {
    console.error('❌ 发送消息错误:', error)

    // 检查是否是用户主动中断
    if (error instanceof Error && error.name === 'AbortError') {
      console.log('🛑 用户中断了请求')
      chatStore.addMessage('assistant', '请求已被中断')
      chatStore.setStatus('请求已中断', 'info')
    } else {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred'
      chatStore.addMessage('assistant', 'Sorry, there was an error processing your request: ' + errorMessage)
      chatStore.setStatus('Network error: ' + errorMessage, 'error')
    }
  } finally {
    chatStore.setLoading(false)
    currentAbortController.value = null
    scrollToBottom()
  }
}

const handleQuickAction = (message: string) => {
  inputMessage.value = message
  sendMessage(message)
}

// 中断当前请求
const abortCurrentRequest = () => {
  if (currentAbortController.value) {
    console.log('🛑 中断当前请求')
    currentAbortController.value.abort()

    // 如果有SSE连接，也要关闭
    if (currentTaskId.value) {
      sseManager.closeConnection(currentTaskId.value)
      currentTaskId.value = undefined
    }
  }
}

const openToolConfig = () => {
  showToolConfig.value = true
}

const clearHistory = async () => {
  try {
    await fetch('/api/chat/clear', { method: 'POST' })
    chatStore.clearMessages()
    chatStore.setStatus('History cleared', 'success')
  } catch (error) {
    chatStore.setStatus('Error clearing history', 'error')
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const startSSEConnection = async (taskId: string) => {
  sseConnectionStatus.value = 'connecting'
  
  try {
    await sseManager.startLogStream(taskId, {
      onOpen: () => {
        sseConnectionStatus.value = 'connected'
        console.log('✅ SSE连接建立成功:', taskId)
      },
      onMessage: (event) => {
        toolLogEvents.value.push(event)
        handleSSEEvent(event)
      },
      onError: (error) => {
        sseConnectionStatus.value = 'error'
        console.error('❌ SSE连接错误:', error)
      },
      onClose: () => {
        sseConnectionStatus.value = 'disconnected'
        console.log('🔚 SSE连接关闭:', taskId)
      }
    })
  } catch (error) {
    sseConnectionStatus.value = 'error'
    console.error('启动SSE连接失败:', error)
  }
}

const handleSSEEvent = (event: any) => {
  switch (event.type) {
    case 'CONNECTION_ESTABLISHED':
      chatStore.setStatus('连接已建立，等待AI开始执行工具...', 'info')
      break
    case 'TASK_COMPLETE':
      handleTaskComplete(event.taskId)
      break
    case 'FILE_CREATED':
      handleFileCreated(event)
      break
    case 'FILE_CONTENT_CHUNK':
      handleFileContentChunk(event)
      break
    case 'FILE_WRITE_PROGRESS':
      handleFileWriteProgress(event)
      break
    case 'FILE_WRITE_COMPLETE':
      handleFileWriteComplete(event)
      break
    case 'FILE_WRITE_ERROR':
      handleFileWriteError(event)
      break
    default:
      // 其他事件由ToolLogDisplay组件处理
      break
  }
}

const handleTaskComplete = async (taskId: string) => {
  try {
    const response = await fetch(`/api/task/result/${taskId}`)
    const resultData = await response.json()

    if (resultData && resultData.fullResponse) {
      chatStore.addMessage('assistant', resultData.fullResponse)
    }

    // 清理任务状态
    currentTaskId.value = undefined
    toolLogEvents.value = []
    sseManager.closeConnection(taskId)
  } catch (error) {
    console.error('获取任务结果失败:', error)
  }
}

// 文件流式写入事件处理函数
const handleFileCreated = (event: any) => {
  console.log('📄 文件已创建:', event.filePath)
  const relativePath = getRelativeFilePath(event.filePath)
  chatStore.setStatus(`文件已创建: ${relativePath}`, 'success')

  // 记录待选中的文件路径
  pendingFileSelection.value = relativePath

  // 初始化流式文件内容
  streamingFiles.value.set(relativePath, '')

  // 触发文件树刷新，然后自动选中文件
  triggerFileSelectionAfterRefresh(relativePath)
}

const handleFileContentChunk = (event: any) => {
  console.log('📝 写入内容块:', event.chunkIndex, event.writtenBytes, '/', event.totalBytes)

  const relativePath = getRelativeFilePath(event.filePath)
  const currentContent = streamingFiles.value.get(relativePath) || ''
  const newContent = currentContent + event.contentChunk

  // 更新流式文件内容
  streamingFiles.value.set(relativePath, newContent)

  // 实时更新编辑器内容
  updateEditorContent(relativePath, newContent)
}

const handleFileWriteProgress = (event: any) => {
  console.log('📊 写入进度:', event.progressPercent + '%')
  const fileName = getRelativeFilePath(event.filePath)
  chatStore.setStatus(`写入进度: ${fileName} (${event.progressPercent.toFixed(1)}%)`, 'info')

  // 发送进度更新事件到编辑器
  window.dispatchEvent(new CustomEvent('file-write-progress', {
    detail: {
      filePath: fileName,
      progress: event.progressPercent,
      writtenBytes: event.writtenBytes,
      totalBytes: event.totalBytes
    }
  }))
}

const handleFileWriteComplete = (event: any) => {
  console.log('✅ 文件写入完成:', event.filePath)
  const fileName = getRelativeFilePath(event.filePath)
  chatStore.setStatus(`文件写入完成: ${fileName} (${event.totalBytes} bytes)`, 'success')

  // 清理流式文件状态
  streamingFiles.value.delete(fileName)

  // 最终更新编辑器内容（确保内容完整）
  finalizeEditorContent(fileName)
}

const handleFileWriteError = (event: any) => {
  console.error('❌ 文件写入错误:', event.filePath, event.message)
  const fileName = getRelativeFilePath(event.filePath)
  chatStore.setStatus(`文件写入失败: ${fileName} - ${event.message}`, 'error')
}

// 辅助函数：获取相对文件路径
const getRelativeFilePath = (fullPath: string): string => {
  if (!fullPath) return ''

  // 尝试提取相对于workspace的路径
  const workspaceIndex = fullPath.indexOf('workspace')
  if (workspaceIndex !== -1) {
    return fullPath.substring(workspaceIndex + 'workspace'.length + 1)
  }

  // 如果没有找到workspace，返回文件名
  const pathParts = fullPath.split(/[/\\]/)
  return pathParts[pathParts.length - 1]
}

// 触发文件选中（在文件树刷新后）
const triggerFileSelectionAfterRefresh = (filePath: string) => {
  // 延迟一段时间等待文件树刷新
  setTimeout(() => {
    // 发送自定义事件通知文件树选中文件
    window.dispatchEvent(new CustomEvent('auto-select-file', {
      detail: { filePath }
    }))

    // 清除待选中状态
    pendingFileSelection.value = null
  }, 1000) // 1秒延迟，确保文件树已刷新
}

// 实时更新编辑器内容
const updateEditorContent = (filePath: string, content: string) => {
  // 发送自定义事件通知编辑器更新内容
  window.dispatchEvent(new CustomEvent('update-editor-content', {
    detail: { filePath, content, isStreaming: true }
  }))
}

// 完成编辑器内容更新
const finalizeEditorContent = (filePath: string) => {
  // 发送自定义事件通知编辑器内容写入完成
  window.dispatchEvent(new CustomEvent('finalize-editor-content', {
    detail: { filePath }
  }))
}

const handleStreamResponse = async (userMessage: string) => {
  // TODO: 实现流式响应处理
  console.log('处理流式响应:', userMessage)
}

// Lifecycle
onMounted(() => {
  scrollToBottom()

  // 处理初始消息
  if (props.initialMessage) {
    inputMessage.value = props.initialMessage

    // 如果需要自动发送
    if (props.autoSend) {
      // 延迟一下确保组件完全加载
      setTimeout(() => {
        sendMessage(props.initialMessage)
      }, 500)
    }
  }
})

onUnmounted(() => {
  if (currentTaskId.value) {
    sseManager.closeConnection(currentTaskId.value)
  }
})
</script>

<style scoped>
.ai-chat-container {
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.message {
  display: flex;
  align-items: flex-start;
}

.message.user {
  justify-content: flex-end;
}

.message-content {
  max-width: 70%;
}

.loading-indicator {
  animation: fadeIn 0.3s ease-in-out;
}

.status-bar {
  transition: all 0.3s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
