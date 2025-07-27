import { ref, reactive } from 'vue'
import axios from 'axios'

export interface FileChangeEvent {
  type: 'created' | 'modified' | 'deleted'
  filePath: string
  fileName: string
  fileExtension: string
  content?: string
  timestamp: string
  size?: number
}

export interface FileWatchCallbacks {
  onFileChange?: (event: FileChangeEvent) => void
  onConnect?: () => void
  onDisconnect?: () => void
  onError?: (error: any) => void
}

class FileWatchService {
  private pollingInterval: number | null = null
  private callbacks: FileWatchCallbacks = {}
  private isConnected = ref(false)
  private readonly baseUrl = 'http://localhost:8080/api/workspace'
  private lastFileList: any[] = []

  /**
   * 连接到文件监控服务 (简化轮询模式)
   */
  async connect(callbacks: FileWatchCallbacks = {}) {
    if (this.isConnected.value) {
      console.log('文件监控服务已连接')
      return
    }

    this.callbacks = callbacks

    console.log('🔗 启动文件监控服务 (简化轮询模式)')
    console.log('📡 API地址:', this.baseUrl)

    try {
      // 测试API连接 - 使用现有的文件列表API
      const response = await axios.get(`${this.baseUrl}/files`)
      console.log('✅ 文件API连接成功，文件数量:', response.data.length)

      // 初始化文件列表
      this.lastFileList = response.data || []

      this.isConnected.value = true
      this.callbacks.onConnect?.()

    } catch (error) {
      console.error('❌ 文件API连接失败:', error)
      this.callbacks.onError?.(error)
    }
  }

  /**
   * 处理文件变化事件
   */
  private handleFileChange(event: FileChangeEvent) {
    console.log('📁 文件变化:', event.type, event.filePath)

    // 调用回调函数，让fileStore处理文件变化
    this.callbacks.onFileChange?.(event)
  }



  /**
   * 开始监控指定目录
   */
  async startWatching(directory: string = '.') {
    if (!this.isConnected.value) {
      console.error('文件监控服务未连接')
      return
    }

    console.log('🔍 开始监控目录:', directory)

    // 开始轮询检查文件变化
    this.startPolling()
  }

  /**
   * 开始轮询检查文件变化
   */
  private startPolling() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval)
    }

    console.log('🔄 开始轮询检查文件变化 (间隔: 3秒)')

    this.pollingInterval = window.setInterval(async () => {
      try {
        const response = await axios.get(`${this.baseUrl}/files`)
        const currentFiles = response.data || []

        // 检测文件变化
        const changes = this.detectFileChanges(this.lastFileList, currentFiles)

        if (changes.length > 0) {
          console.log('📁 检测到文件变化:', changes)

          for (const change of changes) {
            this.handleFileChange(change)
          }
        }

        // 更新文件列表
        this.lastFileList = currentFiles

      } catch (error) {
        console.error('❌ 轮询检查文件变化失败:', error)
      }
    }, 3000) // 每3秒检查一次
  }

  /**
   * 检测文件变化
   */
  private detectFileChanges(oldFiles: any[], newFiles: any[]): FileChangeEvent[] {
    const changes: FileChangeEvent[] = []

    // 创建文件路径映射
    const oldFileMap = new Map(oldFiles.map(f => [f.path, f]))
    const newFileMap = new Map(newFiles.map(f => [f.path, f]))

    // 检测新文件和修改的文件
    for (const newFile of newFiles) {
      const oldFile = oldFileMap.get(newFile.path)

      if (!oldFile) {
        // 新文件
        changes.push(this.createFileChangeEvent('created', newFile))
      } else if (newFile.lastModified !== oldFile.lastModified) {
        // 修改的文件
        changes.push(this.createFileChangeEvent('modified', newFile))
      }
    }

    // 检测删除的文件
    for (const oldFile of oldFiles) {
      if (!newFileMap.has(oldFile.path)) {
        changes.push(this.createFileChangeEvent('deleted', oldFile))
      }
    }

    return changes
  }

  /**
   * 创建文件变化事件
   */
  private createFileChangeEvent(type: string, file: any): FileChangeEvent {
    return {
      type,
      filePath: file.path,
      fileName: file.name,
      fileExtension: this.getFileExtension(file.name),
      timestamp: new Date().toISOString(),
      size: file.size,
      isDirectory: file.type === 'directory'
    }
  }

  /**
   * 获取文件扩展名
   */
  private getFileExtension(fileName: string): string {
    const lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
      return fileName.substring(lastDotIndex)
    }
    return ''
  }

  /**
   * 停止监控
   */
  stopWatching() {
    console.log('⏹️ 停止文件监控')

    // 停止轮询
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval)
      this.pollingInterval = null
    }
  }

  /**
   * 断开连接
   */
  disconnect() {
    console.log('🔌 断开文件监控服务')

    // 停止轮询
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval)
      this.pollingInterval = null
    }

    this.isConnected.value = false
  }

  /**
   * 获取连接状态
   */
  getConnectionStatus() {
    return this.isConnected
  }
}

// 创建单例实例
const fileWatchServiceInstance = new FileWatchService()

// 导出 composable 函数
export function useFileWatchService() {
  return fileWatchServiceInstance
}

// 导出实例（用于非组合式API）
export default fileWatchServiceInstance
