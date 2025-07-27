<template>
  <div class="file-node">
    <!-- 节点内容 -->
    <div
      class="node-content flex items-center py-1 px-2 hover:bg-gray-100 dark:hover:bg-gray-700 cursor-pointer rounded"
      :style="{ paddingLeft: `${level * 16 + 8}px` }"
      @click="handleClick"
      @contextmenu="handleContextMenu"
    >
      <!-- 展开/收起图标 -->
      <div class="w-4 h-4 flex items-center justify-center mr-1">
        <svg
          v-if="node.type === 'directory'"
          class="w-3 h-3 transition-transform"
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
          class="w-4 h-4 text-blue-500"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z"/>
        </svg>
        <svg
          v-else
          class="w-4 h-4 text-gray-500"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4zm2 6a1 1 0 011-1h6a1 1 0 110 2H7a1 1 0 01-1-1zm1 3a1 1 0 100 2h6a1 1 0 100-2H7z" clip-rule="evenodd"/>
        </svg>
      </div>

      <!-- 文件名 -->
      <span
        v-if="!isRenaming"
        class="flex-1 text-sm text-gray-900 dark:text-gray-100 truncate"
        :class="{ 'font-medium': node.type === 'directory' }"
      >
        {{ node.name }}
      </span>

      <!-- 重命名输入框 -->
      <a-input
        v-else
        v-model:value="renamingValue"
        size="small"
        class="flex-1"
        @press-enter="confirmRename"
        @blur="cancelRename"
        @click.stop
        ref="renameInput"
      />
    </div>

    <!-- 子节点 -->
    <div v-if="node.type === 'directory' && isExpanded && node.children">
      <FileNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :level="level + 1"
        @select="$emit('select', $event)"
        @delete="$emit('delete', $event)"
        @rename="$emit('rename', $event.oldPath, $event.newPath)"
      />
    </div>

    <!-- 右键菜单 -->
    <a-dropdown
      v-model:open="showContextMenu"
      :trigger="['contextmenu']"
      placement="bottomLeft"
    >
      <div></div>
      <template #overlay>
        <a-menu @click="handleMenuClick">
          <a-menu-item key="rename">
            <template #icon>
              <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z"/>
              </svg>
            </template>
            重命名
          </a-menu-item>
          <a-menu-item key="delete" class="text-red-600">
            <template #icon>
              <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/>
              </svg>
            </template>
            删除
          </a-menu-item>
        </a-menu>
      </template>
    </a-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import type { FileNode as FileNodeType } from '@/stores/fileStore'

interface Props {
  node: FileNodeType
  level: number
}

interface Emits {
  (e: 'select', filePath: string): void
  (e: 'delete', filePath: string): void
  (e: 'rename', data: { oldPath: string; newPath: string }): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 状态
const isExpanded = ref(false)
const showContextMenu = ref(false)
const isRenaming = ref(false)
const renamingValue = ref('')
const renameInput = ref()

// 事件处理
const handleClick = () => {
  if (props.node.type === 'directory') {
    isExpanded.value = !isExpanded.value
  } else {
    emit('select', props.node.path)
  }
}

const handleContextMenu = (event: MouseEvent) => {
  event.preventDefault()
  showContextMenu.value = true
}

const handleMenuClick = ({ key }: { key: string }) => {
  showContextMenu.value = false
  
  switch (key) {
    case 'rename':
      startRename()
      break
    case 'delete':
      emit('delete', props.node.path)
      break
  }
}

const startRename = () => {
  isRenaming.value = true
  renamingValue.value = props.node.name
  
  nextTick(() => {
    if (renameInput.value) {
      renameInput.value.focus()
      renameInput.value.select()
    }
  })
}

const confirmRename = () => {
  if (renamingValue.value.trim() && renamingValue.value !== props.node.name) {
    const pathParts = props.node.path.split('/')
    pathParts[pathParts.length - 1] = renamingValue.value.trim()
    const newPath = pathParts.join('/')
    
    emit('rename', {
      oldPath: props.node.path,
      newPath: newPath
    })
  }
  
  cancelRename()
}

const cancelRename = () => {
  isRenaming.value = false
  renamingValue.value = ''
}
</script>

<style scoped>
.file-node {
  user-select: none;
}

.node-content {
  transition: background-color 0.15s ease;
}

.rotate-90 {
  transform: rotate(90deg);
}
</style>
