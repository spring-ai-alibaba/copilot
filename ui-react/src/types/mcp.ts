export interface MCPServer {
    id?: number  // 数据库 ID（用于发送到后端）
    name: string
    description?: string
    baseUrl?: string
    command?: string
    args?: string[]
    env?: Record<string, string>
    isActive: boolean
}

export interface MCPToolInputSchema {
    type: string
    title: string
    description?: string
    required?: string[]
    properties: Record<string, object>
}

export interface MCPTool {
    id: `${string}.${string}`
    serverName: string
    name: string
    description?: string
    inputSchema: MCPToolInputSchema
}

/**
 * MCP 工具类型
 */
export type MCPToolType = 'LOCAL' | 'REMOTE' | 'BUILTIN'

/**
 * MCP 工具状态
 */
export type MCPToolStatus = 'ENABLED' | 'DISABLED'

/**
 * 后端返回的 MCP 工具数据
 */
export interface McpToolInfo {
    id: number
    name: string
    description: string | null
    type: MCPToolType
    status: MCPToolStatus
    configJson: string | null
    createTime: string
    updateTime: string
}

/**
 * MCP 工具数据类型别名（向后兼容）
 */
export type McpToolData = McpToolInfo

/**
 * 后端 API 响应格式
 */
export interface McpServerListResponse {
    total: number
    data: McpToolInfo[]
    success: boolean
}
