<template>
  <div class="task-view">
    <a-layout style="height: 100vh;">
      <!-- 头部 -->
      <a-layout-header class="header">
        <div class="header-content">
          <h1 class="title">
            <CodeOutlined />
            AI编码助手
          </h1>
          <div class="header-actions">
            <a-badge :count="activeTasks.length" :offset="[10, 0]">
              <a-button type="text" @click="showActiveTasksModal = true">
                <UnorderedListOutlined />
                活跃任务
              </a-button>
            </a-badge>
          </div>
        </div>
      </a-layout-header>

      <a-layout>
        <!-- 侧边栏 -->
        <a-layout-sider
          v-model:collapsed="collapsed"
          :trigger="null"
          collapsible
          width="300"
          style="background: #fff; border-right: 1px solid #f0f0f0;"
        >
          <div class="sider-content">
            <div class="sider-header">
              <a-button
                type="text"
                @click="collapsed = !collapsed"
                style="margin-bottom: 16px;"
              >
                <MenuUnfoldOutlined v-if="collapsed" />
                <MenuFoldOutlined v-else />
              </a-button>
              <h3 v-if="!collapsed">任务历史</h3>
            </div>

            <TaskHistory
              v-if="!collapsed"
              :tasks="taskHistory"
              @select-task="selectTask"
            />
          </div>
        </a-layout-sider>

        <!-- 主内容区 -->
        <a-layout-content class="main-content">
          <div class="content-wrapper">
            <!-- 任务输入区 -->
            <div class="task-input-section">
              <TaskInput
                :loading="isCreatingTask"
                @submit="handleTaskSubmit"
                @task-created="handleTaskCreated"
              />
            </div>

            <!-- 任务执行区 -->
            <div class="task-execution-section" v-if="currentTask">
              <TaskExecution
                :task="currentTask"
                :sse-connection="sseConnection"
                @retry="handleRetryStep"
              />
            </div>

            <!-- 空状态 -->
            <div class="empty-state" v-else>
              <a-empty
                description="请输入您的编码需求，AI助手将为您分步完成任务"
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
              >
                <template #image>
                  <CodeOutlined style="font-size: 64px; color: #d9d9d9;" />
                </template>
              </a-empty>
            </div>
          </div>
        </a-layout-content>
      </a-layout>
    </a-layout>

    <!-- 活跃任务模态框 -->
    <ActiveTasksModal
      v-model:visible="showActiveTasksModal"
      :tasks="activeTasks"
      @select-task="selectActiveTask"
    />
  </div>
</template>

<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {Empty} from 'ant-design-vue'
import {CodeOutlined, MenuFoldOutlined, MenuUnfoldOutlined, UnorderedListOutlined} from '@ant-design/icons-vue'
import TaskInput from '@/components/TaskInput.vue'
import TaskExecution from '@/components/TaskExecution.vue'
import TaskHistory from '@/components/TaskHistory.vue'
import ActiveTasksModal from '@/components/ActiveTasksModal.vue'
import {useTaskStore} from '@/stores/task'
import type {TaskPlan} from '@/types/task'

const taskStore = useTaskStore()

// 响应式数据
const collapsed = ref(false)
const showActiveTasksModal = ref(false)
const isCreatingTask = ref(false)
const currentTask = ref<TaskPlan | null>(null)
const sseConnection = ref<EventSource | null>(null)
const taskHistory = ref<TaskPlan[]>([])
const activeTasks = ref<TaskPlan[]>([])

// 处理任务提交
const handleTaskSubmit = async (query: string) => {
  isCreatingTask.value = true
}

// 处理任务创建完成
const handleTaskCreated = async (taskId: string) => {
  try {
    // 创建SSE连接
    const eventSource = new EventSource(`/api/task/stream/${taskId}?clientId=web-client`)
    sseConnection.value = eventSource

    // 监听任务更新
    eventSource.addEventListener('taskUpdate', (event) => {
      console.log('收到taskUpdate事件:', event.data)
      const data = JSON.parse(event.data)
      if (data.taskPlan) {
        currentTask.value = data.taskPlan
        updateTaskInHistory(data.taskPlan)
      }
    })

    eventSource.addEventListener('taskStatusUpdate', (event) => {
      console.log('收到taskStatusUpdate事件:', event.data)
      const data = JSON.parse(event.data)
      if (data.taskPlan) {
        currentTask.value = data.taskPlan
        updateTaskInHistory(data.taskPlan)
      }
    })

    // 监听步骤流式更新
    eventSource.addEventListener('stepChunk', (event) => {
      console.log('收到stepChunk事件:', event.data)
      // 这个事件会传递给TaskExecution组件处理
    })

    // 错误处理
    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error)
      eventSource.close()
      sseConnection.value = null
      isCreatingTask.value = false
    }

    // 获取初始任务状态
    const taskStatus = await taskStore.getTaskStatus(taskId)
    if (taskStatus) {
      currentTask.value = taskStatus
      addToTaskHistory(taskStatus)
    }

  } catch (error) {
    console.error('处理任务创建失败:', error)
  } finally {
    isCreatingTask.value = false
  }
}


// 处理重试步骤
const handleRetryStep = async (taskId: string, stepIndex: number) => {
  try {
    await taskStore.retryStep(taskId, stepIndex)
  } catch (error) {
    console.error('重试步骤失败:', error)
  }
}

// 选择任务
const selectTask = (task: TaskPlan) => {
  currentTask.value = task

  // 如果任务还在执行中，创建SSE连接
  if (task.planStatus === 'processing' || task.planStatus === 'planning') {
    if (sseConnection.value) {
      sseConnection.value.close()
    }

    const eventSource = new EventSource(`/api/task/stream/${task.taskId}?clientId=web-client`)
    sseConnection.value = eventSource

    eventSource.addEventListener('taskUpdate', (event) => {
      const data = JSON.parse(event.data)
      if (data.taskPlan) {
        currentTask.value = data.taskPlan
        updateTaskInHistory(data.taskPlan)
      }
    })

    // 添加监听任务状态更新事件
    eventSource.addEventListener('taskStatusUpdate', (event) => {
      const data = JSON.parse(event.data)
      if (data.taskPlan) {
        currentTask.value = data.taskPlan
        updateTaskInHistory(data.taskPlan)
      }
    })

    // 添加监听步骤流式内容更新事件
    eventSource.addEventListener('stepChunk', (event) => {
      // 这个事件会由TaskExecution组件处理，不需要在这里处理
      // 但需要确保事件能够传递到TaskExecution组件
      console.log('收到stepChunk事件:', event.data)
    })

    // 添加错误处理
    eventSource.onerror = (error) => {
      console.error('SSE连接错误:', error)
      eventSource.close()
      sseConnection.value = null
    }
  }
}

// 选择活跃任务
const selectActiveTask = (task: TaskPlan) => {
  selectTask(task)
  showActiveTasksModal.value = false
}

// 添加到任务历史
const addToTaskHistory = (task: TaskPlan) => {
  const existingIndex = taskHistory.value.findIndex(t => t.taskId === task.taskId)
  if (existingIndex === -1) {
    taskHistory.value.unshift(task)
  }
}

// 更新任务历史中的任务
const updateTaskInHistory = (task: TaskPlan) => {
  console.log('更新任务历史:', task)
  const index = taskHistory.value.findIndex(t => t.taskId === task.taskId)
  if (index !== -1) {
    taskHistory.value[index] = JSON.parse(JSON.stringify(task))
  } else {
    addToTaskHistory(task)
  }
}

// 加载活跃任务
const loadActiveTasks = async () => {
  try {
    const response = await taskStore.getActiveTasks()
    activeTasks.value = Object.values(response.activeTasks || {})
  } catch (error) {
    console.error('加载活跃任务失败:', error)
  }
}

// 组件挂载时加载活跃任务
onMounted(() => {
  loadActiveTasks()

  // 定期刷新活跃任务
  const interval = setInterval(loadActiveTasks, 30000) // 30秒刷新一次

  onUnmounted(() => {
    clearInterval(interval)
    if (sseConnection.value) {
      sseConnection.value.close()
    }
  })
})
</script>

<style scoped>
.task-view {
  height: 100vh;
}

.header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 24px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
}

.title {
  margin: 0;
  color: #1890ff;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sider-content {
  padding: 16px;
  height: 100%;
}

.sider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.main-content {
  background: #f5f5f5;
}

.content-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
  gap: 24px;
}

.task-input-section {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.task-execution-section {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>

// 添加处理函数
const handleTaskUpdate = (task: TaskPlan) => {
  currentTask.value = task
  updateTaskInHistory(task)
}
