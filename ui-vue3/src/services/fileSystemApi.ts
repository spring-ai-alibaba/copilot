import axios from 'axios'

// 配置 API 基础 URL
const API_BASE_URL = 'http://localhost:8080/api' // 根据您的 Java 后端端口调整

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 文件信息接口
export interface FileInfo {
  name: string
  path: string
  type: 'file' | 'directory'
  size?: number
  lastModified?: string
  content?: string
}

// 文件系统 API 服务
export class FileSystemApi {
  
  /**
   * 获取工作目录下的所有文件
   */
  static async getWorkspaceFiles(): Promise<FileInfo[]> {
    try {
      const response = await api.get('/workspace/files')
      return response.data
    } catch (error) {
      console.error('获取工作目录文件失败:', error)
      throw new Error('无法获取工作目录文件')
    }
  }

  /**
   * 读取指定文件内容
   */
  static async readFile(filePath: string): Promise<string> {
    try {
      const response = await api.get(`/workspace/files/content`, {
        params: { path: filePath }
      })
      return response.data.content || ''
    } catch (error) {
      console.error('读取文件失败:', error)
      throw new Error(`无法读取文件: ${filePath}`)
    }
  }

  /**
   * 写入文件内容
   */
  static async writeFile(filePath: string, content: string): Promise<void> {
    try {
      await api.post('/workspace/files/content', {
        path: filePath,
        content: content
      })
    } catch (error) {
      console.error('写入文件失败:', error)
      throw new Error(`无法写入文件: ${filePath}`)
    }
  }

  /**
   * 创建新文件
   */
  static async createFile(filePath: string, content: string = ''): Promise<void> {
    try {
      await api.post('/workspace/files', {
        path: filePath,
        content: content
      })
    } catch (error) {
      console.error('创建文件失败:', error)
      throw new Error(`无法创建文件: ${filePath}`)
    }
  }

  /**
   * 删除文件
   */
  static async deleteFile(filePath: string): Promise<void> {
    try {
      await api.delete('/workspace/files', {
        params: { path: filePath }
      })
    } catch (error) {
      console.error('删除文件失败:', error)
      throw new Error(`无法删除文件: ${filePath}`)
    }
  }

  /**
   * 创建目录
   */
  static async createDirectory(dirPath: string): Promise<void> {
    try {
      await api.post('/workspace/directories', {
        path: dirPath
      })
    } catch (error) {
      console.error('创建目录失败:', error)
      throw new Error(`无法创建目录: ${dirPath}`)
    }
  }

  /**
   * 重命名文件或目录
   */
  static async rename(oldPath: string, newPath: string): Promise<void> {
    try {
      await api.put('/workspace/files/rename', {
        oldPath: oldPath,
        newPath: newPath
      })
    } catch (error) {
      console.error('重命名失败:', error)
      throw new Error(`无法重命名: ${oldPath} -> ${newPath}`)
    }
  }

  /**
   * 监听文件变化（轮询方式）
   */
  static async watchFiles(callback: (files: FileInfo[]) => void, interval: number = 3000): Promise<() => void> {
    let isWatching = true
    
    const poll = async () => {
      if (!isWatching) return
      
      try {
        const files = await this.getWorkspaceFiles()
        callback(files)
      } catch (error) {
        console.error('文件监听错误:', error)
      }
      
      if (isWatching) {
        setTimeout(poll, interval)
      }
    }
    
    // 立即执行一次
    poll()
    
    // 返回停止监听的函数
    return () => {
      isWatching = false
    }
  }
}

// 导出默认实例
export default FileSystemApi
