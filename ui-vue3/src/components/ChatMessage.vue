<template>
  <div class="message" :class="message.role">
    <div class="message-content" :class="messageContentClass">
      <div class="message-role text-xs font-semibold mb-2" :class="roleClass">
        {{ message.role === 'user' ? 'You' : 'Assistant' }}
      </div>
      <div class="message-text" v-html="formattedContent"></div>
      <div v-if="message.timestamp" class="message-timestamp text-xs opacity-70 mt-2">
        {{ formatTimestamp(message.timestamp) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp?: Date
}

interface Props {
  message: Message
}

const props = defineProps<Props>()

// Computed
const messageContentClass = computed(() => {
  if (props.message.role === 'user') {
    return 'bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-lg p-4 shadow-sm'
  } else {
    return 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-lg p-4 shadow-sm border border-gray-200 dark:border-gray-700'
  }
})

const roleClass = computed(() => {
  if (props.message.role === 'user') {
    return 'text-blue-100'
  } else {
    return 'text-gray-500 dark:text-gray-400'
  }
})

const formattedContent = computed(() => {
  return formatMessage(props.message.content)
})

// Methods
const formatMessage = (content: string): string => {
  // 处理代码块
  content = content.replace(/```(\w+)?\n([\s\S]*?)```/g, (match, lang, code) => {
    const language = lang || 'text'
    return `<div class="code-block">
      <div class="code-header">
        <span class="code-language">${language}</span>
        <button class="copy-btn" onclick="copyCode(this)">复制</button>
      </div>
      <pre class="code-content"><code class="language-${language}">${escapeHtml(code.trim())}</code></pre>
    </div>`
  })

  // 处理行内代码
  content = content.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

  // 处理换行
  content = content.replace(/\n/g, '<br>')

  // 处理链接
  content = content.replace(
    /(https?:\/\/[^\s]+)/g,
    '<a href="$1" target="_blank" class="text-blue-500 hover:text-blue-600 underline">$1</a>'
  )

  // 处理文件路径
  content = content.replace(
    /📁\s*([^\s<]+)/g,
    '<span class="file-path">📁 <code>$1</code></span>'
  )

  // 处理强调文本
  content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\*(.*?)\*/g, '<em>$1</em>')

  return content
}

const escapeHtml = (text: string): string => {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

const formatTimestamp = (timestamp: Date): string => {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(timestamp)
}

// 全局函数，用于复制代码
if (typeof window !== 'undefined') {
  (window as any).copyCode = (button: HTMLElement) => {
    const codeBlock = button.closest('.code-block')
    const codeContent = codeBlock?.querySelector('.code-content code')
    if (codeContent) {
      const text = codeContent.textContent || ''
      navigator.clipboard.writeText(text).then(() => {
        const originalText = button.textContent
        button.textContent = '已复制!'
        setTimeout(() => {
          button.textContent = originalText
        }, 2000)
      }).catch(err => {
        console.error('复制失败:', err)
      })
    }
  }
}
</script>

<style scoped>
.message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.message.user {
  justify-content: flex-end;
}

.message-content {
  max-width: 70%;
  word-wrap: break-word;
}

:deep(.code-block) {
  margin: 0.5rem 0;
  border-radius: 0.5rem;
  overflow: hidden;
  background: #1e1e1e;
  border: 1px solid #333;
}

:deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background: #2d2d2d;
  border-bottom: 1px solid #333;
}

:deep(.code-language) {
  color: #888;
  font-size: 0.75rem;
  text-transform: uppercase;
}

:deep(.copy-btn) {
  background: #4a5568;
  color: white;
  border: none;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

:deep(.copy-btn:hover) {
  background: #2d3748;
}

:deep(.code-content) {
  margin: 0;
  padding: 1rem;
  background: #1e1e1e;
  color: #e2e8f0;
  overflow-x: auto;
  font-family: 'Fira Code', 'Monaco', 'Consolas', monospace;
  font-size: 0.875rem;
  line-height: 1.5;
}

:deep(.inline-code) {
  background: rgba(0, 0, 0, 0.1);
  padding: 0.125rem 0.25rem;
  border-radius: 0.25rem;
  font-family: 'Fira Code', 'Monaco', 'Consolas', monospace;
  font-size: 0.875em;
}

.message.user :deep(.inline-code) {
  background: rgba(255, 255, 255, 0.2);
}

:deep(.file-path) {
  display: inline-flex;
  align-items: center;
  background: #f7fafc;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  border: 1px solid #e2e8f0;
  margin: 0.125rem 0;
}

.dark :deep(.file-path) {
  background: #2d3748;
  border-color: #4a5568;
  color: #e2e8f0;
}

:deep(.file-path code) {
  background: none;
  padding: 0;
  color: #2d3748;
}

.dark :deep(.file-path code) {
  color: #e2e8f0;
}

/* 动画效果 */
.message {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
