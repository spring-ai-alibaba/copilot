import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

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

export const useToolConfigStore = defineStore('toolConfig', () => {
  // State
  const systemTools = ref<Tool[]>([])
  const mcpTools = ref<Tool[]>([])
  const mcpServers = ref<Record<string, McpServerConfig>>({})
  const isLoading = ref(false)

  // Getters
  const allTools = computed(() => [...systemTools.value, ...mcpTools.value])
  const enabledTools = computed(() => allTools.value.filter(tool => tool.enabled))
  const toolCount = computed(() => ({
    system: systemTools.value.length,
    mcp: mcpTools.value.length,
    total: allTools.value.length,
    enabled: enabledTools.value.length
  }))

  // Actions
  const loadSystemTools = async () => {
    try {
      const response = await fetch('/api/tools/system')
      if (response.ok) {
        const tools = await response.json()
        systemTools.value = tools.map((tool: any) => ({
          ...tool,
          type: 'SYSTEM' as const
        }))
      } else {
        throw new Error('Failed to load system tools')
      }
    } catch (error) {
      console.error('加载系统工具失败:', error)
      throw error
    }
  }

  const loadMcpTools = async () => {
    try {
      const response = await fetch('/api/tools/mcp')
      if (response.ok) {
        const tools = await response.json()
        mcpTools.value = tools.map((tool: any) => ({
          ...tool,
          type: 'MCP' as const
        }))
      } else {
        throw new Error('Failed to load MCP tools')
      }
    } catch (error) {
      console.error('加载MCP工具失败:', error)
      throw error
    }
  }

  const loadAllTools = async () => {
    try {
      const response = await fetch('/api/tools/all')
      if (response.ok) {
        const allToolsData = await response.json()
        
        // 分类工具
        systemTools.value = allToolsData
          .filter((tool: any) => tool.type === 'SYSTEM')
          .map((tool: any) => ({ ...tool, type: 'SYSTEM' as const }))
        
        mcpTools.value = allToolsData
          .filter((tool: any) => tool.type === 'MCP')
          .map((tool: any) => ({ ...tool, type: 'MCP' as const }))
        
        console.log('加载工具完成:', {
          systemTools: systemTools.value.length,
          mcpTools: mcpTools.value.length
        })
      } else {
        throw new Error('Failed to load tools')
      }
    } catch (error) {
      console.error('加载工具列表失败:', error)
      throw error
    }
  }

  const loadMcpServers = async () => {
    try {
      const response = await fetch('/api/mcp/servers')
      if (response.ok) {
        const servers = await response.json()
        mcpServers.value = servers
      } else {
        throw new Error('Failed to load MCP servers')
      }
    } catch (error) {
      console.error('加载MCP服务器失败:', error)
      throw error
    }
  }

  const addMcpServer = async (serverName: string, serverConfig: McpServerConfig) => {
    try {
      const response = await fetch('/api/mcp/servers', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: serverName,
          config: serverConfig
        })
      })

      if (response.ok) {
        mcpServers.value[serverName] = serverConfig
        // 重新加载工具列表
        await loadAllTools()
      } else {
        const errorData = await response.json()
        throw new Error(errorData.message || 'Failed to add MCP server')
      }
    } catch (error) {
      console.error('添加MCP服务器失败:', error)
      throw error
    }
  }

  const removeMcpServer = async (serverName: string) => {
    try {
      const response = await fetch(`/api/mcp/servers/${serverName}`, {
        method: 'DELETE'
      })

      if (response.ok) {
        delete mcpServers.value[serverName]
        // 重新加载工具列表
        await loadAllTools()
      } else {
        throw new Error('Failed to remove MCP server')
      }
    } catch (error) {
      console.error('删除MCP服务器失败:', error)
      throw error
    }
  }

  const testTool = async (toolName: string, toolType: string) => {
    try {
      // 根据工具类型生成测试参数
      const tool = toolType === 'system' 
        ? systemTools.value.find(t => t.name === toolName)
        : mcpTools.value.find(t => t.name === toolName)

      if (!tool) {
        throw new Error('工具不存在')
      }

      const testParams: Record<string, any> = {}
      if (tool.parameters && tool.parameters.length > 0) {
        tool.parameters.forEach(param => {
          testParams[param.name] = getDefaultValueForType(param.type)
        })
      }

      const response = await fetch('/api/tools/test', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          toolName,
          toolType,
          parameters: testParams
        })
      })

      if (response.ok) {
        const result = await response.json()
        alert(`工具测试成功!\n\n结果: ${JSON.stringify(result, null, 2)}`)
      } else {
        const errorData = await response.json()
        throw new Error(errorData.message || 'Tool test failed')
      }
    } catch (error) {
      console.error('测试工具失败:', error)
      alert(`工具测试失败: ${error instanceof Error ? error.message : '未知错误'}`)
      throw error
    }
  }

  const toggleTool = async (toolName: string, enabled: boolean) => {
    try {
      const response = await fetch('/api/tools/toggle', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          toolName,
          enabled
        })
      })

      if (response.ok) {
        // 更新本地状态
        const systemTool = systemTools.value.find(t => t.name === toolName)
        if (systemTool) {
          systemTool.enabled = enabled
        }
      } else {
        throw new Error('Failed to toggle tool')
      }
    } catch (error) {
      console.error('切换工具状态失败:', error)
      throw error
    }
  }

  const removeTool = async (toolName: string) => {
    try {
      const response = await fetch(`/api/tools/${toolName}`, {
        method: 'DELETE'
      })

      if (response.ok) {
        // 从本地状态中移除
        const mcpIndex = mcpTools.value.findIndex(t => t.name === toolName)
        if (mcpIndex > -1) {
          mcpTools.value.splice(mcpIndex, 1)
        }
      } else {
        throw new Error('Failed to remove tool')
      }
    } catch (error) {
      console.error('删除工具失败:', error)
      throw error
    }
  }

  const getToolByName = (toolName: string): Tool | undefined => {
    return allTools.value.find(tool => tool.name === toolName)
  }

  const getToolsByType = (type: 'SYSTEM' | 'MCP'): Tool[] => {
    return allTools.value.filter(tool => tool.type === type)
  }

  const searchTools = (query: string): Tool[] => {
    if (!query.trim()) return []
    
    const lowercaseQuery = query.toLowerCase()
    return allTools.value.filter(tool =>
      tool.name.toLowerCase().includes(lowercaseQuery) ||
      tool.displayName?.toLowerCase().includes(lowercaseQuery) ||
      tool.description.toLowerCase().includes(lowercaseQuery)
    )
  }

  // Helper function
  const getDefaultValueForType = (type: string): any => {
    switch (type.toLowerCase()) {
      case 'string':
        return 'test'
      case 'number':
      case 'integer':
        return 1
      case 'boolean':
        return true
      case 'array':
        return []
      case 'object':
        return {}
      default:
        return 'test'
    }
  }

  return {
    // State
    systemTools,
    mcpTools,
    mcpServers,
    isLoading,
    
    // Getters
    allTools,
    enabledTools,
    toolCount,
    
    // Actions
    loadSystemTools,
    loadMcpTools,
    loadAllTools,
    loadMcpServers,
    addMcpServer,
    removeMcpServer,
    testTool,
    toggleTool,
    removeTool,
    getToolByName,
    getToolsByType,
    searchTools
  }
})

export type ToolConfigStore = ReturnType<typeof useToolConfigStore>
