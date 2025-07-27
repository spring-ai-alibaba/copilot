<template>
  <div class="tool-item" :class="toolItemClass">
    <div class="tool-info flex-1">
      <div class="tool-name flex items-center space-x-2 mb-2">
        <span class="tool-icon text-lg">{{ tool.icon || (type === 'mcp' ? '🌐' : '⚙️') }}</span>
        <span class="tool-title font-medium text-gray-900 dark:text-white">
          {{ tool.displayName || tool.name }}
        </span>
        <span class="tool-badge" :class="badgeClass">
          {{ type === 'mcp' ? 'MCP' : 'SYSTEM' }}
        </span>
        <div v-if="type === 'system'" class="tool-toggle">
          <label class="inline-flex items-center">
            <input
              type="checkbox"
              :checked="tool.enabled"
              @change="$emit('toggle', tool.name, $event.target.checked)"
              class="sr-only"
            >
            <div class="toggle-switch" :class="{ 'enabled': tool.enabled }">
              <div class="toggle-thumb"></div>
            </div>
          </label>
        </div>
      </div>
      
      <div class="tool-description text-sm text-gray-600 dark:text-gray-400 mb-2">
        {{ tool.description || '无描述' }}
      </div>
      
      <div class="tool-source text-xs text-gray-500 dark:text-gray-500">
        来源: {{ tool.source }}
      </div>
      
      <div v-if="tool.parameters && tool.parameters.length > 0" class="tool-parameters mt-2">
        <div class="text-xs text-gray-500 dark:text-gray-500 mb-1">参数:</div>
        <div class="flex flex-wrap gap-1">
          <span
            v-for="param in tool.parameters.slice(0, 3)"
            :key="param.name"
            class="parameter-tag"
          >
            {{ param.name }}
          </span>
          <span v-if="tool.parameters.length > 3" class="parameter-tag">
            +{{ tool.parameters.length - 3 }}
          </span>
        </div>
      </div>
    </div>
    
    <div class="tool-actions flex flex-col space-y-2 ml-4">
      <button
        @click="$emit('test', tool.name, type)"
        class="action-btn test-btn"
        :disabled="!tool.enabled && type === 'system'"
      >
        测试
      </button>
      
      <button
        @click="$emit('info', tool.name, type)"
        class="action-btn info-btn"
      >
        详情
      </button>
      
      <button
        v-if="type === 'mcp'"
        @click="$emit('remove', tool.name)"
        class="action-btn delete-btn"
      >
        删除
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Tool {
  name: string
  displayName?: string
  description: string
  type: 'SYSTEM' | 'MCP'
  source: string
  enabled: boolean
  parameters?: Array<{
    name: string
    type: string
    description: string
  }>
  icon?: string
}

interface Props {
  tool: Tool
  type: 'system' | 'mcp'
}

interface Emits {
  (e: 'test', toolName: string, type: string): void
  (e: 'info', toolName: string, type: string): void
  (e: 'toggle', toolName: string, enabled: boolean): void
  (e: 'remove', toolName: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// Computed
const toolItemClass = computed(() => {
  const baseClass = 'flex items-start p-4 border rounded-lg transition-all duration-200'
  const enabledClass = props.tool.enabled 
    ? 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/10' 
    : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800'
  
  return `${baseClass} ${enabledClass} hover:shadow-md`
})

const badgeClass = computed(() => {
  const baseClass = 'px-2 py-1 text-xs font-medium rounded-full'
  if (props.type === 'mcp') {
    return `${baseClass} bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200`
  } else {
    return `${baseClass} bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200`
  }
})
</script>

<style scoped>
.tool-item {
  animation: slideInUp 0.3s ease-out;
}

.action-btn {
  @apply px-3 py-1 text-sm font-medium rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed;
}

.test-btn {
  @apply bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 hover:bg-blue-200 dark:hover:bg-blue-800;
}

.info-btn {
  @apply bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200 hover:bg-gray-200 dark:hover:bg-gray-600;
}

.delete-btn {
  @apply bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200 hover:bg-red-200 dark:hover:bg-red-800;
}

.parameter-tag {
  @apply px-2 py-1 text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded;
}

.tool-toggle {
  margin-left: auto;
}

.toggle-switch {
  @apply relative inline-block w-10 h-5 bg-gray-300 dark:bg-gray-600 rounded-full transition-colors duration-200 cursor-pointer;
}

.toggle-switch.enabled {
  @apply bg-green-500 dark:bg-green-600;
}

.toggle-thumb {
  @apply absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-200;
}

.toggle-switch.enabled .toggle-thumb {
  @apply transform translate-x-5;
}

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
