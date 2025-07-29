<template>
  <div class="h-full flex flex-col bg-gray-50 dark:bg-gray-900">
    <!-- Header -->
    <header class="bg-white dark:bg-gray-800 shadow-sm border-b border-gray-200 dark:border-gray-700 h-14 flex-shrink-0">
      <div class="h-full px-4 flex justify-between items-center">
        <div class="flex items-center space-x-4">
          <a-button type="text" @click="goHome" class="flex items-center">
            <svg class="w-4 h-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z" clip-rule="evenodd"/>
            </svg>
            返回
          </a-button>
          <h1 class="text-lg font-semibold text-gray-900 dark:text-white">
            代码编辑器
          </h1>
        </div>

        <div class="flex items-center space-x-2">
          <a-button @click="loadWorkspaceFiles" :loading="fileStore.isLoading">
            重新加载工作目录
          </a-button>
          <a-button @click="initWebContainer" :loading="containerLoading">
            {{ containerStatus === 'ready' ? '容器已就绪' : '初始化容器' }}
          </a-button>
          <a-button type="primary" @click="runProject" :disabled="containerStatus !== 'ready'">
            运行项目
          </a-button>
          <a-button @click="toggleTerminal">
            {{ showTerminal ? '隐藏终端' : '显示终端' }}
          </a-button>
          <a-button @click="toggleFileExplorer" class="ml-2">
            {{ showFileExplorer ? '隐藏文件浏览器' : '显示文件浏览器' }}
          </a-button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <div class="flex-1 flex overflow-hidden">
      <!-- AI Chat Sidebar (Left) -->
      <div
        ref="chatSidebar"
        :style="{ width: chatSidebarWidth + 'px' }"
        class="bg-white dark:bg-gray-800 flex-shrink-0 flex flex-col border-r border-gray-200 dark:border-gray-700"
        style="min-width: 280px; max-width: 600px;"
      >
        <!-- AI Chat Content -->
        <div class="flex-1 overflow-hidden">
          <AiChat
            :initial-message="initialPrompt"
            :auto-send="autoSendMessage"
          />
        </div>
      </div>

      <!-- Resizable Divider -->
      <div
        ref="resizeDivider"
        class="w-1 bg-gray-200 dark:bg-gray-700 hover:bg-blue-400 dark:hover:bg-blue-500 cursor-col-resize transition-colors duration-200 relative group"
        @mousedown="startResize"
      >
        <!-- Resize Handle Visual Indicator -->
        <div class="absolute inset-y-0 left-0 w-1 bg-blue-400 dark:bg-blue-500 opacity-0 group-hover:opacity-100 transition-opacity duration-200"></div>
        <div class="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-3 h-8 bg-gray-300 dark:bg-gray-600 rounded-full opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center">
          <div class="w-0.5 h-4 bg-gray-500 dark:bg-gray-400 rounded-full mx-0.5"></div>
          <div class="w-0.5 h-4 bg-gray-500 dark:bg-gray-400 rounded-full mx-0.5"></div>
        </div>
      </div>

      <!-- Right Panel: File Explorer + Editor -->
      <div class="flex-1 flex overflow-hidden">
        <!-- File Explorer (可隐藏) -->
        <div v-if="showFileExplorer" class="w-64 bg-white dark:bg-gray-800 flex-shrink-0 flex flex-col">
          <!-- File Explorer Header -->
          <div class="p-3 border-b border-gray-200 dark:border-gray-700  ml-2 mr-2.5">
            <div class="flex items-center justify-between min-w-0">
              <h3 class="text-sm font-medium text-gray-900 dark:text-white truncate flex-shrink">
                文件浏览器
              </h3>
              <div class="flex items-center space-x-1 flex-shrink-0 ml-2 mr-2.5">
                <div
                  :class="{
                    'w-2 h-2 rounded-full': true,
                    'bg-green-500': fileStore.isConnectedToBackend,
                    'bg-red-500': !fileStore.isConnectedToBackend,
                    'animate-pulse': fileStore.isLoading
                  }"
                ></div>
                <span class="text-xs text-gray-500 whitespace-nowrap">
                  {{ fileStore.isConnectedToBackend ? '已连接' : '未连接' }}
                </span>
              </div>
            </div>
          </div>

          <!-- File Explorer Content -->
          <div class="flex-1 overflow-hidden">
            <!-- 错误提示 -->
            <div v-if="fileStore.error" class="mx-4 mt-2 p-2 bg-red-50 border border-red-200 rounded text-xs text-red-600">
              {{ fileStore.error }}
              <a-button type="link" size="small" @click="fileStore.clearError" class="p-0 h-auto">
                关闭
              </a-button>
            </div>

            <div class="p-2 flex-1 overflow-y-auto">
              <!-- 简单的文件创建界面 -->
              <div class="mb-4">
                <a-button type="primary" size="small" @click="showCreateModal = true" block>
                  创建文件
                </a-button>
              </div>

              <!-- 文件树 -->
              <div class="file-tree">
                <FileTreeNode
                  v-for="node in fileStore.fileTree"
                  :key="node.path"
                  :node="node"
                  :level="0"
                  :active-file="activeTab"
                  @file-select="handleFileSelect"
                  @file-delete="handleFileDelete"
                />
              </div>

              <!-- 加载状态 -->
              <div v-if="fileStore.isLoading" class="text-center py-8">
                <div class="animate-spin w-8 h-8 mx-auto mb-2 border-2 border-blue-500 border-t-transparent rounded-full"></div>
                <p class="text-sm text-gray-500">
                  {{ fileStore.isInitialized ? '正在重新加载工作目录...' : '正在加载工作目录...' }}
                </p>
              </div>

              <!-- 空状态 -->
              <div v-else-if="fileStore.fileTree.length === 0 && fileStore.isInitialized" class="text-center py-8">
                <svg class="w-12 h-12 mx-auto text-gray-400 mb-2" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clip-rule="evenodd"/>
                </svg>
                <p class="text-sm text-gray-500">
                  {{ fileStore.isConnectedToBackend ? '工作目录为空' : '暂无文件' }}
                </p>
                <p class="text-xs text-gray-400">
                  {{ fileStore.isConnectedToBackend ? '点击"重新加载工作目录"或创建新文件' : '点击上方按钮创建文件' }}
                </p>
              </div>

              <!-- 未初始化状态 -->
              <div v-else-if="!fileStore.isInitialized && !fileStore.isLoading" class="text-center py-8">
                <svg class="w-12 h-12 mx-auto text-gray-400 mb-2" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" clip-rule="evenodd"/>
                </svg>
                <p class="text-sm text-gray-500">准备加载工作目录</p>
                <p class="text-xs text-gray-400">正在连接到后端服务...</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Editor and Preview Area -->
        <div class="flex-1 flex flex-col">
          <!-- Editor Tabs -->
          <div class="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
            <div v-if="openTabs.length > 0" class="flex items-center overflow-x-auto">
              <div
                v-for="tab in openTabs"
                :key="tab"
                class="flex items-center px-4 py-2 border-r border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                :class="{
                  'bg-blue-50 dark:bg-blue-900 text-blue-600 dark:text-blue-400': activeTab === tab,
                  'text-gray-600 dark:text-gray-300': activeTab !== tab
                }"
                @click="switchTab(tab)"
              >
                <span class="text-sm truncate max-w-32" :title="tab">
                  {{ tab.split('/').pop() }}
                </span>
                <button
                  @click.stop="closeTab(tab)"
                  class="ml-2 w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
                >
                  <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                  </svg>
                </button>
              </div>
            </div>
            <div v-else class="p-3">
              <div class="text-sm text-gray-500 dark:text-gray-400">没有打开的文件</div>
            </div>
          </div>

          <!-- Editor and Preview Split -->
          <div class="flex-1 flex">
            <!-- Code Editor -->
            <div :class="showPreview ? 'flex-1' : 'w-full'" class="flex flex-col">
              <div v-if="activeTab" class="flex-1 flex flex-col">
                <!-- 编辑器工具栏 -->
                <div class="bg-gray-50 dark:bg-gray-800 px-4 py-2 border-b border-gray-200 dark:border-gray-700">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-gray-600 dark:text-gray-300">{{ activeTab }}</span>
                    <div class="flex space-x-2">
                      <a-button size="small" @click="saveFile">保存</a-button>
                      <a-button size="small" @click="previewFile" v-if="activeTab.endsWith('.html')">预览</a-button>
                    </div>
                  </div>
                </div>

                <!-- 文件写入进度条 -->
                <div v-if="fileWriteProgress && fileWriteProgress.filePath === activeTab"
                     class="bg-blue-50 dark:bg-blue-900 border-b border-blue-200 dark:border-blue-700 px-4 py-2">
                  <div class="flex items-center justify-between text-sm text-blue-700 dark:text-blue-300 mb-1">
                    <span>🌊 正在流式写入文件...</span>
                    <span>{{ fileWriteProgress.progress.toFixed(1) }}%</span>
                  </div>
                  <div class="w-full bg-blue-200 dark:bg-blue-800 rounded-full h-2">
                    <div
                      class="bg-blue-600 h-2 rounded-full transition-all duration-300"
                      :style="{ width: fileWriteProgress.progress + '%' }"
                    ></div>
                  </div>
                  <div class="text-xs text-blue-600 dark:text-blue-400 mt-1">
                    {{ Math.round(fileWriteProgress.writtenBytes / 1024) }}KB / {{ Math.round(fileWriteProgress.totalBytes / 1024) }}KB
                  </div>
                </div>

                <!-- 代码编辑器 -->
                <textarea
                  v-model="currentFileContent"
                  class="flex-1 p-4 border-none outline-none resize-none bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-mono text-sm"
                  :placeholder="`编辑 ${activeTab}...`"
                  @input="handleContentChange"
                  spellcheck="false"
                  style="font-family: 'Consolas', 'Monaco', 'Courier New', monospace; line-height: 1.5; tab-size: 2;"
                />
              </div>

              <div v-else class="flex-1 flex items-center justify-center bg-gray-50 dark:bg-gray-900">
                <div class="text-center">
                  <svg class="w-16 h-16 mx-auto text-gray-400 mb-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clip-rule="evenodd"/>
                  </svg>
                  <p class="text-gray-500 dark:text-gray-400">选择一个文件开始编辑</p>
                </div>
              </div>
            </div>

            <!-- Preview Panel - 只有在showPreview为true时才显示 -->
            <div
              v-if="showPreview"
              :class="{
                'w-1/2 border-l border-gray-200 dark:border-gray-700': !isPreviewFullscreen,
                'fixed inset-0 z-50 bg-white dark:bg-gray-900': isPreviewFullscreen
              }"
              class="flex flex-col"
            >
              <!-- 预览工具栏 -->
              <div class="bg-gray-50 dark:bg-gray-800 px-4 py-2 border-b border-gray-200 dark:border-gray-700">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-gray-600 dark:text-gray-300">预览</span>
                  <div class="flex space-x-2">
                    <a-button size="small" @click="refreshPreview" v-if="previewContent || previewUrl">刷新</a-button>
                    <a-button size="small" @click="togglePreviewFullscreen">
                      {{ isPreviewFullscreen ? '退出全屏' : '全屏' }}
                    </a-button>
                    <a-button size="small" @click="closePreview">关闭</a-button>
                  </div>
                </div>
              </div>

              <!-- 预览内容 -->
              <div class="flex-1 bg-white">
                <!-- 使用静态文件服务预览 -->
                <iframe
                  v-if="previewUrl"
                  :src="previewUrl"
                  class="w-full h-full border-none"
                  sandbox="allow-scripts allow-same-origin"
                />
                <!-- 使用srcdoc预览（后端未连接时的降级方案） -->
                <iframe
                  v-else-if="previewContent"
                  :srcdoc="previewContent"
                  class="w-full h-full border-none"
                  sandbox="allow-scripts"
                />
                <!-- 空状态 -->
                <div v-else class="flex-1 flex items-center justify-center h-full">
                  <div class="text-center">
                    <svg class="w-16 h-16 mx-auto text-gray-400 mb-4" fill="currentColor" viewBox="0 0 20 20">
                      <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" clip-rule="evenodd"/>
                    </svg>
                    <p class="text-gray-500 dark:text-gray-400">选择 HTML 文件进行预览</p>
                    <p class="text-sm text-gray-400">或点击编辑器中的"预览"按钮</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Terminal - 只有在showTerminal为true时才显示 -->
          <div v-if="showTerminal" class="h-48 border-t border-gray-200 dark:border-gray-700 flex flex-col bg-black">
            <!-- 终端工具栏 -->
            <div class="bg-gray-800 px-4 py-2 border-b border-gray-600">
              <div class="flex items-center justify-between">
                <span class="text-sm text-gray-300">终端</span>
                <div class="flex space-x-2">
                  <a-button size="small" @click="toggleTerminal" class="text-gray-300">关闭</a-button>
                </div>
              </div>
            </div>
            <!-- 终端内容 -->
            <div class="flex-1 flex items-center justify-center">
              <p class="text-green-400">终端面板区域</p>
            </div>
          </div>

        </div>
      </div>
    </div>

    <!-- 创建文件模态框 -->
    <a-modal
      v-model:open="showCreateModal"
      title="创建新文件"
      @ok="createFile"
      @cancel="cancelCreate"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            文件名
          </label>
          <a-input
            v-model:value="newFileName"
            placeholder="例如: index.html, script.js, style.css"
            @press-enter="createFile"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            快速模板
          </label>
          <div class="grid grid-cols-2 gap-2">
            <a-button @click="setTemplate('index.html')" size="small">HTML 页面</a-button>
            <a-button @click="setTemplate('script.js')" size="small">JavaScript</a-button>
            <a-button @click="setTemplate('style.css')" size="small">CSS 样式</a-button>
            <a-button @click="setTemplate('data.json')" size="small">JSON 数据</a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFileStore } from '@/stores/fileStore'
import { getFileTemplate } from '@/utils/fileTemplates'
import FileTreeNode from '@/components/FileTreeNode.vue'
import AiChat from '@/components/AiChat.vue'

const router = useRouter()
const route = useRoute()
const fileStore = useFileStore()

// 状态管理
const containerLoading = ref(false)
const containerStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const openTabs = ref<string[]>([])
const activeTab = ref<string | null>(null)
const showFileExplorer = ref(true)

// 拖拽调整相关
const chatSidebar = ref<HTMLElement>()
const resizeDivider = ref<HTMLElement>()
const chatSidebarWidth = ref(320) // 默认宽度
const isResizing = ref(false)

// 路由参数处理
const initialPrompt = ref('')
const autoSendMessage = ref(false)

// 文件编辑相关
const currentFileContent = ref('')
const previewContent = ref('')
const previewUrl = ref('')
const showPreview = ref(false)
const showTerminal = ref(false)
const isPreviewFullscreen = ref(false)

// 模态框相关
const showCreateModal = ref(false)
const newFileName = ref('')

// 文件写入进度状态
const fileWriteProgress = ref<{
  filePath: string
  progress: number
  writtenBytes: number
  totalBytes: number
} | null>(null)

const goHome = () => {
  router.push('/')
}

const toggleFileExplorer = () => {
  showFileExplorer.value = !showFileExplorer.value
}

const toggleTerminal = () => {
  showTerminal.value = !showTerminal.value
}

// 拖拽调整聊天栏宽度
const startResize = (e: MouseEvent) => {
  e.preventDefault()
  isResizing.value = true

  const startX = e.clientX
  const startWidth = chatSidebarWidth.value

  const handleMouseMove = (e: MouseEvent) => {
    if (!isResizing.value) return

    const deltaX = e.clientX - startX
    const newWidth = Math.max(280, Math.min(600, startWidth + deltaX))
    chatSidebarWidth.value = newWidth

    // 添加用户选择禁用样式，防止拖拽时选中文本
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'col-resize'
  }

  const handleMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)

    // 恢复用户选择和光标样式
    document.body.style.userSelect = ''
    document.body.style.cursor = ''

    // 保存宽度到localStorage
    localStorage.setItem('chatSidebarWidth', chatSidebarWidth.value.toString())
  }

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

// 处理来自Home页面的路由参数
onMounted(async () => {
  const prompt = route.query.prompt as string
  const autoSend = route.query.autoSend as string

  if (prompt) {
    initialPrompt.value = prompt
  }

  if (autoSend === 'true') {
    autoSendMessage.value = true
  }

  // 从localStorage恢复聊天栏宽度
  const savedWidth = localStorage.getItem('chatSidebarWidth')
  if (savedWidth) {
    const width = parseInt(savedWidth)
    if (width >= 280 && width <= 600) {
      chatSidebarWidth.value = width
    }
  }

  // 自动加载工作目录
  await loadWorkspaceFiles()

  // 监听自动选中文件事件
  window.addEventListener('auto-select-file', handleAutoSelectFile as unknown as EventListener)

  // 监听编辑器内容更新事件
  window.addEventListener('update-editor-content', handleUpdateEditorContent as unknown as EventListener)
  window.addEventListener('finalize-editor-content', handleFinalizeEditorContent as unknown as EventListener)
  window.addEventListener('file-write-progress', handleFileWriteProgress as unknown as EventListener)
})

onUnmounted(() => {
  // 清理事件监听器
  window.removeEventListener('auto-select-file', handleAutoSelectFile as unknown as EventListener)
  window.removeEventListener('update-editor-content', handleUpdateEditorContent as unknown as EventListener)
  window.removeEventListener('finalize-editor-content', handleFinalizeEditorContent as unknown as EventListener)
  window.removeEventListener('file-write-progress', handleFileWriteProgress as unknown as EventListener)
})

const initWebContainer = async () => {
  containerLoading.value = true
  containerStatus.value = 'loading'

  // 模拟初始化
  setTimeout(() => {
    containerStatus.value = 'ready'
    containerLoading.value = false
  }, 2000)
}

const runProject = async () => {
  console.log('运行项目')
}

// 加载工作目录文件
const loadWorkspaceFiles = async () => {
  try {
    await fileStore.loadWorkspaceFiles()
    console.log('工作目录文件加载成功')

    // 如果成功加载且有文件，可以考虑自动打开第一个文件
    if (fileStore.fileTree.length > 0 && openTabs.value.length === 0) {
      const firstFile = findFirstFile(fileStore.fileTree)
      if (firstFile) {
        await handleFileSelect(firstFile.path)
      }
    }
  } catch (error) {
    console.error('加载工作目录失败:', error)
    // 错误已经在fileStore中处理，这里只需要记录日志
  }
}

// 辅助函数：查找第一个文件
const findFirstFile = (nodes: any[]): any => {
  for (const node of nodes) {
    if (node.type === 'file') {
      return node
    }
    if (node.children && node.children.length > 0) {
      const found = findFirstFile(node.children)
      if (found) return found
    }
  }
  return null
}

// 文件操作函数
const handleFileSelect = async (filePath: string) => {
  try {
    // 如果连接到后端，且文件内容还未加载，先从后端加载文件内容
    if (fileStore.isConnectedToBackend && !fileStore.getContent(filePath)) {
      const { FileSystemApi } = await import('@/services/fileSystemApi')
      const content = await FileSystemApi.readFile(filePath)
      fileStore.updateContent(filePath, content)
    }

    if (!openTabs.value.includes(filePath)) {
      openTabs.value.push(filePath)
    }
    activeTab.value = filePath
    currentFileContent.value = fileStore.getContent(filePath)

    // 如果是 HTML 文件，自动预览
    if (filePath.endsWith('.html')) {
      await updatePreview(filePath)
    }
  } catch (error) {
    console.error('加载文件失败:', error)
  }
}

// 处理自动选中文件事件
const handleAutoSelectFile = async (event: CustomEvent) => {
  const { filePath } = event.detail
  console.log('🎯 自动选中文件:', filePath)

  // 等待文件树刷新
  await fileStore.loadFiles()

  // 选中文件
  await handleFileSelect(filePath)

  console.log('✅ 文件已自动选中并打开:', filePath)
}

// 处理编辑器内容实时更新事件
const handleUpdateEditorContent = (event: CustomEvent) => {
  const { filePath, content, isStreaming } = event.detail
  console.log('📝 实时更新编辑器内容:', filePath, '长度:', content.length)

  // 更新文件存储中的内容
  fileStore.updateContent(filePath, content)

  // 如果当前打开的是这个文件，更新编辑器内容
  if (activeTab.value === filePath) {
    currentFileContent.value = content

    // 如果是流式更新，自动滚动到底部
    if (isStreaming) {
      nextTick(() => {
        const textarea = document.querySelector('textarea')
        if (textarea) {
          textarea.scrollTop = textarea.scrollHeight
        }
      })
    }
  }
}

// 处理编辑器内容完成事件
const handleFinalizeEditorContent = async (event: CustomEvent) => {
  const { filePath } = event.detail
  console.log('✅ 文件写入完成，最终更新:', filePath)

  // 清除进度显示
  fileWriteProgress.value = null

  // 重新从后端加载文件内容，确保内容完整
  if (fileStore.isConnectedToBackend) {
    try {
      const { FileSystemApi } = await import('@/services/fileSystemApi')
      const content = await FileSystemApi.readFile(filePath)
      fileStore.updateContent(filePath, content)

      // 如果当前打开的是这个文件，更新编辑器内容
      if (activeTab.value === filePath) {
        currentFileContent.value = content
      }
    } catch (error) {
      console.error('重新加载文件内容失败:', error)
    }
  }
}

// 处理文件写入进度事件
const handleFileWriteProgress = (event: CustomEvent) => {
  const { filePath, progress, writtenBytes, totalBytes } = event.detail
  console.log('📊 文件写入进度:', filePath, progress + '%')

  fileWriteProgress.value = {
    filePath,
    progress,
    writtenBytes,
    totalBytes
  }
}

const handleFileDelete = async (filePath: string) => {
  try {
    if (fileStore.isConnectedToBackend) {
      await fileStore.deleteFileFromBackend(filePath)
    } else {
      fileStore.deleteFile(filePath)
    }

    // 如果删除的是当前打开的文件，关闭对应的标签页
    if (openTabs.value.includes(filePath)) {
      closeTab(filePath)
    }

    console.log(`文件 ${filePath} 已删除`)
  } catch (error) {
    console.error('删除文件失败:', error)
  }
}

// 标签页相关函数
const switchTab = (filePath: string) => {
  activeTab.value = filePath
  currentFileContent.value = fileStore.getContent(filePath)
}

const closeTab = (filePath: string) => {
  const tabIndex = openTabs.value.indexOf(filePath)
  if (tabIndex > -1) {
    openTabs.value.splice(tabIndex, 1)

    // 如果关闭的是当前活动标签页，切换到其他标签页
    if (activeTab.value === filePath) {
      if (openTabs.value.length > 0) {
        // 优先选择右边的标签页，如果没有则选择左边的
        const nextTab = openTabs.value[tabIndex] || openTabs.value[tabIndex - 1]
        switchTab(nextTab)
      } else {
        activeTab.value = null
        currentFileContent.value = ''
      }
    }
  }
}

// 编辑器相关函数
const handleContentChange = () => {
  if (activeTab.value) {
    fileStore.updateContent(activeTab.value, currentFileContent.value)
  }
}

const saveFile = async () => {
  if (!activeTab.value) return

  try {
    if (fileStore.isConnectedToBackend) {
      await fileStore.saveFileToBackend(activeTab.value, currentFileContent.value)
    } else {
      fileStore.updateContent(activeTab.value, currentFileContent.value)
    }
    console.log(`文件 ${activeTab.value} 已保存`)
  } catch (error) {
    console.error('保存文件失败:', error)
  }
}

const updatePreview = async (filePath: string) => {
  if (!filePath.endsWith('.html')) return

  try {
    // 如果连接到后端，使用静态文件服务
    if (fileStore.isConnectedToBackend) {
      const { FileSystemApi } = await import('@/services/fileSystemApi')

      // 先保存当前文件内容到后端
      if (activeTab.value === filePath) {
        await fileStore.saveFileToBackend(filePath, currentFileContent.value)
      }

      // 使用静态文件服务的URL
      previewUrl.value = FileSystemApi.getStaticFileUrl(filePath)
      previewContent.value = '' // 清空srcdoc内容
    } else {
      // 后端未连接时，使用srcdoc方式
      previewContent.value = currentFileContent.value
      previewUrl.value = ''
    }
  } catch (error) {
    console.error('更新预览失败:', error)
    // 降级到srcdoc方式
    previewContent.value = currentFileContent.value
    previewUrl.value = ''
  }
}

const previewFile = async () => {
  if (activeTab.value && activeTab.value.endsWith('.html')) {
    await updatePreview(activeTab.value)
    showPreview.value = true
  }
}

const refreshPreview = async () => {
  if (activeTab.value && activeTab.value.endsWith('.html')) {
    await updatePreview(activeTab.value)
  }
}

const closePreview = () => {
  showPreview.value = false
  previewUrl.value = ''
  previewContent.value = ''
  isPreviewFullscreen.value = false // 关闭预览时也退出全屏
}

const togglePreviewFullscreen = () => {
  isPreviewFullscreen.value = !isPreviewFullscreen.value
}

// 文件创建相关函数
const createFile = async () => {
  if (!newFileName.value.trim()) return

  try {
    const template = getFileTemplate(newFileName.value)

    if (fileStore.isConnectedToBackend) {
      await fileStore.createFileInBackend(newFileName.value, template)
    } else {
      fileStore.addFile(newFileName.value, template)
    }

    // 打开新创建的文件
    handleFileSelect(newFileName.value)

    showCreateModal.value = false
    newFileName.value = ''
    console.log(`文件 ${newFileName.value} 创建成功`)
  } catch (error) {
    console.error('创建文件失败:', error)
  }
}

const cancelCreate = () => {
  showCreateModal.value = false
  newFileName.value = ''
}

const setTemplate = (fileName: string) => {
  newFileName.value = fileName
}
</script>

<style scoped>
.file-tree {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(156, 163, 175, 0.5) transparent;
}

.file-tree::-webkit-scrollbar {
  width: 6px;
}

.file-tree::-webkit-scrollbar-track {
  background: transparent;
}

.file-tree::-webkit-scrollbar-thumb {
  background-color: rgba(156, 163, 175, 0.5);
  border-radius: 3px;
}

.file-tree::-webkit-scrollbar-thumb:hover {
  background-color: rgba(156, 163, 175, 0.7);
}
</style>
