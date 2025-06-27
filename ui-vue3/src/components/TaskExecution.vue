<template>
  <div class="task-execution">
    <!-- 任务头部信息 -->
    <div class="task-header">
      <div class="task-info">
        <h3>{{ task.title }}</h3>
        <p class="task-description">{{ task.description }}</p>
        <div class="task-meta">
          <a-tag :color="getStatusColor(task.planStatus)">
            {{ getStatusText(task.planStatus) }}
          </a-tag>
          <span class="task-id">任务ID: {{ task.taskId }}</span>
        </div>
      </div>

      <div class="task-actions">
        <a-button
          v-if="task.planStatus === 'processing' || task.planStatus === 'planning'"
          danger
          @click="handleCancel"
        >
          <StopOutlined />
          取消任务
        </a-button>
      </div>
    </div>

    <!-- 执行进度 -->
    <div class="execution-progress">
      <div class="progress-header">
        <h4>执行进度</h4>
        <span class="step-count">{{ completedSteps }}/{{ totalSteps }} 步骤</span>
      </div>

      <a-progress
        :percent="progressPercent"
        :status="getProgressStatus()"
        :stroke-color="getProgressColor()"
      />

      <!-- 当前执行步骤提示 -->
      <div v-if="currentExecutingStep" class="current-step-info">
        <a-spin size="small" />
        <span>正在执行: 步骤{{ currentExecutingStep.stepIndex }} - {{ currentExecutingStep.stepRequirement }}</span>
      </div>
    </div>

    <!-- 步骤列表 -->
    <div class="steps-container">
      <h4>执行步骤</h4>

      <div class="steps-list">
        <div
          v-for="(step) in task.steps"
          :key="step.stepIndex"
          :data-step-index="step.stepIndex"
          class="step-item"
          :class="getStepClass(step)"
        >
          <div class="step-header">
            <div class="step-info">
              <div class="step-icon">
                <LoadingOutlined v-if="step.status === 'executing'" spin />
                <CheckCircleOutlined v-else-if="step.status === 'completed'" />
                <ExclamationCircleOutlined v-else-if="step.status === 'failed'" />
                <ClockCircleOutlined v-else-if="step.status === 'waiting'" class="waiting-icon" />
                <ClockCircleOutlined v-else />
              </div>

              <div class="step-content">
                <div class="step-title">
                  步骤 {{ step.stepIndex }}: {{ step.stepRequirement }}
                </div>
                <div class="step-meta">
                  <a-tag size="small">{{ step.toolName }}</a-tag>
                  <span class="step-status">{{ getStepStatusText(step.status) }}</span>
                  <span v-if="step.startTime" class="step-time">
                    {{ formatTime(step.startTime) }}
                  </span>
                </div>
              </div>
            </div>

            <div class="step-actions">
              <a-button
                v-if="step.status === 'failed'"
                size="small"
                type="primary"
                @click="handleRetry(step.stepIndex)"
              >
                <RedoOutlined />
                重试
              </a-button>

              <a-button
                size="small"
                type="text"
                @click="toggleStepDetail(step.stepIndex)"
                :disabled="step.status === 'pending'"
              >
                <DownOutlined v-if="expandedSteps.includes(step.stepIndex)" />
                <RightOutlined v-else />
              </a-button>
            </div>
          </div>

          <!-- 步骤详情 - 自动展开执行中的步骤 -->
          <div
            v-if="expandedSteps.includes(step.stepIndex) || step.status === 'executing'"
            class="step-detail"
          >
            <!-- 实时流式内容 -->
            <div v-if="step.status === 'executing' && streamingContent[step.stepIndex]" class="step-streaming">
              <StreamingOutput
                :content="streamingContent[step.stepIndex]"
                :is-streaming="step.status === 'executing'"
                :title="`步骤 ${step.stepIndex} 实时输出`"
                :start-time="step.startTime"
                :allow-clear="false"
                :auto-scroll="true"
              />
            </div>

            <!-- 等待执行提示 -->
            <div v-else-if="step.status === 'waiting'" class="step-waiting">
              <a-empty
                description="步骤准备执行中，请稍候..."
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
              >
                <template #image>
                  <ClockCircleOutlined style="font-size: 48px; color: #faad14;" />
                </template>
              </a-empty>
            </div>

            <!-- 最终结果 -->
            <div v-else-if="step.result && step.status !== 'executing'" class="step-result">
              <h5>执行结果:</h5>
              <pre class="result-content">{{ step.result }}</pre>
            </div>

            <!-- 错误信息 -->
            <div v-else-if="step.status === 'failed'" class="step-error">
              <a-alert
                message="步骤执行失败"
                :description="step.error || '执行过程中发生未知错误'"
                type="error"
                show-icon
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时日志 -->
    <div v-if="task.planStatus === 'processing'" class="live-log">
      <h4>实时日志</h4>
      <div class="log-container" ref="logContainer">
        <div
          v-for="(log, index) in logs"
          :key="index"
          class="log-item"
          :class="log.type"
        >
          <span class="log-time">{{ formatTime(log.timestamp) }}</span>
          <span class="log-content">{{ log.message }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue'
import {Empty} from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DownOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  RedoOutlined,
  RightOutlined,
  StopOutlined
} from '@ant-design/icons-vue'
import StreamingOutput from './StreamingOutput.vue'
import type {TaskPlan, TaskStep} from '@/types/task'

interface LogItem {
  timestamp: number
  message: string
  type: 'info' | 'success' | 'warning' | 'error'
}

const props = defineProps<{
  task: TaskPlan
  sseConnection?: EventSource | null
}>()

const emit = defineEmits<{
  cancel: [taskId: string]
  retry: [taskId: string, stepIndex: number]
}>()

const expandedSteps = ref<number[]>([])
const logs = ref<LogItem[]>([])
const logContainer = ref<HTMLElement>()
const streamingContent = ref<Record<number, string>>({})
const streamingContainer = ref<HTMLElement>()

// 计算属性
const totalSteps = computed(() => props.task.steps.length)
const completedSteps = computed(() =>
  props.task.steps.filter(step => step.status === 'completed').length
)
const progressPercent = computed(() => {
  if (totalSteps.value === 0) return 0
  return Math.round((completedSteps.value / totalSteps.value) * 100)
})

// 当前执行的步骤
const currentExecutingStep = computed(() =>
  props.task.steps.find(step => step.status === 'executing')
)

// 监听SSE连接
watch(() => props.sseConnection, (newConnection) => {
  if (newConnection) {
    // 监听任务更新
    newConnection.addEventListener('taskUpdate', (event) => {
      const data = JSON.parse(event.data)
      addLog('info', `任务状态更新: ${data.message || '正在执行...'}`)
    })

    // 监听步骤流式内容更新
    newConnection.addEventListener('stepChunk', (event) => {
      try {
        const data = JSON.parse(event.data)
        const { stepIndex, chunk, isComplete } = data

        console.log('收到stepChunk事件:', { stepIndex, chunk: chunk?.substring(0, 100), isComplete })

        // 自动展开正在执行的步骤
        if (!expandedSteps.value.includes(stepIndex)) {
          expandedSteps.value.push(stepIndex)
        }

        if (isComplete) {
          // 步骤完成，保留内容但停止流式更新
          addLog('success', `步骤 ${stepIndex} 执行完成`)
          console.log(`步骤 ${stepIndex} 流式输出完成`)
        } else {
          // 使用响应式更新方式追加流式内容
          const currentContent = streamingContent.value[stepIndex] || ''
          const newContent = currentContent + chunk

          // 重新赋值整个对象以确保响应式更新
          streamingContent.value = {
            ...streamingContent.value,
            [stepIndex]: newContent
          }

          console.log(`步骤 ${stepIndex} 内容更新，当前长度: ${newContent.length}`)

          // 自动滚动到当前执行的步骤
          nextTick(() => {
            const stepElement = document.querySelector(`[data-step-index="${stepIndex}"]`)
            if (stepElement) {
              stepElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
            }
          })
        }
      } catch (error) {
        console.error('解析stepChunk数据失败:', error)
      }
    })

    // 监听任务状态更新
    newConnection.addEventListener('taskStatusUpdate', (event) => {
      const data = JSON.parse(event.data)
      addLog('success', `任务完成: ${data.summary || '任务执行完成'}`)
    })
  }
})

// 监听任务步骤变化，自动展开新的执行步骤
watch(() => props.task.steps, (newSteps, oldSteps) => {
  if (newSteps && oldSteps) {
    newSteps.forEach(step => {
      // 如果步骤开始执行，自动展开
      if (step.status === 'executing' && !expandedSteps.value.includes(step.stepIndex)) {
        expandedSteps.value.push(step.stepIndex)
        addLog('info', `开始执行步骤 ${step.stepIndex}: ${step.stepRequirement}`)
      }

      // 如果步骤完成，添加日志
      if (step.status === 'completed') {
        const oldStep = oldSteps.find(s => s.stepIndex === step.stepIndex)
        if (oldStep && oldStep.status !== 'completed') {
          addLog('success', `步骤 ${step.stepIndex} 执行完成`)
        }
      }

      // 如果步骤失败，添加日志并展开
      if (step.status === 'failed') {
        const oldStep = oldSteps.find(s => s.stepIndex === step.stepIndex)
        if (oldStep && oldStep.status !== 'failed') {
          addLog('error', `步骤 ${step.stepIndex} 执行失败`)
          if (!expandedSteps.value.includes(step.stepIndex)) {
            expandedSteps.value.push(step.stepIndex)
          }
        }
      }
    })
  }
}, { deep: true })

// 获取状态颜色
const getStatusColor = (status: string) => {
  const colors = {
    'planning': 'blue',
    'processing': 'orange',
    'completed': 'green',
    'failed': 'red',
    'cancelled': 'default'
  }
  return colors[status as keyof typeof colors] || 'default'
}

// 获取状态文本
const getStatusText = (status: string) => {
  const texts = {
    'planning': '规划中',
    'processing': '执行中',
    'completed': '已完成',
    'failed': '执行失败',
    'cancelled': '已取消'
  }
  return texts[status as keyof typeof texts] || status
}

// 获取进度状态
const getProgressStatus = () => {
  if (props.task.planStatus === 'failed') return 'exception'
  if (props.task.planStatus === 'completed') return 'success'
  return 'active'
}

// 获取进度颜色
const getProgressColor = () => {
  if (props.task.planStatus === 'failed') return '#ff4d4f'
  if (props.task.planStatus === 'completed') return '#52c41a'
  return '#1890ff'
}

// 获取步骤样式类
const getStepClass = (step: TaskStep) => {
  return {
    'step-pending': step.status === 'pending',
    'step-waiting': step.status === 'waiting',
    'step-executing': step.status === 'executing',
    'step-completed': step.status === 'completed',
    'step-failed': step.status === 'failed',
    'step-auto-expanded': step.status === 'executing' // 自动展开执行中的步骤
  }
}

// 获取步骤状态文本
const getStepStatusText = (status: string) => {
  const texts = {
    'pending': '等待中',
    'waiting': '准备执行',
    'executing': '执行中',
    'completed': '已完成',
    'failed': '执行失败'
  }
  return texts[status as keyof typeof texts] || status
}

// 格式化时间
const formatTime = (timestamp: number) => {
  return new Date(timestamp).toLocaleTimeString()
}

// 切换步骤详情
const toggleStepDetail = (stepIndex: number) => {
  const index = expandedSteps.value.indexOf(stepIndex)
  if (index > -1) {
    expandedSteps.value.splice(index, 1)
  } else {
    expandedSteps.value.push(stepIndex)
  }
}

// 处理取消
const handleCancel = () => {
  emit('cancel', props.task.taskId)
}

// 处理重试
const handleRetry = (stepIndex: number) => {
  emit('retry', props.task.taskId, stepIndex)
}

// 添加日志
const addLog = (type: LogItem['type'], message: string) => {
  logs.value.push({
    timestamp: Date.now(),
    message,
    type
  })

  // 自动滚动到底部
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.task-execution {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.task-info h3 {
  margin: 0 0 8px 0;
  color: #262626;
}

.task-description {
  margin: 0 0 12px 0;
  color: #8c8c8c;
  line-height: 1.5;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-id {
  font-size: 12px;
  color: #bfbfbf;
}

.execution-progress {
  background: #fafafa;
  padding: 16px;
  border-radius: 6px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.progress-header h4 {
  margin: 0;
  color: #262626;
}

.step-count {
  font-size: 14px;
  color: #8c8c8c;
}

.steps-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.steps-container h4 {
  margin: 0 0 16px 0;
  color: #262626;
}

.steps-list {
  flex: 1;
  overflow-y: auto;
}

.step-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 12px;
  transition: all 0.3s;
}

.step-item.step-waiting {
  border-color: #faad14;
  box-shadow: 0 0 0 2px rgba(250, 173, 20, 0.1);
  background-color: #fffbe6;
}

.step-item.step-executing {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.step-item.step-completed {
  border-color: #52c41a;
}

.step-item.step-failed {
  border-color: #ff4d4f;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
}

.step-info {
  display: flex;
  gap: 12px;
  flex: 1;
}

.step-icon {
  font-size: 16px;
  margin-top: 2px;
}

.waiting-icon {
  color: #faad14;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.step-content {
  flex: 1;
}

.step-title {
  font-weight: 500;
  color: #262626;
  margin-bottom: 8px;
  line-height: 1.4;
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.step-actions {
  display: flex;
  gap: 8px;
}

/* 步骤详情区域 - 增加高度和改善布局 */
.step-detail {
  border-top: 1px solid #f0f0f0;
  padding: 20px;
  background: #fafafa;
  min-height: 300px; /* 增加最小高度 */
}

/* 步骤结果内容 - 增加显示高度 */
.result-content {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 16px;
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  min-height: 250px; /* 增加最小高度 */
  max-height: 500px; /* 增加最大高度 */
  overflow-y: auto;
}

/* 流式内容样式 - 扩大显示区域 */
.step-streaming {
  margin-top: 16px;
  margin-bottom: 20px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  min-height: 350px; /* 增加最小高度 */
  background: #fff;
}

/* 步骤容器 - 优化整体布局 */
.steps-container {
  margin-top: 24px;
  width: 100%;
}

.steps-list {
  width: 100%;
}

/* 步骤项 - 增加内边距和高度 */
.step-item {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  margin-bottom: 16px;
  transition: all 0.3s;
  width: 100%;
}

/* 步骤头部 - 增加内边距 */
.step-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px;
}

/* 步骤等待状态 - 增加高度 */
.step-waiting {
  padding: 40px;
  text-align: center;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

/* 任务执行容器 - 确保全宽度利用 */
.task-execution {
  width: 100%;
  max-width: none;
  padding: 0;
}

/* 实时日志容器 - 增加高度 */
.log-container {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 16px;
  min-height: 300px;
  max-height: 600px; /* 增加最大高度 */
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

/* 日志项 - 优化显示 */
.log-item {
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  display: flex;
  gap: 12px;
}

.log-item:last-child {
  border-bottom: none;
}

/* 优化StreamingOutput组件的显示 */
.step-streaming :deep(.streaming-output) {
  min-height: 300px;
}

.step-streaming :deep(.streaming-content) {
  min-height: 250px;
  max-height: 450px;
  font-size: 13px;
  line-height: 1.6;
  padding: 16px;
}

/* 响应式设计 - 在大屏幕上更好地利用空间 */
@media (min-width: 1200px) {
  .step-detail {
    min-height: 400px;
  }

  .result-content {
    min-height: 350px;
    max-height: 700px;
  }

  .step-streaming {
    min-height: 450px;
  }

  .log-container {
    min-height: 400px;
    max-height: 800px;
  }
}
</style>


