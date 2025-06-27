<template>
  <a-modal
    v-model:open="visible"
    title="活跃任务"
    :footer="null"
    width="800px"
  >
    <div class="active-tasks">
      <div v-if="tasks.length === 0" class="empty-tasks">
        <a-empty description="当前没有活跃任务" />
      </div>

      <div v-else class="tasks-list">
        <div
          v-for="task in tasks"
          :key="task.taskId"
          class="task-card"
        >
          <div class="task-header">
            <div class="task-info">
              <h4>{{ task.title }}</h4>
              <p class="task-description">{{ task.description }}</p>
            </div>

            <div class="task-status">
              <a-tag :color="getStatusColor(task.planStatus)">
                {{ getStatusText(task.planStatus) }}
              </a-tag>
            </div>
          </div>

          <div class="task-progress">
            <div class="progress-info">
              <span>进度: {{ getCompletedSteps(task) }}/{{ task.steps.length }} 步骤</span>
              <span class="task-id">ID: {{ task.taskId.slice(0, 8) }}...</span>
            </div>

            <a-progress
              :percent="getProgressPercent(task)"
              size="small"
              :status="getProgressStatus(task.planStatus)"
            />
          </div>

          <div class="task-actions">
            <a-button
              type="primary"
              size="small"
              @click="selectTask(task)"
            >
              <EyeOutlined />
              查看详情
            </a-button>

            <a-button
              v-if="task.planStatus === 'processing' || task.planStatus === 'planning'"
              danger
              size="small"
              @click="cancelTask(task)"
            >
              <StopOutlined />
              取消任务
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {EyeOutlined, StopOutlined} from '@ant-design/icons-vue'
import type {TaskPlan} from '@/types/task'

const props = defineProps<{
  visible: boolean
  tasks: TaskPlan[]
}>()

const emit = defineEmits<{
  'update:visible': [visible: boolean]
  selectTask: [task: TaskPlan]
  cancelTask: [taskId: string]
}>()

const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

// 选择任务
const selectTask = (task: TaskPlan) => {
  emit('selectTask', task)
}

// 取消任务
const cancelTask = (task: TaskPlan) => {
  emit('cancelTask', task.taskId)
}

// 获取已完成步骤数
const getCompletedSteps = (task: TaskPlan) => {
  return task.steps.filter(step => step.status === 'completed').length
}

// 获取进度百分比
const getProgressPercent = (task: TaskPlan) => {
  if (task.steps.length === 0) return 0
  return Math.round((getCompletedSteps(task) / task.steps.length) * 100)
}

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
const getProgressStatus = (status: string) => {
  if (status === 'failed') return 'exception'
  if (status === 'completed') return 'success'
  return 'active'
}
</script>

<style scoped>
.active-tasks {
  max-height: 600px;
  overflow-y: auto;
}

.empty-tasks {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.tasks-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s;
}

.task-card:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-info h4 {
  margin: 0 0 4px 0;
  color: #262626;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-description {
  margin: 0;
  color: #8c8c8c;
  font-size: 14px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-status {
  flex-shrink: 0;
  margin-left: 16px;
}

.task-progress {
  margin-bottom: 16px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.task-id {
  font-family: monospace;
}

.task-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
