<template>
  <div class="preview-panel h-full flex flex-col bg-white dark:bg-gray-900">
    <!-- 预览工具栏 -->
    <div class="preview-toolbar flex items-center justify-between px-4 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center space-x-2">
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          预览
        </span>
        <div v-if="url" class="flex items-center space-x-1">
          <div class="w-2 h-2 bg-green-500 rounded-full"></div>
          <span class="text-xs text-gray-500 dark:text-gray-400">运行中</span>
        </div>
        <div v-else-if="loading" class="flex items-center space-x-1">
          <div class="w-2 h-2 bg-yellow-500 rounded-full animate-pulse"></div>
          <span class="text-xs text-gray-500 dark:text-gray-400">启动中</span>
        </div>
        <div v-else class="flex items-center space-x-1">
          <div class="w-2 h-2 bg-gray-400 rounded-full"></div>
          <span class="text-xs text-gray-500 dark:text-gray-400">未运行</span>
        </div>
      </div>
      
      <div class="flex items-center space-x-2">
        <!-- 地址栏 -->
        <div v-if="url" class="flex items-center space-x-2">
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ displayUrl }}
          </span>
          <a-button size="small" @click="copyUrl" title="复制链接">
            <template #icon>
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"/>
                <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z"/>
              </svg>
            </template>
          </a-button>
        </div>
        
        <!-- 控制按钮 -->
        <a-button size="small" @click="refreshPreview" :disabled="!url" title="刷新">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd"/>
            </svg>
          </template>
        </a-button>
        
        <a-button size="small" @click="openInNewTab" :disabled="!url" title="在新标签页打开">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path d="M11 3a1 1 0 100 2h2.586l-6.293 6.293a1 1 0 101.414 1.414L15 6.414V9a1 1 0 102 0V4a1 1 0 00-1-1h-5z"/>
              <path d="M5 5a2 2 0 00-2 2v8a2 2 0 002 2h8a2 2 0 002-2v-3a1 1 0 10-2 0v3H5V7h3a1 1 0 000-2H5z"/>
            </svg>
          </template>
        </a-button>

        <!-- 缩放控制 -->
        <div class="flex items-center space-x-1 border-l border-gray-300 dark:border-gray-600 pl-2">
          <a-button size="small" @click="zoomOut" :disabled="scale <= 0.5" title="缩小">
            <template #icon>
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M5 10a1 1 0 011-1h8a1 1 0 110 2H6a1 1 0 01-1-1z" clip-rule="evenodd"/>
              </svg>
            </template>
          </a-button>
          
          <span class="text-xs text-gray-500 dark:text-gray-400 min-w-12 text-center">
            {{ Math.round(scale * 100) }}%
          </span>
          
          <a-button size="small" @click="zoomIn" :disabled="scale >= 2" title="放大">
            <template #icon>
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z" clip-rule="evenodd"/>
              </svg>
            </template>
          </a-button>
          
          <a-button size="small" @click="resetZoom" title="重置缩放">
            <template #icon>
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1z" clip-rule="evenodd"/>
              </svg>
            </template>
          </a-button>
        </div>
      </div>
    </div>

    <!-- 预览内容 -->
    <div class="preview-content flex-1 relative overflow-hidden">
      <!-- 加载状态 -->
      <div
        v-if="loading"
        class="absolute inset-0 flex flex-col items-center justify-center bg-gray-50 dark:bg-gray-800"
      >
        <a-spin size="large" />
        <p class="mt-4 text-gray-500 dark:text-gray-400">正在启动开发服务器...</p>
      </div>

      <!-- 错误状态 -->
      <div
        v-else-if="error"
        class="absolute inset-0 flex flex-col items-center justify-center bg-gray-50 dark:bg-gray-800"
      >
        <svg class="w-16 h-16 text-red-500 mb-4" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
        </svg>
        <p class="text-red-600 dark:text-red-400 text-center">
          {{ error }}
        </p>
        <a-button type="primary" @click="retryLoad" class="mt-4">
          重试
        </a-button>
      </div>

      <!-- 空状态 -->
      <div
        v-else-if="!url"
        class="absolute inset-0 flex flex-col items-center justify-center bg-gray-50 dark:bg-gray-800"
      >
        <svg class="w-16 h-16 text-gray-400 mb-4" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" clip-rule="evenodd"/>
        </svg>
        <p class="text-gray-500 dark:text-gray-400 text-center">
          点击"运行项目"开始预览
        </p>
      </div>

      <!-- iframe 预览 -->
      <div
        v-else
        class="iframe-container h-full w-full"
        :style="{ transform: `scale(${scale})`, transformOrigin: 'top left' }"
      >
        <iframe
          ref="iframeRef"
          :src="url"
          class="w-full h-full border-none bg-white"
          :style="{ 
            width: `${100 / scale}%`, 
            height: `${100 / scale}%` 
          }"
          sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
          allow="cross-origin-isolated"
          @load="handleIframeLoad"
          @error="handleIframeError"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'

interface Props {
  url?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  url: '',
  loading: false
})

// 状态
const iframeRef = ref<HTMLIFrameElement>()
const scale = ref(1)
const error = ref('')

// 计算属性
const displayUrl = computed(() => {
  if (!props.url) return ''
  try {
    const urlObj = new URL(props.url)
    return `${urlObj.protocol}//${urlObj.host}`
  } catch {
    return props.url
  }
})

// 方法
const refreshPreview = () => {
  if (iframeRef.value) {
    iframeRef.value.src = iframeRef.value.src
  }
}

const openInNewTab = () => {
  if (props.url) {
    window.open(props.url, '_blank')
  }
}

const copyUrl = async () => {
  if (props.url) {
    try {
      await navigator.clipboard.writeText(props.url)
      message.success('链接已复制到剪贴板')
    } catch {
      message.error('复制失败')
    }
  }
}

const zoomIn = () => {
  scale.value = Math.min(scale.value * 1.1, 2)
}

const zoomOut = () => {
  scale.value = Math.max(scale.value * 0.9, 0.5)
}

const resetZoom = () => {
  scale.value = 1
}

const handleIframeLoad = () => {
  error.value = ''
}

const handleIframeError = () => {
  error.value = '预览加载失败'
}

const retryLoad = () => {
  error.value = ''
  refreshPreview()
}
</script>

<style scoped>
.preview-panel {
  background: #f8f9fa;
}

.iframe-container {
  overflow: hidden;
}

.preview-content {
  background: #f8f9fa;
}

/* 深色模式下的背景 */
.dark .preview-panel {
  background: #1f2937;
}

.dark .preview-content {
  background: #1f2937;
}
</style>
