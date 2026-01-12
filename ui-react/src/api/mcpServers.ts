import {apiUrl} from './base'
import type {MCPServer, McpServerListResponse, McpToolInfo} from '@/types/mcp'

export interface McpServerDTO extends MCPServer {
    id?: string | number
    createdAt?: string
    updatedAt?: string
}

/**
 * 查询 MCP 服务器列表
 */
export const fetchMcpServers = async (params?: {
    keyword?: string
    type?: string
    status?: string
}): Promise<McpServerListResponse> => {
    const queryParams = new URLSearchParams()
    if (params?.keyword) {
        queryParams.append('keyword', params.keyword)
    }
    if (params?.type) {
        queryParams.append('type', params.type)
    }
    if (params?.status) {
        queryParams.append('status', params.status)
    }

    const url = `/api/mcp/servers${queryParams.toString() ? '?' + queryParams.toString() : ''}`
    const res = await fetch(apiUrl(url), {
        method: 'GET',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`获取 MCP 服务器列表失败: ${res.status} ${res.statusText}`)
    }

    const response: McpServerListResponse = await res.json()
    
    if (!response.success) {
        throw new Error('获取 MCP 服务器列表失败: 服务器返回失败状态')
    }

    // 返回完整响应，包含 total 信息
    return response
}

/**
 * 保存或更新 MCP 服务器
 * 后端接口：POST /api/mcp
 * 如果 server 包含 id，则为更新；否则为新增
 */
export const saveMcpServer = async (server: Partial<McpToolInfo>): Promise<McpToolInfo> => {
    const res = await fetch(apiUrl('/api/mcp'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(server)
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `保存 MCP 服务器失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()
    
    if (!response.success) {
        throw new Error(response.message || '保存失败')
    }

    return response.data
}

/**
 * 修改 MCP 服务器
 * 后端接口：PUT /api/mcp/{id}
 */
export const updateMcpServer = async (server: Partial<McpToolInfo> & { id: number }): Promise<McpToolInfo> => {
    if (!server.id) {
        throw new Error('更新 MCP 服务器需要提供 id')
    }

    // 从 server 中提取 id，请求体中不包含 id（id 在路径中）
    const { id, ...toolData } = server

    const res = await fetch(apiUrl(`/api/mcp/${encodeURIComponent(id)}`), {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(toolData)
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `更新 MCP 服务器失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()
    
    if (!response.success) {
        throw new Error(response.message || '更新失败')
    }

    return response.data
}

/**
 * 删除 MCP 服务器
 */
export const deleteMcpServer = async (id: number | string): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/${encodeURIComponent(id)}`), {
        method: 'DELETE',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`删除 MCP 服务器失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()
    
    if (!response.success) {
        throw new Error(response.message || '删除失败')
    }

    return response
}

/**
 * 批量删除 MCP 服务器
 * 后端接口：DELETE /api/mcp/batch?ids=1,2,3
 */
export const batchDeleteMcpServers = async (ids: (number | string)[]): Promise<{ success: boolean; message: string }> => {
    const idsParam = ids.map(id => encodeURIComponent(id)).join(',')
    const res = await fetch(apiUrl(`/api/mcp/batch?ids=${idsParam}`), {
        method: 'DELETE',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `批量删除 MCP 服务器失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()
    
    if (!response.success) {
        throw new Error(response.message || '批量删除失败')
    }

    return response
}

/**
 * 更新 MCP 服务器状态
 */
export const updateMcpServerStatus = async (
    id: number | string,
    status: 'ENABLED' | 'DISABLED'
): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/${encodeURIComponent(id)}/status?status=${encodeURIComponent(status)}`), {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`更新 MCP 服务器状态失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()
    
    if (!response.success) {
        throw new Error(response.message || '状态更新失败')
    }

    return response
}

/**
 * 测试 MCP 工具
 */
export const testMcpTool = async (id: number | string): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/${encodeURIComponent(id)}/test`), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`测试 MCP 工具失败: ${res.status} ${res.statusText}`)
    }

    return res.json()
}

// 向后兼容的别名（已废弃，建议使用新函数名）
/** @deprecated 使用 saveMcpServer 代替 */
export const createMcpServer = saveMcpServer

/** @deprecated 使用 deleteMcpServer 代替 */
export const deleteMcpServerRequest = async (name: string): Promise<void> => {
    // 兼容旧代码：如果没有 id，尝试使用 name 作为 id
    await deleteMcpServer(name)
}

/** @deprecated 使用 updateMcpServerStatus 代替 */
export const setMcpServerActiveRemote = async (
    name: string,
    isActive: boolean
): Promise<{ success: boolean; message: string }> => {
    // 兼容旧代码：如果没有 id，尝试使用 name 作为 id
    return updateMcpServerStatus(name, isActive ? 'ENABLED' : 'DISABLED')
}

