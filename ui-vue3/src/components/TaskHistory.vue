<template>
  <div class="task-history">
    <div class="history-header">
      <h4>任务历史</h4>
      <a-button
        type="text"
        size="small"
        @click="clearHistory"
        :disabled="tasks.length === 0"
      >
        <DeleteOutlined />
        清空
      </a-button>
    </div>

    <div class="history-list">
      <div
        v-for="task in tasks"
        :key="task.taskId"
        class="history-item"
        @click="selectTask(task)"
      >
        <div class="task-info">
          <div class="task-title">{{ task.title }}</div>
          <div class="task-meta">
            <a-tag size="small" :color="getStatusColor(task.planStatus)">
              {{ getStatusText(task.planStatus) }}
            </a-tag>
            <span class="step-count">1 步骤</span>
          </div>
        </div>

        <div class="task-actions">
          <a-button
            type="text"
            size="small"
            @click.stop="deleteTask(task.taskId)"
          >
            <DeleteOutlined />
          </a-button>
        </div>
      </div>

      <div v-if="tasks.length === 0" class="empty-history">
        <a-empty
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          description="暂无任务历史"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Empty} from 'ant-design-vue'
import {DeleteOutlined} from '@ant-design/icons-vue'
import type {TaskPlan} from '@/types/task'

const props = defineProps<{
  tasks: TaskPlan[]
}>()

const emit = defineEmits<{
  selectTask: [task: TaskPlan]
  deleteTask: [taskId: string]
  clearHistory: []
}>()

// 选择任务
const selectTask = (task: TaskPlan) => {
  emit('selectTask', task)
}

// 删除任务
const deleteTask = (taskId: string) => {
  emit('deleteTask', taskId)
}

// 清空历史
const clearHistory = () => {
  emit('clearHistory')
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
    'failed': '失败',
    'cancelled': '已取消'
  }
  return texts[status as keyof typeof texts] || status
}
</script>

<style scoped>
.task-history {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.history-header h4 {
  margin: 0;
  color: #262626;
  font-size: 14px;
}

.history-list {
  flex: 1;
  overflow-y: auto;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.history-item:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.1);
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-title {
  font-size: 13px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-count {
  font-size: 11px;
  color: #8c8c8c;
}

.task-actions {
  flex-shrink: 0;
  margin-left: 8px;
}

.empty-history {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

:deep(.ant-empty-description) {
  font-size: 12px;
}
</style>
