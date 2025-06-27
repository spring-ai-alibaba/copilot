// 任务步骤接口
export interface TaskStep {
  stepIndex: number
  stepRequirement: string
  toolName: string
  result?: string
  status: 'pending' | 'waiting' | 'executing' | 'completed' | 'failed'
  startTime?: number
  endTime?: number
}

// 任务计划接口
export interface TaskPlan {
  taskId: string
  title: string
  description: string
  steps: TaskStep[]
  planStatus: 'planning' | 'processing' | 'completed' | 'failed' | 'cancelled'
  extraParams?: string
}

// API响应接口
export interface ApiResponse<T = any> {
  status: 'success' | 'error'
  message?: string
  data?: T
}

// 任务创建响应
export interface CreateTaskResponse {
  taskId: string
  status: string
  message: string
}

// 活跃任务响应
export interface ActiveTasksResponse {
  status: string
  activeTasks: Record<string, TaskPlan>
  count: number
}
