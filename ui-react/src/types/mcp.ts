export type MCPArgType = 'string' | 'list' | 'number'
export type MCPEnvType = 'string' | 'number'
export type MCPArgParameter = { [key: string]: MCPArgType }
export type MCPEnvParameter = { [key: string]: MCPEnvType }

export interface MCPServerParameter {
    name: string
    type: MCPArgType | MCPEnvType
    description: string
}

export interface MCPServer {
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

export interface MCPToolResponse {
    id: string // tool call id, it should be unique
    tool: MCPTool // tool info
    status: string // 'invoking' | 'done'
    response?: any
}
