import { defineStore } from 'pinia'
import { ref } from 'vue'
import { WebContainer } from '@webcontainer/api'
import type { WebContainer as WebContainerType } from '@webcontainer/api'
import { useFileStore } from './fileStore'

export const useWebContainerStore = defineStore('webContainer', () => {
  // 状态
  const instance = ref<WebContainerType | null>(null)
  const isBooting = ref(false)
  const isReady = ref(false)
  const serverUrl = ref('')
  const serverPort = ref<number | null>(null)

  // 初始化 WebContainer
  const initContainer = async (): Promise<WebContainerType> => {
    if (instance.value) {
      return instance.value
    }

    isBooting.value = true
    
    try {
      console.log('Booting WebContainer...')
      instance.value = await WebContainer.boot()
      isReady.value = true
      
      console.log('WebContainer booted successfully')
      
      // 监听服务器就绪事件
      instance.value.on('server-ready', (port, url) => {
        console.log('Server ready:', { port, url })
        serverPort.value = port
        serverUrl.value = url
      })

      // 同步文件系统
      await syncFileSystem()
      
      return instance.value
    } catch (error) {
      console.error('Failed to boot WebContainer:', error)
      throw error
    } finally {
      isBooting.value = false
    }
  }

  // 同步文件系统
  const syncFileSystem = async () => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    const fileStore = useFileStore()
    const files = fileStore.files

    try {
      // 构建文件树结构
      const fileTree: any = {}
      
      for (const [path, content] of Object.entries(files)) {
        const parts = path.split('/')
        let current = fileTree
        
        for (let i = 0; i < parts.length; i++) {
          const part = parts[i]
          
          if (i === parts.length - 1) {
            // 文件
            current[part] = {
              file: {
                contents: content
              }
            }
          } else {
            // 目录
            if (!current[part]) {
              current[part] = {
                directory: {}
              }
            }
            current = current[part].directory
          }
        }
      }

      console.log('Mounting file system:', fileTree)
      await instance.value.mount(fileTree)
      console.log('File system mounted successfully')
    } catch (error) {
      console.error('Failed to sync file system:', error)
      throw error
    }
  }

  // 启动开发服务器
  const startDevServer = async (): Promise<string> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      console.log('Installing dependencies...')
      const installProcess = await instance.value.spawn('npm', ['install'])
      const installExitCode = await installProcess.exit
      
      if (installExitCode !== 0) {
        throw new Error('Failed to install dependencies')
      }

      console.log('Starting dev server...')
      const devProcess = await instance.value.spawn('npm', ['run', 'dev'])
      
      // 等待服务器就绪
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
          reject(new Error('Server start timeout'))
        }, 30000) // 30秒超时

        instance.value!.on('server-ready', (port, url) => {
          clearTimeout(timeout)
          console.log('Dev server started:', { port, url })
          resolve(url)
        })
      })
    } catch (error) {
      console.error('Failed to start dev server:', error)
      throw error
    }
  }

  // 执行命令
  const executeCommand = async (command: string, args: string[] = []): Promise<void> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      console.log(`Executing command: ${command} ${args.join(' ')}`)
      const process = await instance.value.spawn(command, args)
      const exitCode = await process.exit
      
      if (exitCode !== 0) {
        throw new Error(`Command failed with exit code ${exitCode}`)
      }
    } catch (error) {
      console.error('Failed to execute command:', error)
      throw error
    }
  }

  // 读取文件
  const readFile = async (path: string): Promise<string> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      return await instance.value.fs.readFile(path, 'utf-8')
    } catch (error) {
      console.error('Failed to read file:', error)
      throw error
    }
  }

  // 写入文件
  const writeFile = async (path: string, content: string): Promise<void> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      // 确保目录存在
      const dirPath = path.substring(0, path.lastIndexOf('/'))
      if (dirPath) {
        await instance.value.fs.mkdir(dirPath, { recursive: true })
      }
      
      await instance.value.fs.writeFile(path, content)
      
      // 同步到文件存储
      const fileStore = useFileStore()
      fileStore.updateContent(path, content)
    } catch (error) {
      console.error('Failed to write file:', error)
      throw error
    }
  }

  // 创建目录
  const createDirectory = async (path: string): Promise<void> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      await instance.value.fs.mkdir(path, { recursive: true })
    } catch (error) {
      console.error('Failed to create directory:', error)
      throw error
    }
  }

  // 删除文件或目录
  const remove = async (path: string): Promise<void> => {
    if (!instance.value) {
      throw new Error('WebContainer not initialized')
    }

    try {
      await instance.value.fs.rm(path, { recursive: true })
    } catch (error) {
      console.error('Failed to remove:', error)
      throw error
    }
  }

  // 获取容器实例
  const getInstance = () => instance.value

  // 重置容器
  const reset = () => {
    instance.value = null
    isReady.value = false
    serverUrl.value = ''
    serverPort.value = null
  }

  return {
    // 状态
    instance,
    isBooting,
    isReady,
    serverUrl,
    serverPort,
    
    // 方法
    initContainer,
    syncFileSystem,
    startDevServer,
    executeCommand,
    readFile,
    writeFile,
    createDirectory,
    remove,
    getInstance,
    reset
  }
})
