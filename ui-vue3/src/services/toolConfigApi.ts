/**
 * 工具配置API服务
 * 提供与后端工具配置相关的API调用
 */

export interface Tool {
  name: string
  displayName?: string
  description: string
  type: 'SYSTEM' | 'MCP'
  source: string
  enabled: boolean
  parameters?: Array<{
    name: string
    type: string
    description: string
    required?: boolean
  }>
  icon?: string
}

export interface McpServerConfig {
  command: string
  args?: string[]
  workingDirectory?: string
  env?: Record<string, string>
}

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
  error?: string
}

class ToolConfigApi {
  private baseUrl = '/api'

  /**
   * 获取所有工具
   */
  async getAllTools(): Promise<Tool[]> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/all`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('获取所有工具失败:', error)
      throw error
    }
  }

  /**
   * 获取系统工具
   */
  async getSystemTools(): Promise<Tool[]> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/system`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('获取系统工具失败:', error)
      throw error
    }
  }

  /**
   * 获取MCP工具
   */
  async getMcpTools(): Promise<Tool[]> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/mcp`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('获取MCP工具失败:', error)
      throw error
    }
  }

  /**
   * 获取MCP服务器列表
   */
  async getMcpServers(): Promise<Record<string, McpServerConfig>> {
    try {
      const response = await fetch(`${this.baseUrl}/mcp/servers`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('获取MCP服务器失败:', error)
      throw error
    }
  }

  /**
   * 添加MCP服务器
   */
  async addMcpServer(serverName: string, serverConfig: McpServerConfig): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/mcp/servers`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: serverName,
          config: serverConfig
        })
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('添加MCP服务器失败:', error)
      throw error
    }
  }

  /**
   * 删除MCP服务器
   */
  async removeMcpServer(serverName: string): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/mcp/servers/${serverName}`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('删除MCP服务器失败:', error)
      throw error
    }
  }

  /**
   * 测试工具
   */
  async testTool(toolName: string, toolType: string, parameters: Record<string, any> = {}): Promise<any> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/test`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          toolName,
          toolType,
          parameters
        })
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
      }

      return await response.json()
    } catch (error) {
      console.error('测试工具失败:', error)
      throw error
    }
  }

  /**
   * 切换工具启用状态
   */
  async toggleTool(toolName: string, enabled: boolean): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/toggle`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          toolName,
          enabled
        })
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('切换工具状态失败:', error)
      throw error
    }
  }

  /**
   * 删除工具
   */
  async removeTool(toolName: string): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/${toolName}`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('删除工具失败:', error)
      throw error
    }
  }

  /**
   * 获取工具详情
   */
  async getToolInfo(toolName: string): Promise<Tool> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/${toolName}`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('获取工具详情失败:', error)
      throw error
    }
  }

  /**
   * 批量操作工具
   */
  async batchToggleTools(toolNames: string[], enabled: boolean): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/batch-toggle`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          toolNames,
          enabled
        })
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('批量切换工具状态失败:', error)
      throw error
    }
  }

  /**
   * 导出工具配置
   */
  async exportToolConfig(): Promise<string> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/export`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.text()
    } catch (error) {
      console.error('导出工具配置失败:', error)
      throw error
    }
  }

  /**
   * 导入工具配置
   */
  async importToolConfig(configData: string): Promise<void> {
    try {
      const response = await fetch(`${this.baseUrl}/tools/import`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ configData })
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
      }
    } catch (error) {
      console.error('导入工具配置失败:', error)
      throw error
    }
  }
}

// 创建单例实例
const toolConfigApi = new ToolConfigApi()

export default toolConfigApi
export { ToolConfigApi }
