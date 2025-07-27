<template>
  <div class="editor-tabs bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
    <div class="flex items-center overflow-x-auto">
      <div
        v-for="tab in tabs"
        :key="tab"
        class="tab-item flex items-center px-4 py-2 border-r border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
        :class="{
          'bg-gray-50 dark:bg-gray-700 border-b-2 border-blue-500': tab === activeTab,
          'text-gray-900 dark:text-white': tab === activeTab,
          'text-gray-600 dark:text-gray-300': tab !== activeTab
        }"
        @click="selectTab(tab)"
      >
        <!-- 文件图标 -->
        <div class="w-4 h-4 mr-2 flex-shrink-0">
          <svg
            v-if="getFileIcon(tab) === 'js'"
            class="w-4 h-4 text-yellow-500"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"/>
          </svg>
          <svg
            v-else-if="getFileIcon(tab) === 'html'"
            class="w-4 h-4 text-orange-500"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"/>
          </svg>
          <svg
            v-else-if="getFileIcon(tab) === 'css'"
            class="w-4 h-4 text-blue-500"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"/>
          </svg>
          <svg
            v-else-if="getFileIcon(tab) === 'json'"
            class="w-4 h-4 text-green-500"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"/>
          </svg>
          <svg
            v-else
            class="w-4 h-4 text-gray-500"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z"/>
          </svg>
        </div>

        <!-- 文件名 -->
        <span class="text-sm truncate max-w-32" :title="tab">
          {{ getFileName(tab) }}
        </span>

        <!-- 关闭按钮 -->
        <button
          class="ml-2 w-4 h-4 flex items-center justify-center rounded hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
          @click.stop="closeTab(tab)"
        >
          <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
          </svg>
        </button>
      </div>

      <!-- 空状态 -->
      <div
        v-if="tabs.length === 0"
        class="flex items-center px-4 py-2 text-gray-500 dark:text-gray-400 text-sm"
      >
        没有打开的文件
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  tabs: string[]
  activeTab: string | null
}

interface Emits {
  (e: 'tab-select', filePath: string): void
  (e: 'tab-close', filePath: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 获取文件名
const getFileName = (filePath: string): string => {
  return filePath.split('/').pop() || filePath
}

// 获取文件图标类型
const getFileIcon = (filePath: string): string => {
  const extension = filePath.split('.').pop()?.toLowerCase()
  
  switch (extension) {
    case 'js':
    case 'jsx':
    case 'ts':
    case 'tsx':
      return 'js'
    case 'html':
    case 'htm':
      return 'html'
    case 'css':
    case 'scss':
    case 'sass':
    case 'less':
      return 'css'
    case 'json':
      return 'json'
    case 'vue':
      return 'vue'
    case 'md':
    case 'markdown':
      return 'md'
    default:
      return 'file'
  }
}

// 选择标签页
const selectTab = (filePath: string) => {
  emit('tab-select', filePath)
}

// 关闭标签页
const closeTab = (filePath: string) => {
  emit('tab-close', filePath)
}
</script>

<style scoped>
.editor-tabs {
  min-height: 40px;
}

.tab-item {
  min-width: 120px;
  max-width: 200px;
  white-space: nowrap;
}

.tab-item:last-child {
  border-right: none;
}

/* 滚动条样式 */
.editor-tabs > div {
  scrollbar-width: thin;
  scrollbar-color: rgba(156, 163, 175, 0.5) transparent;
}

.editor-tabs > div::-webkit-scrollbar {
  height: 4px;
}

.editor-tabs > div::-webkit-scrollbar-track {
  background: transparent;
}

.editor-tabs > div::-webkit-scrollbar-thumb {
  background-color: rgba(156, 163, 175, 0.5);
  border-radius: 2px;
}

.editor-tabs > div::-webkit-scrollbar-thumb:hover {
  background-color: rgba(156, 163, 175, 0.7);
}
</style>
