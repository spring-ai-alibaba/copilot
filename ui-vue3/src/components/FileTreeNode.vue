<template>
  <div class="file-tree-node">
    <!-- 节点内容 -->
    <div
      class="node-content flex items-center py-1 px-2 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer rounded text-sm"
      :class="{
        'bg-blue-50 dark:bg-blue-900': node.type === 'file' && activeFile === node.path
      }"
      :style="{ paddingLeft: `${level * 16 + 8}px` }"
      @click="handleClick"
    >
      <!-- 展开/收起图标 -->
      <div class="w-4 h-4 flex items-center justify-center mr-1">
        <svg
          v-if="node.type === 'directory'"
          class="w-3 h-3 transition-transform text-gray-500"
          :class="{ 'rotate-90': isExpanded }"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd"/>
        </svg>
      </div>

      <!-- 文件/文件夹图标 -->
      <div class="w-4 h-4 flex items-center justify-center mr-2">
        <svg
          v-if="node.type === 'directory'"
          class="w-4 h-4"
          :class="isExpanded ? 'text-blue-500' : 'text-yellow-600'"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path v-if="isExpanded" d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
          <path v-else d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
        </svg>
        <svg
          v-else
          class="w-4 h-4"
          :class="getFileIconColor(node.name)"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clip-rule="evenodd"/>
        </svg>
      </div>

      <!-- 文件名 -->
      <span
        class="flex-1 text-gray-900 dark:text-gray-100 truncate"
        :class="{ 'font-medium': node.type === 'directory' }"
        :title="node.path"
      >
        {{ node.name }}
      </span>

      <!-- 操作按钮 -->
      <div v-if="node.type === 'file'" class="flex items-center space-x-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <a-button 
          type="text" 
          size="small" 
          @click.stop="handleDelete"
          danger
          class="p-0 w-5 h-5 flex items-center justify-center"
        >
          <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/>
          </svg>
        </a-button>
      </div>
    </div>

    <!-- 子节点 -->
    <div v-if="node.type === 'directory' && isExpanded && node.children && node.children.length > 0">
      <FileTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :level="level + 1"
        :active-file="activeFile"
        @file-select="$emit('file-select', $event)"
        @file-delete="$emit('file-delete', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FileNode } from '@/stores/fileStore'

interface Props {
  node: FileNode
  level: number
  activeFile?: string | null
}

interface Emits {
  (e: 'file-select', filePath: string): void
  (e: 'file-delete', filePath: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 状态
const isExpanded = ref(props.level < 2) // 默认展开前两层

// 获取文件图标颜色
const getFileIconColor = (fileName: string): string => {
  const extension = fileName.split('.').pop()?.toLowerCase()
  
  switch (extension) {
    case 'html':
    case 'htm':
      return 'text-orange-500'
    case 'js':
    case 'jsx':
    case 'ts':
    case 'tsx':
      return 'text-yellow-500'
    case 'css':
    case 'scss':
    case 'sass':
    case 'less':
      return 'text-blue-500'
    case 'json':
      return 'text-green-500'
    case 'vue':
      return 'text-green-600'
    case 'md':
    case 'markdown':
      return 'text-gray-600'
    case 'java':
      return 'text-red-600'
    case 'py':
    case 'python':
      return 'text-blue-600'
    case 'xml':
    case 'yml':
    case 'yaml':
      return 'text-purple-500'
    default:
      return 'text-gray-500'
  }
}

// 事件处理
const handleClick = () => {
  if (props.node.type === 'directory') {
    isExpanded.value = !isExpanded.value
  } else {
    emit('file-select', props.node.path)
  }
}

const handleDelete = () => {
  emit('file-delete', props.node.path)
}
</script>

<style scoped>
.file-tree-node {
  user-select: none;
}

.node-content {
  transition: background-color 0.15s ease;
}

.node-content:hover .opacity-0 {
  opacity: 1;
}

.rotate-90 {
  transform: rotate(90deg);
}

/* 文件树缩进线 */
.file-tree-node::before {
  content: '';
  position: absolute;
  left: calc(var(--level) * 16px + 12px);
  top: 0;
  bottom: 0;
  width: 1px;
  background-color: rgba(156, 163, 175, 0.3);
}

.file-tree-node:last-child::before {
  height: 20px;
}
</style>
