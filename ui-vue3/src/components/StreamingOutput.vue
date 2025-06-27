<template>
  <div class="streaming-output">
    <div class="streaming-header">
      <h5>{{ title }}</h5>
      <div class="streaming-controls">
        <a-button
          v-if="allowCopy"
          size="small"
          type="text"
          @click="copyContent"
        >
          <CopyOutlined />
        </a-button>
        <a-button
          v-if="allowClear"
          size="small"
          type="text"
          @click="clearContent"
        >
          <ClearOutlined />
        </a-button>
      </div>
    </div>

    <div
      ref="contentContainer"
      class="streaming-container"
      :class="{ 'auto-scroll': autoScroll }"
    >
      <pre class="streaming-text">{{ content }}</pre>
      <div v-if="isStreaming" class="streaming-cursor">|</div>
    </div>

    <div v-if="showStats" class="streaming-stats">
      <span>字符数: {{ content.length }}</span>
      <span v-if="startTime">耗时: {{ formatDuration(Date.now() - startTime) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {nextTick, ref, watch} from 'vue'
import {ClearOutlined, CopyOutlined} from '@ant-design/icons-vue'
import {message} from 'ant-design-vue'

interface Props {
  content: string
  isStreaming?: boolean
  title?: string
  autoScroll?: boolean
  allowCopy?: boolean
  allowClear?: boolean
  showStats?: boolean
  startTime?: number
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false,
  title: '实时输出',
  autoScroll: true,
  allowCopy: true,
  allowClear: false,
  showStats: true
})

const emit = defineEmits<{
  clear: []
}>()

const contentContainer = ref<HTMLElement>()

// 监听内容变化，自动滚动到底部
watch(() => props.content, () => {
  if (props.autoScroll) {
    nextTick(() => {
      if (contentContainer.value) {
        contentContainer.value.scrollTop = contentContainer.value.scrollHeight
      }
    })
  }
})

// 复制内容
const copyContent = async () => {
  try {
    await navigator.clipboard.writeText(props.content)
    message.success('内容已复制到剪贴板')
  } catch (error) {
    message.error('复制失败')
  }
}

// 清空内容
const clearContent = () => {
  emit('clear')
}

// 格式化持续时间
const formatDuration = (ms: number) => {
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}:${(minutes % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`
  } else if (minutes > 0) {
    return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`
  } else {
    return `${seconds}s`
  }
}
</script>

<style scoped>
.streaming-output {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
}

.streaming-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.streaming-header h5 {
  margin: 0;
  font-size: 14px;
  color: #262626;
}

.streaming-controls {
  display: flex;
  gap: 4px;
}

.streaming-container {
  background: #001529;
  padding: 12px;
  position: relative;
  max-height: 600px; /* 增加最大高度 */
  overflow-y: auto;
  min-height: 100px;
}

/* 添加响应式高度 */
@media (max-height: 800px) {
  .streaming-container {
    max-height: 300px;
  }
}

@media (min-height: 1200px) {
  .streaming-container {
    max-height: 800px;
  }
}

.streaming-container.auto-scroll {
  scroll-behavior: smooth;
}

.streaming-text {
  color: #00ff00;
  font-family: 'Courier New', 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  line-height: 1.4;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.streaming-cursor {
  display: inline-block;
  color: #00ff00;
  font-family: 'Courier New', 'Monaco', 'Menlo', monospace;
  animation: blink 1s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.streaming-stats {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f5f5f5;
  font-size: 11px;
  color: #8c8c8c;
  border-top: 1px solid #e8e8e8;
}

/* 滚动条样式 */
.streaming-container::-webkit-scrollbar {
  width: 6px;
}

.streaming-container::-webkit-scrollbar-track {
  background: #1f1f1f;
}

.streaming-container::-webkit-scrollbar-thumb {
  background: #555;
  border-radius: 3px;
}

.streaming-container::-webkit-scrollbar-thumb:hover {
  background: #777;
}

</style>
