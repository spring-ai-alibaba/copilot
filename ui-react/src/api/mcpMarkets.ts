import {apiUrl} from './base'
import type {McpMarketInfo, McpMarketListResponse, McpMarketToolListResponse} from '@/types/mcp'

/**
 * 获取市场列表
 */
export const fetchMcpMarkets = async (params?: {
    keyword?: string
    status?: string
}): Promise<McpMarketListResponse> => {
    const queryParams = new URLSearchParams()
    if (params?.keyword) {
        queryParams.append('keyword', params.keyword)
    }
    if (params?.status) {
        queryParams.append('status', params.status)
    }

    const url = `/api/mcp/markets${queryParams.toString() ? '?' + queryParams.toString() : ''}`
    const res = await fetch(apiUrl(url), {
        method: 'GET',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`获取市场列表失败: ${res.status} ${res.statusText}`)
    }

    const response: McpMarketListResponse = await res.json()

    if (!response.success) {
        throw new Error('获取市场列表失败: 服务器返回失败状态')
    }

    // 返回完整响应，包含 total 信息
    return response
}

/**
 * 获取市场工具列表（支持分页）
 */
export const fetchMarketTools = async (
    marketId: number,
    page: number = 1,
    size: number = 10
): Promise<McpMarketToolListResponse> => {
    const res = await fetch(apiUrl(`/api/mcp/markets/${marketId}/tools?page=${page}&size=${size}`), {
        method: 'GET',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        throw new Error(`获取市场工具列表失败: ${res.status} ${res.statusText}`)
    }

    const response: McpMarketToolListResponse = await res.json()

    if (!response.success) {
        throw new Error('获取市场工具列表失败: 服务器返回失败状态')
    }

    return response
}

/**
 * 刷新市场工具列表
 */
export const refreshMarketTools = async (marketId: number): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/markets/${marketId}/refresh`), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `刷新市场工具列表失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '刷新失败')
    }

    return response
}

/**
 * 批量加载工具到本地
 */
export const batchLoadTools = async (toolIds: number[]): Promise<{
    success: boolean;
    message: string;
    successCount?: number
}> => {
    const res = await fetch(apiUrl('/api/mcp/markets/tools/batch-load'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({toolIds})
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `批量加载工具失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '批量加载失败')
    }

    return response
}

/**
 * 加载单个工具到本地
 */
export const loadToolToLocal = async (toolId: number): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/markets/tools/${toolId}/load`), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `加载工具失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '加载失败')
    }

    return response
}

/**
 * 保存市场
 */
export const saveMcpMarket = async (market: Partial<McpMarketInfo>): Promise<McpMarketInfo> => {
    const res = await fetch(apiUrl('/api/mcp/markets'), {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(market)
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `保存市场失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '保存失败')
    }

    return response.data
}

/**
 * 更新市场
 */
export const updateMcpMarket = async (market: Partial<McpMarketInfo> & { id: number }): Promise<McpMarketInfo> => {
    const {id, ...rest} = market
    const res = await fetch(apiUrl(`/api/mcp/markets/${encodeURIComponent(id)}`), {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(rest)
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `更新市场失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '更新失败')
    }

    return response.data
}

/**
 * 更新市场状态
 */
export const updateMarketStatus = async (id: number, status: 'ENABLED' | 'DISABLED'): Promise<{
    success: boolean;
    message: string
}> => {
    const res = await fetch(apiUrl(`/api/mcp/markets/${encodeURIComponent(id)}/status?status=${status}`), {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `更新市场状态失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '状态更新失败')
    }

    return response
}

/**
 * 删除市场
 */
export const deleteMcpMarket = async (id: number): Promise<{ success: boolean; message: string }> => {
    const res = await fetch(apiUrl(`/api/mcp/markets/${encodeURIComponent(id)}`), {
        method: 'DELETE',
        headers: {'Content-Type': 'application/json'}
    })

    if (!res.ok) {
        const errorResponse = await res.json().catch(() => ({}))
        throw new Error(errorResponse.message || `删除市场失败: ${res.status} ${res.statusText}`)
    }

    const response = await res.json()

    if (!response.success) {
        throw new Error(response.message || '删除失败')
    }

    return response
}

