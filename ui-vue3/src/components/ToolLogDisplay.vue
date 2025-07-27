<template>
  <div class="tool-log-container bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
    <!-- Header -->
    <div class="tool-log-header bg-gray-50 dark:bg-gray-700 px-4 py-3 border-b border-gray-200 dark:border-gray-600">
      <div class="flex items-center justify-between">
        <div class="flex items-center space-x-2">
          <span class="text-lg">🔧</span>
          <span class="font-medium text-gray-900 dark:text-white">工具执行日志</span>
        </div>
        <div class="connection-status" :class="connectionStatusClass">
          <div class="flex items-center space-x-1">
            <div
              :class="{
                'w-2 h-2 rounded-full': true,
                'bg-green-500': connectionStatus === 'connected',
                'bg-yellow-500': connectionStatus === 'connecting',
                'bg-red-500': connectionStatus === 'error' || connectionStatus === 'disconnected',
                'animate-pulse': connectionStatus === 'connecting'
              }"
            ></div>
            <span class="text-sm">{{ connectionStatusText }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Content -->
    <div class="tool-log-content p-4 space-y-3 max-h-96 overflow-y-auto">
      <!-- Waiting Card -->
      <div v-if="showWaitingCard" class="waiting-tool-card">
        <div class="tool-card bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
          <div class="flex items-center space-x-3">
            <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500"></div>
            <div>
              <div class="font-medium text-blue-900 dark:text-blue-100">等待AI开始执行工具...</div>
              <div class="text-sm text-blue-700 dark:text-blue-300 mt-1">连接已建立，正在等待任务分配</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tool Cards -->
      <div
        v-for="(tool, index) in processedTools"
        :key="`${tool.toolName}-${index}`"
        class="tool-card"
        :class="getToolCardClass(tool.status)"
      >
        <div class="tool-header flex items-center justify-between mb-2">
          <div class="flex items-center space-x-2">
            <span class="text-lg">{{ tool.icon || '🔧' }}</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ tool.toolName }}</span>
            <span class="tool-status-badge" :class="getStatusBadgeClass(tool.status)">
              {{ getStatusText(tool.status) }}
            </span>
          </div>
          <div v-if="tool.executionTime" class="text-xs text-gray-500 dark:text-gray-400">
            {{ tool.executionTime }}ms
          </div>
        </div>

        <div v-if="tool.filePath" class="tool-file flex items-center space-x-1 mb-2">
          <span class="text-sm">📁</span>
          <code class="text-sm bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">
            {{ getFileName(tool.filePath) }}
          </code>
        </div>

        <div class="tool-message text-sm text-gray-700 dark:text-gray-300 mb-2">
          {{ tool.message }}
        </div>

        <div class="tool-time text-xs text-gray-500 dark:text-gray-400">
          {{ tool.status === 'RUNNING' ? '开始时间' : '完成时间' }}: {{ tool.timestamp }}
        </div>

        <div v-if="tool.summary" class="tool-summary mt-2 p-2 bg-gray-50 dark:bg-gray-700 rounded text-sm">
          {{ tool.summary }}
        </div>
      </div>

      <!-- Task Complete -->
      <div v-if="isTaskComplete" class="task-complete-card">
        <div class="tool-card bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
          <div class="flex items-center space-x-3">
            <span class="text-lg">✅</span>
            <div>
              <div class="font-medium text-green-900 dark:text-green-100">任务执行完成</div>
              <div class="text-sm text-green-700 dark:text-green-300 mt-1">所有工具已成功执行</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SSEEvent } from '../services/sseManager'

interface Props {
  taskId: string
  logEvents: SSEEvent[]
  connectionStatus: 'disconnected' | 'connecting' | 'connected' | 'error'
}

const props = defineProps<Props>()

// Reactive data
const isTaskComplete = ref(false)

// Computed
const connectionStatusText = computed(() => {
  switch (props.connectionStatus) {
    case 'connected': return '已连接'
    case 'connecting': return '连接中'
    case 'error': return '连接错误'
    default: return '未连接'
  }
})

const connectionStatusClass = computed(() => {
  switch (props.connectionStatus) {
    case 'connected': return 'text-green-600 dark:text-green-400'
    case 'connecting': return 'text-yellow-600 dark:text-yellow-400'
    case 'error': return 'text-red-600 dark:text-red-400'
    default: return 'text-gray-600 dark:text-gray-400'
  }
})

const showWaitingCard = computed(() => {
  return props.connectionStatus === 'connected' && processedTools.value.length === 0 && !isTaskComplete.value
})

const processedTools = computed(() => {
  const toolMap = new Map<string, SSEEvent & { status: string }>()
  
  props.logEvents.forEach(event => {
    if (event.toolName) {
      const existing = toolMap.get(event.toolName)
      
      if (event.type === 'TOOL_START') {
        toolMap.set(event.toolName, { ...event, status: 'RUNNING' })
      } else if (event.type === 'TOOL_SUCCESS') {
        toolMap.set(event.toolName, { 
          ...existing, 
          ...event, 
          status: 'SUCCESS' 
        })
      } else if (event.type === 'TOOL_ERROR') {
        toolMap.set(event.toolName, { 
          ...existing, 
          ...event, 
          status: 'ERROR' 
        })
      }
    }
  })
  
  return Array.from(toolMap.values())
})

// Methods
const getToolCardClass = (status: string) => {
  const baseClass = 'rounded-lg p-4 border transition-all duration-300'
  
  switch (status) {
    case 'RUNNING':
      return `${baseClass} bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800`
    case 'SUCCESS':
      return `${baseClass} bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800`
    case 'ERROR':
      return `${baseClass} bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800`
    default:
      return `${baseClass} bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600`
  }
}

const getStatusBadgeClass = (status: string) => {
  const baseClass = 'px-2 py-1 text-xs font-medium rounded-full'
  
  switch (status) {
    case 'RUNNING':
      return `${baseClass} bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200`
    case 'SUCCESS':
      return `${baseClass} bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200`
    case 'ERROR':
      return `${baseClass} bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200`
    default:
      return `${baseClass} bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200`
  }
}

const getStatusText = (status: string) => {
  switch (status) {
    case 'RUNNING': return '⏳ 执行中'
    case 'SUCCESS': return '✅ 成功'
    case 'ERROR': return '❌ 失败'
    default: return '⏸️ 等待'
  }
}

const getFileName = (filePath: string) => {
  if (!filePath) return ''
  const parts = filePath.split('/')
  return parts[parts.length - 1] || filePath
}

// Watch for task completion
watch(() => props.logEvents, (events) => {
  const hasTaskComplete = events.some(event => event.type === 'TASK_COMPLETE')
  if (hasTaskComplete) {
    isTaskComplete.value = true
  }
}, { deep: true })
</script>

<style scoped>
.tool-log-content::-webkit-scrollbar {
  width: 4px;
}

.tool-log-content::-webkit-scrollbar-track {
  background: transparent;
}

.tool-log-content::-webkit-scrollbar-thumb {
  background: #cbd5e0;
  border-radius: 2px;
}

.dark .tool-log-content::-webkit-scrollbar-thumb {
  background: #4a5568;
}

.tool-card {
  animation: slideInUp 0.3s ease-out;
}

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
