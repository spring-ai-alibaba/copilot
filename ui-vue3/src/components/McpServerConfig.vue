<template>
  <div class="mcp-server-config space-y-6">
    <h3 class="text-lg font-medium text-gray-900 dark:text-white">添加MCP服务器</h3>

    <!-- JSON配置方式 -->
    <div class="config-method">
      <h4 class="text-md font-medium text-gray-800 dark:text-gray-200 mb-3">方式1: JSON配置</h4>
      <div class="space-y-3">
        <textarea
          v-model="jsonConfig"
          placeholder="请输入MCP服务器配置JSON"
          class="json-textarea"
          rows="8"
        ></textarea>
        <div class="text-sm text-gray-500 dark:text-gray-400">
          示例配置:
          <pre class="example-json">{{exampleJson}}</pre>
        </div>
        <button
          @click="addFromJson"
          :disabled="!jsonConfig.trim() || isLoading"
          class="submit-btn"
        >
          <div v-if="isLoading" class="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
          从JSON添加
        </button>
      </div>
    </div>

    <!-- 分隔线 -->
    <div class="divider">
      <div class="divider-line"></div>
      <span class="divider-text">或</span>
      <div class="divider-line"></div>
    </div>

    <!-- 表单配置方式 -->
    <div class="config-method">
      <h4 class="text-md font-medium text-gray-800 dark:text-gray-200 mb-3">方式2: 表单配置</h4>
      <form @submit.prevent="addFromForm" class="space-y-4">
        <div class="form-group">
          <label class="form-label">服务器名称 *</label>
          <input
            v-model="formData.serverName"
            type="text"
            required
            class="form-input"
            placeholder="例如: mcp-server-chart"
          >
        </div>

        <div class="form-group">
          <label class="form-label">命令 *</label>
          <input
            v-model="formData.command"
            type="text"
            required
            class="form-input"
            placeholder="例如: npx"
          >
        </div>

        <div class="form-group">
          <label class="form-label">参数 (每行一个)</label>
          <textarea
            v-model="formData.args"
            class="form-textarea"
            rows="3"
            placeholder="-y&#10;@antv/mcp-server-chart"
          ></textarea>
        </div>

        <div class="form-group">
          <label class="form-label">工作目录</label>
          <input
            v-model="formData.workDir"
            type="text"
            class="form-input"
            placeholder="留空使用当前目录"
          >
        </div>

        <button
          type="submit"
          :disabled="!isFormValid || isLoading"
          class="submit-btn"
        >
          <div v-if="isLoading" class="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
          添加MCP服务器
        </button>
      </form>
    </div>

    <!-- 状态消息 -->
    <div v-if="statusMessage" class="status-message" :class="statusClass">
      {{ statusMessage }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useToolConfigStore } from '../stores/toolConfigStore'

interface Emits {
  (e: 'server-added'): void
}

const emit = defineEmits<Emits>()

// Store
const toolConfigStore = useToolConfigStore()

// Reactive data
const jsonConfig = ref('')
const isLoading = ref(false)
const statusMessage = ref('')
const statusType = ref<'success' | 'error' | 'info'>('info')

const formData = reactive({
  serverName: '',
  command: 'npx',
  args: '',
  workDir: ''
})

// Computed
const isFormValid = computed(() => {
  return formData.serverName.trim() && formData.command.trim()
})

const statusClass = computed(() => {
  const baseClass = 'px-4 py-2 rounded-md text-sm'
  switch (statusType.value) {
    case 'success':
      return `${baseClass} bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200 border border-green-200 dark:border-green-800`
    case 'error':
      return `${baseClass} bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200 border border-red-200 dark:border-red-800`
    default:
      return `${baseClass} bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 border border-blue-200 dark:border-blue-800`
  }
})

const exampleJson = `{
  "mcpServers": {
    "mcp-server-chart": {
      "command": "npx",
      "args": ["-y", "@antv/mcp-server-chart"]
    }
  }
}`

// Methods
const addFromJson = async () => {
  if (!jsonConfig.value.trim()) {
    showStatus('请输入JSON配置', 'error')
    return
  }

  isLoading.value = true
  try {
    const config = JSON.parse(jsonConfig.value)
    
    if (!config.mcpServers) {
      throw new Error('JSON配置中缺少 mcpServers 节点')
    }
    
    // 遍历mcpServers配置
    for (const [serverName, serverConfig] of Object.entries(config.mcpServers)) {
      await toolConfigStore.addMcpServer(serverName, serverConfig as any)
    }
    
    showStatus('MCP服务器配置添加成功', 'success')
    jsonConfig.value = ''
    emit('server-added')
    
  } catch (error) {
    console.error('添加MCP服务器失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    showStatus('添加MCP服务器失败: ' + errorMessage, 'error')
  } finally {
    isLoading.value = false
  }
}

const addFromForm = async () => {
  if (!isFormValid.value) {
    showStatus('请填写必填字段', 'error')
    return
  }

  isLoading.value = true
  try {
    const serverConfig: any = {
      command: formData.command.trim()
    }

    // 解析参数
    if (formData.args.trim()) {
      serverConfig.args = formData.args
        .split('\n')
        .map(arg => arg.trim())
        .filter(arg => arg.length > 0)
    }

    // 工作目录
    if (formData.workDir.trim()) {
      serverConfig.workingDirectory = formData.workDir.trim()
    }

    await toolConfigStore.addMcpServer(formData.serverName.trim(), serverConfig)
    
    showStatus('MCP服务器添加成功', 'success')
    resetForm()
    emit('server-added')
    
  } catch (error) {
    console.error('添加MCP服务器失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    showStatus('添加MCP服务器失败: ' + errorMessage, 'error')
  } finally {
    isLoading.value = false
  }
}

const resetForm = () => {
  formData.serverName = ''
  formData.command = 'npx'
  formData.args = ''
  formData.workDir = ''
}

const showStatus = (message: string, type: 'success' | 'error' | 'info') => {
  statusMessage.value = message
  statusType.value = type
  
  // 自动清除状态消息
  setTimeout(() => {
    statusMessage.value = ''
  }, 5000)
}
</script>

<style scoped>
.config-method {
  @apply p-4 border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800;
}

.json-textarea {
  @apply w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 0.875rem;
}

.example-json {
  @apply mt-2 p-3 bg-gray-100 dark:bg-gray-700 rounded text-xs overflow-x-auto;
  font-family: 'Monaco', 'Consolas', monospace;
}

.divider {
  @apply flex items-center;
}

.divider-line {
  @apply flex-1 h-px bg-gray-300 dark:bg-gray-600;
}

.divider-text {
  @apply px-3 text-sm text-gray-500 dark:text-gray-400;
}

.form-group {
  @apply space-y-1;
}

.form-label {
  @apply block text-sm font-medium text-gray-700 dark:text-gray-300;
}

.form-input {
  @apply w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent;
}

.form-textarea {
  @apply w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none;
}

.submit-btn {
  @apply flex items-center justify-center px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-medium rounded-md transition-colors disabled:cursor-not-allowed;
}

.status-message {
  animation: slideInDown 0.3s ease-out;
}

@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
