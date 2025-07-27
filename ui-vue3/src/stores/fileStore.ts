import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import FileSystemApi, { type FileInfo } from '@/services/fileSystemApi'
import { useFileWatchService, type FileChangeEvent } from '@/services/fileWatchService'

export interface FileNode {
  name: string
  path: string
  type: 'file' | 'directory'
  children?: FileNode[]
  content?: string
}

export const useFileStore = defineStore('file', () => {
  // 状态
  const files = ref<Record<string, string>>({})
  const fileTree = ref<FileNode[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const isConnectedToBackend = ref(false)
  const isInitialized = ref(false)
  const fileWatchService = useFileWatchService()

  // 计算属性
  const getContent = computed(() => {
    return (path: string): string => {
      return files.value[path] || ''
    }
  })

  const getFileList = computed(() => {
    return Object.keys(files.value)
  })

  // 方法
  const addFile = (path: string, content: string = '') => {
    files.value[path] = content
    updateFileTree()
  }

  const removeFile = (path: string) => {
    delete files.value[path]
    updateFileTree()
  }

  const updateContent = (path: string, content: string) => {
    if (files.value.hasOwnProperty(path)) {
      files.value[path] = content
    }
  }

  const renameFile = (oldPath: string, newPath: string) => {
    if (files.value.hasOwnProperty(oldPath)) {
      files.value[newPath] = files.value[oldPath]
      delete files.value[oldPath]
      updateFileTree()
    }
  }

  const createDirectory = (dirPath: string) => {
    // 目录在文件系统中通过文件路径隐式创建
    updateFileTree()
  }

  const updateFileTree = () => {
    const tree: FileNode[] = []
    const pathMap = new Map<string, FileNode>()

    // 首先创建所有路径的节点
    Object.keys(files.value).forEach(filePath => {
      const parts = filePath.split('/').filter(part => part.length > 0) // 过滤空字符串
      let currentPath = ''

      parts.forEach((part, index) => {
        const parentPath = currentPath
        currentPath = currentPath ? `${currentPath}/${part}` : part

        if (!pathMap.has(currentPath)) {
          const isFile = index === parts.length - 1
          const node: FileNode = {
            name: part,
            path: currentPath,
            type: isFile ? 'file' : 'directory',
            children: isFile ? undefined : [],
            content: isFile ? files.value[filePath] : undefined
          }

          pathMap.set(currentPath, node)

          if (parentPath) {
            const parent = pathMap.get(parentPath)
            if (parent && parent.children) {
              parent.children.push(node)
            }
          } else {
            tree.push(node)
          }
        }
      })
    })

    // 对每个目录的子节点进行排序（目录在前，文件在后，然后按名称排序）
    const sortNodes = (nodes: FileNode[]) => {
      nodes.sort((a, b) => {
        if (a.type !== b.type) {
          return a.type === 'directory' ? -1 : 1
        }
        return a.name.localeCompare(b.name)
      })

      nodes.forEach(node => {
        if (node.children) {
          sortNodes(node.children)
        }
      })
    }

    sortNodes(tree)
    fileTree.value = tree
  }

  const getFilesByExtension = (extension: string): string[] => {
    return Object.keys(files.value).filter(path => path.endsWith(extension))
  }

  const searchFiles = (query: string): string[] => {
    const lowerQuery = query.toLowerCase()
    return Object.keys(files.value).filter(path =>
      path.toLowerCase().includes(lowerQuery) ||
      files.value[path].toLowerCase().includes(lowerQuery)
    )
  }

  const exportFiles = () => {
    return { ...files.value }
  }

  const importFiles = (importedFiles: Record<string, string>) => {
    files.value = { ...importedFiles }
    updateFileTree()
  }

  const clearAll = () => {
    files.value = {}
    fileTree.value = []
  }

  // 后端 API 相关方法
  const loadWorkspaceFiles = async () => {
    isLoading.value = true
    error.value = null

    try {
      const fileInfos = await FileSystemApi.getWorkspaceFiles()

      // 清空当前文件
      files.value = {}

      // 加载文件内容
      for (const fileInfo of fileInfos) {
        if (fileInfo.type === 'file') {
          try {
            const content = await FileSystemApi.readFile(fileInfo.path)
            files.value[fileInfo.path] = content
          } catch (err) {
            console.warn(`无法读取文件 ${fileInfo.path}:`, err)
            files.value[fileInfo.path] = ''
          }
        }
      }

      updateFileTree()
      isConnectedToBackend.value = true
      isInitialized.value = true

      // 自动启动文件监控
      startRealtimeFileWatch()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载工作目录失败'
      isConnectedToBackend.value = false
      console.error('加载工作目录失败:', err)
    } finally {
      isLoading.value = false
    }
  }

  const saveFileToBackend = async (filePath: string, content: string) => {
    try {
      await FileSystemApi.writeFile(filePath, content)
      files.value[filePath] = content
    } catch (err) {
      error.value = err instanceof Error ? err.message : '保存文件失败'
      throw err
    }
  }

  const createFileInBackend = async (filePath: string, content: string = '') => {
    try {
      await FileSystemApi.createFile(filePath, content)
      files.value[filePath] = content
      updateFileTree()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '创建文件失败'
      throw err
    }
  }

  const deleteFileFromBackend = async (filePath: string) => {
    try {
      await FileSystemApi.deleteFile(filePath)
      delete files.value[filePath]
      updateFileTree()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '删除文件失败'
      throw err
    }
  }

  const renameFileInBackend = async (oldPath: string, newPath: string) => {
    try {
      await FileSystemApi.rename(oldPath, newPath)
      if (files.value[oldPath]) {
        files.value[newPath] = files.value[oldPath]
        delete files.value[oldPath]
        updateFileTree()
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '重命名失败'
      throw err
    }
  }

  const startFileWatcher = async () => {
    try {
      return await FileSystemApi.watchFiles((fileInfos) => {
        // 检查文件是否有变化
        const currentPaths = new Set(Object.keys(files.value))
        const newPaths = new Set(fileInfos.filter(f => f.type === 'file').map(f => f.path))

        // 如果文件列表有变化，重新加载
        if (currentPaths.size !== newPaths.size ||
            ![...currentPaths].every(path => newPaths.has(path))) {
          loadWorkspaceFiles()
        }
      })
    } catch (err) {
      console.error('启动文件监听失败:', err)
      return () => {}
    }
  }

  const clearError = () => {
    error.value = null
  }

  // 处理文件变化事件 - 简化版，只刷新文件列表
  const handleFileChange = async (event: FileChangeEvent) => {
    console.log('🎯 FileStore收到文件变化事件:', event.type, event.filePath)

    try {
      // 重新加载工作目录文件列表
      console.log('🔄 重新加载文件列表...')
      await loadWorkspaceFiles()
      console.log('✅ 文件列表已刷新')

    } catch (err) {
      console.error('刷新文件列表失败:', err)
      error.value = `刷新文件列表失败: ${err instanceof Error ? err.message : '未知错误'}`
    }
  }

  // 启动文件监控
  const startRealtimeFileWatch = () => {
    if (!isConnectedToBackend.value) {
      console.warn('⚠️ 后端未连接，无法启动文件监控')
      return
    }

    console.log('🔍 启动文件浏览器自动刷新监控')

    // 连接文件监控服务
    fileWatchService.connect({
      onFileChange: handleFileChange,
      onConnect: () => {
        console.log('✅ 文件监控API连接成功')
        // 开始监控工作目录
        console.log('📁 开始监控工作目录，每3秒检查一次文件变化')
        fileWatchService.startWatching('.')
      },
      onDisconnect: () => {
        console.log('❌ 文件监控连接断开')
      },
      onError: (error) => {
        console.error('❌ 文件监控错误:', error)
      }
    })
  }

  // 停止文件监控
  const stopRealtimeFileWatch = () => {
    console.log('⏹️ 停止文件浏览器自动刷新监控')
    fileWatchService.stopWatching()
    fileWatchService.disconnect()
  }

  // 初始化时更新文件树
  updateFileTree()

  return {
    // 状态
    files,
    fileTree,
    isLoading,
    error,
    isConnectedToBackend,
    isInitialized,

    // 计算属性
    getContent,
    getFileList,

    // 本地方法
    addFile,
    removeFile,
    updateContent,
    renameFile,
    createDirectory,
    getFilesByExtension,
    searchFiles,
    exportFiles,
    importFiles,
    clearAll,

    // 后端 API 方法
    loadWorkspaceFiles,
    saveFileToBackend,
    createFileInBackend,
    deleteFileFromBackend,
    renameFileInBackend,
    startFileWatcher,
    clearError,

    // 实时文件监控方法
    startRealtimeFileWatch,
    stopRealtimeFileWatch,
    handleFileChange
  }
})
