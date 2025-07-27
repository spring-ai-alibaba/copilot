<template>
  <div class="chat-input-container">

    
    <!-- Quick Actions -->
    <div class="quick-actions mb-4">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="action in quickActions"
          :key="action.text"
          @click="$emit('quick-action', action.text)"
          :disabled="isLoading"
          class="quick-action-btn"
        >
          <span class="mr-1">{{ action.icon }}</span>
          {{ action.label }}
        </button>
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-wrapper flex items-end space-x-2">
      <div class="flex-1 relative">
        <textarea
          ref="textareaRef"
          v-model="inputValue"
          @keydown="handleKeydown"
          @input="adjustHeight"
          :disabled="isLoading"
          :placeholder="placeholder"
          class="input-textarea"
          rows="1"
        ></textarea>
        <div v-if="inputValue.length > 0" class="char-count">
          {{ inputValue.length }}
        </div>
      </div>

      <!-- 中断按钮 (仅在加载时显示) -->
      <button
        v-if="isLoading"
        @click="handleAbort"
        class="abort-btn"
        title="中断当前请求"
      >
        <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8 7a1 1 0 00-1 1v4a1 1 0 001 1h4a1 1 0 001-1V8a1 1 0 00-1-1H8z" clip-rule="evenodd"/>
        </svg>
      </button>

      <!-- 发送按钮 -->
      <button
        v-else
        @click="handleSend"
        :disabled="!canSend"
        class="send-btn"
      >
        <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
          <path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5-1.429A1 1 0 009 15.571V11a1 1 0 112 0v4.571a1 1 0 00.725.962l5 1.428a1 1 0 001.17-1.408l-7-14z"/>
        </svg>
      </button>
    </div>


    <!-- Tool Config Modal -->
    <ToolConfig v-model:visible="showToolConfig" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import ToolConfig from './ToolConfig.vue'

interface QuickAction {
  icon: string
  label: string
  text: string
}

interface Props {
  modelValue: string
  isLoading: boolean
  placeholder?: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'send', message: string): void
  (e: 'quick-action', message: string): void
  (e: 'abort'): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: 'Ask me to create files, read content, or manage your project...'
})

const emit = defineEmits<Emits>()

// Refs
const textareaRef = ref<HTMLTextAreaElement>()
const showToolConfig = ref(false)

// Computed
const inputValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const canSend = computed(() => {
  return inputValue.value.trim().length > 0 && !props.isLoading
})

// Quick Actions
const quickActions: QuickAction[] = []

// Methods
const handleSend = () => {
  if (canSend.value) {
    emit('send', inputValue.value.trim())
    inputValue.value = ''
    adjustHeight()
  }
}

const handleAbort = () => {
  emit('abort')
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter') {
    if (event.ctrlKey || event.metaKey) {
      // Ctrl+Enter 或 Cmd+Enter 发送消息
      event.preventDefault()
      handleSend()
    } else if (!event.shiftKey) {
      // Enter 发送消息（除非按住 Shift）
      event.preventDefault()
      handleSend()
    }
    // Shift+Enter 允许换行，不做任何处理
  }
}

const adjustHeight = async () => {
  await nextTick()
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    const scrollHeight = textareaRef.value.scrollHeight
    const maxHeight = 120 // 最大高度
    textareaRef.value.style.height = Math.min(scrollHeight, maxHeight) + 'px'
  }
}

const openToolConfig = () => {
  showToolConfig.value = true
}

// Lifecycle
onMounted(() => {
  adjustHeight()
  if (textareaRef.value) {
    textareaRef.value.focus()
  }
})
</script>

<style scoped>
.chat-input-container {
  width: 100%;
}

.quick-action-btn {
  @apply px-3 py-1.5 text-sm bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed;
}

.input-wrapper {
  position: relative;
}

.input-textarea {
  @apply w-full px-4 py-3 pr-12 border border-gray-300 dark:border-gray-600 rounded-lg resize-none bg-white dark:bg-gray-800 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed;
  min-height: 44px;
  max-height: 120px;
  line-height: 1.5;
}

.char-count {
  @apply absolute bottom-2 right-14 text-xs text-gray-400 dark:text-gray-500;
}

.send-btn {
  @apply flex items-center justify-center w-10 h-10 bg-blue-500 hover:bg-blue-600 disabled:bg-gray-400 text-white rounded-lg transition-colors disabled:cursor-not-allowed;
}

.send-btn.sending {
  @apply bg-blue-400;
}

.abort-btn {
  @apply flex items-center justify-center w-10 h-10 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors;
}

.tool-config-btn {
  @apply px-3 py-1 text-sm bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed;
}

/* 动画效果 */
.quick-action-btn {
  animation: fadeInUp 0.3s ease-out;
}

.quick-action-btn:nth-child(1) { animation-delay: 0.1s; }
.quick-action-btn:nth-child(2) { animation-delay: 0.2s; }
.quick-action-btn:nth-child(3) { animation-delay: 0.3s; }
.quick-action-btn:nth-child(4) { animation-delay: 0.4s; }
.quick-action-btn:nth-child(5) { animation-delay: 0.5s; }
.quick-action-btn:nth-child(6) { animation-delay: 0.6s; }

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 滚动条样式 */
.input-textarea::-webkit-scrollbar {
  width: 4px;
}

.input-textarea::-webkit-scrollbar-track {
  background: transparent;
}

.input-textarea::-webkit-scrollbar-thumb {
  background: #cbd5e0;
  border-radius: 2px;
}

.dark .input-textarea::-webkit-scrollbar-thumb {
  background: #4a5568;
}
</style>
