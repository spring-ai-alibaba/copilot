<template>
  <div class="message" :class="message.role">
    <div class="message-content" :class="messageContentClass">
      <div class="message-role text-xs font-semibold mb-2" :class="roleClass">
        <span v-if="message.role === 'assistant'" class="w-2 h-2 bg-blue-500 rounded-full mr-2"></span>
        {{ message.role === 'user' ? 'You' : 'Assistant' }}
        <span v-if="message.role === 'user'" class="w-2 h-2 bg-blue-200 rounded-full ml-2"></span>
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
    return 'bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-600 text-white rounded-xl px-4 py-3 shadow-md max-w-[85%] ml-auto'
  } else {
    return 'bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-xl px-4 py-3 shadow-sm border border-gray-200 dark:border-gray-700 max-w-[90%]'
  }
})

const roleClass = computed(() => {
  if (props.message.role === 'user') {
    return 'text-blue-100 flex items-center justify-end'
  } else {
    return 'text-blue-600 dark:text-blue-400 flex items-center'
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

  // 处理文件路径 - 添加折叠功能
  content = content.replace(
    /📁\s*([^\s<]+)/g,
    (match, path) => {
      const truncatedPath = truncateFilePath(path)
      const needsTruncation = path !== truncatedPath

      if (needsTruncation) {
        return `<span class="file-path-container">
          <span class="file-path collapsible" title="${escapeHtml(path)}">
            📁 <code class="path-text">${escapeHtml(truncatedPath)}</code>
            <button class="expand-btn" onclick="togglePath(this)" title="展开完整路径">
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/>
              </svg>
            </button>
          </span>
          <span class="file-path full-path hidden" title="${escapeHtml(path)}">
            📁 <code class="path-text">${escapeHtml(path)}</code>
            <button class="collapse-btn" onclick="togglePath(this)" title="收起路径">
              <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M14.707 12.707a1 1 0 01-1.414 0L10 9.414l-3.293 3.293a1 1 0 01-1.414-1.414l4-4a1 1 0 011.414 0l4 4a1 1 0 010 1.414z" clip-rule="evenodd"/>
              </svg>
            </button>
          </span>
        </span>`
      } else {
        return `<span class="file-path">📁 <code>${escapeHtml(path)}</code></span>`
      }
    }
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

// 截断文件路径
const truncateFilePath = (path: string, maxLength: number = 40): string => {
  if (path.length <= maxLength) return path

  const parts = path.split('/')
  if (parts.length <= 2) return path

  // 保留第一个和最后一个部分，中间用...替代
  const first = parts[0]
  const last = parts[parts.length - 1]
  const middle = parts.slice(1, -1)

  let truncated = `${first}/.../${last}`

  // 如果还是太长，进一步截断最后一个部分
  if (truncated.length > maxLength && last.length > 15) {
    const truncatedLast = last.substring(0, 12) + '...'
    truncated = `${first}/.../${truncatedLast}`
  }

  return truncated
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

  // 全局函数，用于切换路径显示
  (window as any).togglePath = (button: HTMLElement) => {
    const container = button.closest('.file-path-container')
    if (container) {
      const collapsible = container.querySelector('.collapsible')
      const fullPath = container.querySelector('.full-path')

      if (collapsible && fullPath) {
        if (collapsible.classList.contains('hidden')) {
          // 当前显示完整路径，切换到折叠状态
          collapsible.classList.remove('hidden')
          fullPath.classList.add('hidden')
        } else {
          // 当前显示折叠路径，切换到完整状态
          collapsible.classList.add('hidden')
          fullPath.classList.remove('hidden')
        }
      }
    }
  }
}
</script>

<style scoped>
.message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 1.25rem;
  width: 100%;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.message-content {
  word-wrap: break-word;
  position: relative;
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

/* 文件路径容器 */
:deep(.file-path-container) {
  display: inline-block;
  margin: 0.125rem 0;
}

/* 基础文件路径样式 */
:deep(.file-path) {
  display: inline-flex;
  align-items: center;
  background: #f8fafc;
  padding: 0.375rem 0.75rem;
  border-radius: 0.5rem;
  border: 1px solid #e2e8f0;
  margin: 0.125rem 0;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

:deep(.file-path:hover) {
  background: #f1f5f9;
  border-color: #cbd5e1;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.dark :deep(.file-path) {
  background: #1e293b;
  border-color: #475569;
  color: #e2e8f0;
}

.dark :deep(.file-path:hover) {
  background: #334155;
  border-color: #64748b;
}

/* 路径文本样式 */
:deep(.path-text) {
  background: none !important;
  padding: 0 !important;
  color: #475569 !important;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 0.875rem;
  font-weight: 500;
}

.dark :deep(.path-text) {
  color: #cbd5e1 !important;
}

/* 展开/收起按钮样式 */
:deep(.expand-btn),
:deep(.collapse-btn) {
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 0.125rem;
  margin-left: 0.5rem;
  border-radius: 0.25rem;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

:deep(.expand-btn:hover),
:deep(.collapse-btn:hover) {
  background: #e2e8f0;
  color: #475569;
}

.dark :deep(.expand-btn:hover),
.dark :deep(.collapse-btn:hover) {
  background: #475569;
  color: #e2e8f0;
}

/* 隐藏状态 */
:deep(.hidden) {
  display: none !important;
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
