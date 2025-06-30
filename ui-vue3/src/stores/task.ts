import {defineStore} from 'pinia'
import axios from 'axios'
import type {ActiveTasksResponse, CreateTaskResponse, TaskPlan} from '@/types/task'

// 配置axios
const api = axios.create({
  baseURL: '/api/task',
  timeout: 30000
})

// 响应拦截器
api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API请求失败:', error)
    throw error
  }
)

export const useTaskStore = defineStore('task', {
  state: () => ({
    currentTask: null as TaskPlan | null,
    taskHistory: [] as TaskPlan[],
    activeTasks: {} as Record<string, TaskPlan>
  }),

  actions: {
    /**
     * 创建任务
     */
    async createTask(query: string): Promise<CreateTaskResponse> {
      try {
        const response = await api.post<any, CreateTaskResponse>('/create', { query })
        return response
      } catch (error) {
        console.error('创建任务失败:', error)
        throw error
      }
    },



    /**
     * 取消任务
     */
    async cancelTask(taskId: string): Promise<boolean> {
      try {
        await api.post(`/cancel/${taskId}`)
        return true
      } catch (error) {
        console.error('取消任务失败:', error)
        return false
      }
    },

    /**
     * 重试失败的步骤
     */
    async retryStep(taskId: string, stepIndex: number): Promise<boolean> {
      try {
        await api.post(`/retry/${taskId}/${stepIndex}`)
        return true
      } catch (error) {
        console.error('重试步骤失败:', error)
        return false
      }
    },

    /**
     * 手动触发下一步
     */
    async triggerNextStep(taskId: string, stepResult: string): Promise<TaskPlan | null> {
      try {
        const response = await api.post<any, { taskPlan: TaskPlan }>(`/next-step/${taskId}`, { stepResult })
        return response.taskPlan
      } catch (error) {
        console.error('触发下一步失败:', error)
        return null
      }
    },

    /**
     * 获取活跃任务
     */
    async getActiveTasks(): Promise<ActiveTasksResponse> {
      try {
        const response = await api.get<any, ActiveTasksResponse>('/active')
        this.activeTasks = response.activeTasks || {}
        return response
      } catch (error) {
        console.error('获取活跃任务失败:', error)
        return {
          status: 'error',
          activeTasks: {},
          count: 0
        }
      }
    },

    /**
     * 更新当前任务
     */
    updateCurrentTask(task: TaskPlan) {
      this.currentTask = task

      // 更新任务历史
      const existingIndex = this.taskHistory.findIndex(t => t.taskId === task.taskId)
      if (existingIndex !== -1) {
        this.taskHistory[existingIndex] = task
      } else {
        this.taskHistory.unshift(task)
      }

      // 限制历史记录数量
      if (this.taskHistory.length > 50) {
        this.taskHistory = this.taskHistory.slice(0, 50)
      }
    },

    /**
     * 清除当前任务
     */
    clearCurrentTask() {
      this.currentTask = null
    }
  }
})
