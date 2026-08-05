import {apiUrl} from './base'

/** 后端统一响应结构 */
interface R<T> {
    code: number
    msg: string
    data: T
    success: boolean
}

export interface SkillDraft {
    name: string
    description: string
    conversationId: string
}

export interface SkillItem {
    name: string
    description: string
    source: 'workspace' | 'market'
    enabled: string
    usageCount: string
    lastUsed: string
}

export interface SearchQueryItem {
    query: string
    cnt: number
    lastAt: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(apiUrl(path), {
        headers: {'Content-Type': 'application/json'},
        ...init,
    })
    if (!res.ok) {
        throw new Error(`请求失败: ${res.status} ${res.statusText}`)
    }
    const body: R<T> = await res.json()
    if (!body.success) {
        throw new Error(body.msg || '服务器返回失败状态')
    }
    return body.data
}

/** 已生效技能列表（共享技能库 + 技能市场） */
export const fetchSkills = () =>
    request<SkillItem[]>('/api/skills')

/** 待审核技能草稿列表 */
export const fetchSkillDrafts = () =>
    request<SkillDraft[]>('/api/skills/drafts')

/** 草稿 SKILL.md 内容 */
export const fetchDraftContent = (name: string) =>
    request<string>(`/api/skills/drafts/${encodeURIComponent(name)}/content`)

/** 已生效技能内容（含市场技能） */
export const fetchSkillContent = (name: string, source: string) =>
    request<string>(`/api/skills/${encodeURIComponent(name)}/content?source=${encodeURIComponent(source)}`)

/** 技能启停（共享技能改目录前缀 / 市场技能改 enabled 字段） */
export const updateSkillStatus = (name: string, source: string, enabled: boolean) =>
    request<void>(`/api/skills/${encodeURIComponent(name)}/status?enabled=${enabled}&source=${encodeURIComponent(source)}`,
        {method: 'PUT'})

/** 保存草稿 SKILL.md（审核中编辑） */
export const updateDraftContent = (name: string, content: string) =>
    request<void>(`/api/skills/drafts/${encodeURIComponent(name)}/content`,
        {method: 'PUT', body: JSON.stringify({content})})

/** 最近技能检索词 */
export const fetchSearchQueries = () =>
    request<SearchQueryItem[]>('/api/skills/search-queries')

/** 晋升草稿为正式技能 */
export const promoteDraft = (name: string) =>
    request<void>(`/api/skills/drafts/${encodeURIComponent(name)}/promote`, {method: 'POST'})

/** 驳回并删除草稿 */
export const rejectDraft = (name: string) =>
    request<void>(`/api/skills/drafts/${encodeURIComponent(name)}`, {method: 'DELETE'})
