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

/**
 * MCP 市场实体
 */
export interface McpMarketInfo {
    id: number
    name: string
    url: string
    description: string | null
    authConfig: string | null
    status: 'ENABLED' | 'DISABLED'
    createTime: string
    updateTime: string
}

/**
 * MCP 市场工具实体
 */
export interface McpMarketTool {
    id: number
    marketId: number
    toolName: string
    toolDescription: string | null
    toolVersion: string | null
    toolMetadata: string | null
    isLoaded: boolean
    localToolId: number | null
    createTime: string
}

/**
 * 市场列表响应
 */
export interface McpMarketListResponse {
    success: boolean
    data: McpMarketInfo[]
    total: number
}

/**
 * 市场工具列表响应
 */
export interface McpMarketToolListResponse {
    success: boolean
    data: McpMarketTool[]
    total: number
    page: number
    size: number
    pages: number
}
