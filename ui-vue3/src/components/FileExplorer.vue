<template>
  <div class="file-explorer">
    <!-- 工具栏 -->
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs text-gray-500 dark:text-gray-400">文件</span>
      <div class="flex space-x-1">
        <a-button type="text" size="small" @click="showCreateFileModal = true">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/>
            </svg>
          </template>
        </a-button>
        <a-button type="text" size="small" @click="showCreateFolderModal = true">
          <template #icon>
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
            </svg>
          </template>
        </a-button>
      </div>
    </div>

    <!-- 文件树 -->
    <div class="file-tree">
      <FileNode
        v-for="node in fileTree"
        :key="node.path"
        :node="node"
        :level="0"
        @select="handleFileSelect"
        @delete="handleFileDelete"
        @rename="handleFileRename"
      />
    </div>

    <!-- 创建文件模态框 -->
    <a-modal
      v-model:open="showCreateFileModal"
      title="创建文件"
      @ok="createFile"
      @cancel="cancelCreateFile"
    >
      <a-input
        v-model:value="newFileName"
        placeholder="输入文件名 (例如: index.js)"
        @press-enter="createFile"
      />
    </a-modal>

    <!-- 创建文件夹模态框 -->
    <a-modal
      v-model:open="showCreateFolderModal"
      title="创建文件夹"
      @ok="createFolder"
      @cancel="cancelCreateFolder"
    >
      <a-input
        v-model:value="newFolderName"
        placeholder="输入文件夹名"
        @press-enter="createFolder"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FileNode as FileNodeType } from '@/stores/fileStore'
import FileNode from './FileNode.vue'

interface Props {
  files: Record<string, string>
}

interface Emits {
  (e: 'file-select', filePath: string): void
  (e: 'file-create', filePath: string): void
  (e: 'file-delete', filePath: string): void
  (e: 'file-rename', oldPath: string, newPath: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 状态
const showCreateFileModal = ref(false)
const showCreateFolderModal = ref(false)
const newFileName = ref('')
const newFolderName = ref('')

// 计算文件树
const fileTree = computed(() => {
  const tree: FileNodeType[] = []
  const pathMap = new Map<string, FileNodeType>()

  // 构建文件树
  Object.keys(props.files).forEach(filePath => {
    const parts = filePath.split('/')
    let currentPath = ''

    parts.forEach((part, index) => {
      const parentPath = currentPath
      currentPath = currentPath ? `${currentPath}/${part}` : part
      
      if (!pathMap.has(currentPath)) {
        const isFile = index === parts.length - 1
        const node: FileNodeType = {
          name: part,
          path: currentPath,
          type: isFile ? 'file' : 'directory',
          children: isFile ? undefined : [],
          content: isFile ? props.files[filePath] : undefined
        }
        
        pathMap.set(currentPath, node)
        
        if (parentPath) {
          const parent = pathMap.get(parentPath)
          if (parent && parent.children) {
            parent.children.push(node)
          }
        } else {
          tree.push(node)
        }
      }
    })
  })

  // 排序
  const sortNodes = (nodes: FileNodeType[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) {
        return a.type === 'directory' ? -1 : 1
      }
      return a.name.localeCompare(b.name)
    })
    
    nodes.forEach(node => {
      if (node.children) {
        sortNodes(node.children)
      }
    })
  }

  sortNodes(tree)
  return tree
})

// 事件处理
const handleFileSelect = (filePath: string) => {
  emit('file-select', filePath)
}

const handleFileDelete = (filePath: string) => {
  emit('file-delete', filePath)
}

const handleFileRename = (oldPath: string, newPath: string) => {
  emit('file-rename', oldPath, newPath)
}

const createFile = () => {
  if (newFileName.value.trim()) {
    emit('file-create', newFileName.value.trim())
    newFileName.value = ''
    showCreateFileModal.value = false
  }
}

const cancelCreateFile = () => {
  newFileName.value = ''
  showCreateFileModal.value = false
}

const createFolder = () => {
  if (newFolderName.value.trim()) {
    // 创建文件夹通过创建一个占位文件实现
    const folderPath = `${newFolderName.value.trim()}/.gitkeep`
    emit('file-create', folderPath)
    newFolderName.value = ''
    showCreateFolderModal.value = false
  }
}

const cancelCreateFolder = () => {
  newFolderName.value = ''
  showCreateFolderModal.value = false
}
</script>

<style scoped>
.file-explorer {
  height: 100%;
  overflow-y: auto;
}

.file-tree {
  font-size: 13px;
}
</style>
