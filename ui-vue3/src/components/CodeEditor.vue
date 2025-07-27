<template>
  <div class="code-editor h-full flex flex-col bg-white dark:bg-gray-900">
    <!-- 编辑器工具栏 -->
    <div class="editor-toolbar flex items-center justify-between px-4 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center space-x-2">
        <span class="text-sm text-gray-600 dark:text-gray-300">
          {{ filePath }}
        </span>
        <span v-if="isDirty" class="text-xs text-orange-500">●</span>
      </div>

      <div class="flex items-center space-x-2">
        <a-button size="small" @click="formatCode" :disabled="!canFormat">
          格式化
        </a-button>
        <a-button size="small" @click="saveFile" :disabled="!isDirty">
          保存
        </a-button>
      </div>
    </div>

    <!-- 编辑器容器 -->
    <div class="editor-container flex-1 relative">
      <textarea
        ref="editorRef"
        v-model="editorContent"
        class="h-full w-full p-4 border-none outline-none resize-none bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 font-mono text-sm"
        :placeholder="`开始编辑 ${filePath}...`"
        @input="handleInput"
        @keydown="handleKeyDown"
        spellcheck="false"
      />

      <!-- 加载状态 -->
      <div
        v-if="loading"
        class="absolute inset-0 flex items-center justify-center bg-white dark:bg-gray-900 bg-opacity-75"
      >
        <a-spin size="large" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useWebContainerStore } from '@/stores/webContainerStore'

interface Props {
  filePath: string
  content: string
}

interface Emits {
  (e: 'content-change', content: string): void
  (e: 'save', filePath: string, content: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const webContainerStore = useWebContainerStore()

// 状态
const editorRef = ref<HTMLTextAreaElement>()
const loading = ref(false)
const isDirty = ref(false)
const editorContent = ref('')

// 检查是否可以格式化
const canFormat = ref(true)

// 处理输入
const handleInput = () => {
  isDirty.value = editorContent.value !== props.content
  emit('content-change', editorContent.value)
}

// 格式化代码
const formatCode = () => {
  try {
    const extension = props.filePath.split('.').pop()?.toLowerCase()

    // 简单的格式化逻辑
    if (extension === 'json') {
      try {
        const formatted = JSON.stringify(JSON.parse(editorContent.value), null, 2)
        editorContent.value = formatted
        handleInput()
      } catch (e) {
        console.warn('Invalid JSON, cannot format')
      }
    }
  } catch (error) {
    console.error('Failed to format code:', error)
  }
}

// 保存文件
const saveFile = async () => {
  if (!isDirty.value) return

  try {
    // 如果 WebContainer 已初始化，同步到容器
    if (webContainerStore.isReady) {
      await webContainerStore.writeFile(props.filePath, editorContent.value)
    }

    emit('save', props.filePath, editorContent.value)
    isDirty.value = false
  } catch (error) {
    console.error('Failed to save file:', error)
  }
}

// 键盘快捷键处理
const handleKeyDown = (event: KeyboardEvent) => {
  // Ctrl+S 保存
  if ((event.ctrlKey || event.metaKey) && event.key === 's') {
    event.preventDefault()
    saveFile()
  }

  // Ctrl+Shift+F 格式化
  if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'F') {
    event.preventDefault()
    formatCode()
  }
}

// 监听内容变化
watch(() => props.content, (newContent) => {
  if (editorContent.value !== newContent) {
    editorContent.value = newContent
    isDirty.value = false
  }
}, { immediate: true })
</script>

<style scoped>
.code-editor {
  font-family: 'Fira Code', 'JetBrains Mono', 'Monaco', 'Consolas', monospace;
}

.editor-container {
  overflow: hidden;
}

textarea {
  font-family: 'Fira Code', 'JetBrains Mono', 'Monaco', 'Consolas', monospace;
  line-height: 1.5;
  tab-size: 2;
}

textarea:focus {
  outline: none;
  box-shadow: none;
}
</style>
