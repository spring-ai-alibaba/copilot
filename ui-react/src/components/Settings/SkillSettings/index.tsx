import React, {FC, useCallback, useEffect, useMemo, useState} from 'react'
import {useTranslation} from 'react-i18next'
import ReactMarkdown from 'react-markdown'
import {
    Badge,
    Button,
    ConfigProvider,
    Empty,
    Input,
    message,
    Modal,
    Popconfirm,
    Segmented,
    Space,
    Switch,
    Table,
    Tag,
    Tooltip,
    Typography,
} from 'antd'
import {CheckOutlined, DeleteOutlined, EditOutlined, EyeOutlined, ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {
    fetchDraftContent,
    fetchSearchQueries,
    fetchSkillContent,
    fetchSkillDrafts,
    fetchSkills,
    promoteDraft,
    rejectDraft,
    updateDraftContent,
    updateSkillStatus,
    type SearchQueryItem,
    type SkillDraft,
    type SkillItem,
} from '@/api/skills'

/** 拆掉 YAML frontmatter，渲染时前置元信息单独展示 */
function splitFrontmatter(content: string): {meta: string; body: string} {
    if (content.startsWith('---')) {
        const end = content.indexOf('\n---', 3)
        if (end > 0) {
            return {meta: content.slice(3, end).trim(), body: content.slice(end + 4).trim()}
        }
    }
    return {meta: '', body: content}
}

interface PreviewState {
    name: string
    content: string
    /** 可编辑 = 草稿 */
    editable: boolean
    editing: boolean
    mode: 'rendered' | 'raw'
}

/**
 * 技能页：已生效技能总览（启停/使用统计/内容查看）
 * + agent 起草技能的人工审核闸门（查看/编辑/晋升/驳回）
 * + 最近技能检索词（新技能的需求信号）。
 */
const SkillSettings: FC = () => {
    const {t} = useTranslation()
    const [drafts, setDrafts] = useState<SkillDraft[]>([])
    const [skills, setSkills] = useState<SkillItem[]>([])
    const [queries, setQueries] = useState<SearchQueryItem[]>([])
    const [keyword, setKeyword] = useState('')
    const [loading, setLoading] = useState(false)
    const [preview, setPreview] = useState<PreviewState | null>(null)
    const [draftText, setDraftText] = useState('')
    const [saving, setSaving] = useState(false)

    const load = useCallback(async () => {
        setLoading(true)
        try {
            const [draftList, skillList, queryList] = await Promise.all([
                fetchSkillDrafts(), fetchSkills(), fetchSearchQueries(),
            ])
            setDrafts(draftList)
            setSkills(skillList)
            setQueries(queryList)
        } catch (e: any) {
            message.error(e.message || t('settings.skills.loadError'))
        } finally {
            setLoading(false)
        }
    }, [t])

    useEffect(() => {
        void load()
    }, [load])

    const filteredSkills = useMemo(() => {
        const k = keyword.trim().toLowerCase()
        if (!k) return skills
        return skills.filter(s =>
            s.name.toLowerCase().includes(k) || (s.description || '').toLowerCase().includes(k))
    }, [skills, keyword])

    const openPreview = async (name: string, fetcher: Promise<string>, editable: boolean) => {
        try {
            const content = await fetcher
            setDraftText(content)
            setPreview({name, content, editable, editing: false, mode: 'rendered'})
        } catch (e: any) {
            message.error(e.message || t('settings.skills.loadError'))
        }
    }

    const handleToggle = async (row: SkillItem, enabled: boolean) => {
        try {
            await updateSkillStatus(row.name, row.source, enabled)
            message.success(enabled ? t('settings.skills.enableSuccess') : t('settings.skills.disableSuccess'))
            void load()
        } catch (e: any) {
            message.error(e.message || t('settings.skills.toggleError'))
        }
    }

    const handleSaveDraft = async () => {
        if (!preview) return
        setSaving(true)
        try {
            await updateDraftContent(preview.name, draftText)
            message.success(t('settings.skills.saveSuccess'))
            setPreview({...preview, content: draftText, editing: false})
            void load()
        } catch (e: any) {
            message.error(e.message || t('settings.skills.saveError'))
        } finally {
            setSaving(false)
        }
    }

    const handlePromote = async (name: string) => {
        try {
            await promoteDraft(name)
            message.success(t('settings.skills.promoteSuccess'))
            void load()
        } catch (e: any) {
            message.error(e.message || t('settings.skills.promoteError'))
        }
    }

    const handleReject = async (name: string) => {
        try {
            await rejectDraft(name)
            message.success(t('settings.skills.rejectSuccess'))
            void load()
        } catch (e: any) {
            message.error(e.message || t('settings.skills.rejectError'))
        }
    }

    const nameCol = {
        title: t('settings.skills.columns.name'),
        dataIndex: 'name',
        width: 200,
        render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    }

    const previewParts = preview ? splitFrontmatter(preview.content) : null

    return (
        <ConfigProvider
            theme={{
                token: {
                    colorPrimary: 'hsl(var(--foreground))',
                    colorLink: 'hsl(var(--foreground))',
                },
            }}
        >
            <div className="flex flex-col gap-4">
                {/* 已生效技能 */}
                <div className="flex items-center justify-between">
                    <div>
                        <div className="text-base font-semibold">{t('settings.skills.activeTitle')}</div>
                        <div className="mt-1 text-xs text-muted-foreground">{t('settings.skills.activeHint')}</div>
                    </div>
                    <Space>
                        <Input
                            allowClear
                            prefix={<SearchOutlined/>}
                            placeholder={t('settings.skills.searchPlaceholder')}
                            style={{width: 220}}
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                        />
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={() => void load()}>
                            {t('settings.skills.refresh')}
                        </Button>
                    </Space>
                </div>

                <Table<SkillItem>
                    rowKey={r => `${r.source}:${r.name}`}
                    size="middle"
                    loading={loading}
                    pagination={false}
                    dataSource={filteredSkills}
                    columns={[
                        nameCol,
                        {
                            title: t('settings.skills.columns.description'),
                            dataIndex: 'description',
                            ellipsis: {showTitle: false},
                            render: (v: string) => <Tooltip title={v} placement="topLeft">{v}</Tooltip>,
                        },
                        {
                            title: t('settings.skills.columns.skillSource'),
                            dataIndex: 'source',
                            width: 110,
                            filters: [
                                {text: t('settings.skills.sourceWorkspace'), value: 'workspace'},
                                {text: t('settings.skills.sourceMarket'), value: 'market'},
                            ],
                            onFilter: (v, row) => row.source === v,
                            render: (v: string) => v === 'market'
                                ? <Tag color="blue">{t('settings.skills.sourceMarket')}</Tag>
                                : <Tag>{t('settings.skills.sourceWorkspace')}</Tag>,
                        },
                        {
                            title: t('settings.skills.columns.enabled'),
                            dataIndex: 'enabled',
                            width: 90,
                            render: (v: string, row: SkillItem) => (
                                <Switch
                                    size="small"
                                    checked={v === 'true'}
                                    onChange={checked => void handleToggle(row, checked)}
                                />
                            ),
                        },
                        {
                            title: t('settings.skills.columns.usage'),
                            dataIndex: 'usageCount',
                            width: 100,
                            align: 'right' as const,
                            sorter: (a, b) => Number(a.usageCount) - Number(b.usageCount),
                            render: (v: string) => Number(v) > 0
                                ? <Typography.Text>{v}</Typography.Text>
                                : <Typography.Text type="secondary">{t('settings.skills.neverUsed')}</Typography.Text>,
                        },
                        {
                            title: t('settings.skills.columns.lastUsed'),
                            dataIndex: 'lastUsed',
                            width: 165,
                            render: (v: string) => v
                                ? <Typography.Text type="secondary">{v.replace('T', ' ').slice(0, 19)}</Typography.Text>
                                : <Typography.Text type="secondary">—</Typography.Text>,
                        },
                        {
                            title: t('settings.skills.columns.actions'),
                            width: 90,
                            render: (_: unknown, row: SkillItem) => (
                                <Button size="small" icon={<EyeOutlined/>}
                                        onClick={() => void openPreview(row.name, fetchSkillContent(row.name, row.source), false)}>
                                    {t('settings.skills.view')}
                                </Button>
                            ),
                        },
                    ]}
                    locale={{
                        emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                                          description={t('settings.skills.activeEmpty')}/>,
                    }}
                />

                {/* 待审核草稿 */}
                <div className="mt-4">
                    <div className="flex items-center gap-2">
                        <span className="text-base font-semibold">{t('settings.skills.draftsTitle')}</span>
                        <Badge count={drafts.length} showZero={false}/>
                    </div>
                    <div className="mb-3 mt-1 text-xs text-muted-foreground">{t('settings.skills.draftsHint')}</div>
                    {drafts.length === 0 ? (
                        <div className="rounded-md border border-dashed border-border/70 px-4 py-3 text-xs text-muted-foreground">
                            {t('settings.skills.empty')}
                        </div>
                    ) : (
                        <Table<SkillDraft>
                            rowKey="name"
                            size="middle"
                            loading={loading}
                            pagination={false}
                            dataSource={drafts}
                            columns={[
                                nameCol,
                                {
                                    title: t('settings.skills.columns.description'),
                                    dataIndex: 'description',
                                    ellipsis: true,
                                },
                                {
                                    title: t('settings.skills.columns.source'),
                                    dataIndex: 'conversationId',
                                    width: 150,
                                    render: (v: string) => v
                                        ? <Tooltip title={v}><Typography.Text type="secondary">{v.slice(0, 8)}…</Typography.Text></Tooltip>
                                        : <Typography.Text type="secondary">{t('settings.skills.sharedSource')}</Typography.Text>,
                                },
                                {
                                    title: t('settings.skills.columns.actions'),
                                    width: 240,
                                    render: (_: unknown, row: SkillDraft) => (
                                        <Space>
                                            <Button size="small" icon={<EyeOutlined/>}
                                                    onClick={() => void openPreview(row.name, fetchDraftContent(row.name), true)}>
                                                {t('settings.skills.review')}
                                            </Button>
                                            <Popconfirm title={t('settings.skills.promoteConfirm')}
                                                        onConfirm={() => void handlePromote(row.name)}>
                                                <Button size="small" type="primary" icon={<CheckOutlined/>}>
                                                    {t('settings.skills.promote')}
                                                </Button>
                                            </Popconfirm>
                                            <Popconfirm title={t('settings.skills.rejectConfirm')}
                                                        onConfirm={() => void handleReject(row.name)}>
                                                <Button size="small" danger icon={<DeleteOutlined/>}>
                                                    {t('settings.skills.reject')}
                                                </Button>
                                            </Popconfirm>
                                        </Space>
                                    ),
                                },
                            ]}
                        />
                    )}
                </div>

                {/* 最近技能检索词 */}
                {queries.length > 0 && (
                    <div className="mt-4">
                        <div className="text-base font-semibold">{t('settings.skills.queriesTitle')}</div>
                        <div className="mb-3 mt-1 text-xs text-muted-foreground">{t('settings.skills.queriesHint')}</div>
                        <div className="flex flex-wrap gap-2">
                            {queries.map(q => (
                                <Tooltip key={q.query}
                                         title={`${t('settings.skills.columns.lastUsed')}: ${String(q.lastAt).replace('T', ' ').slice(0, 19)}`}>
                                    <Tag className="!m-0">
                                        {q.query}
                                        <span className="ml-1 text-muted-foreground">×{q.cnt}</span>
                                    </Tag>
                                </Tooltip>
                            ))}
                        </div>
                    </div>
                )}

                {/* 内容预览 / 草稿编辑 */}
                <Modal
                    title={
                        <Space>
                            <span>{preview?.name}</span>
                            {preview && !preview.editing && (
                                <Segmented
                                    size="small"
                                    value={preview.mode}
                                    onChange={v => setPreview({...preview, mode: v as 'rendered' | 'raw'})}
                                    options={[
                                        {label: t('settings.skills.rendered'), value: 'rendered'},
                                        {label: t('settings.skills.raw'), value: 'raw'},
                                    ]}
                                />
                            )}
                        </Space>
                    }
                    open={!!preview}
                    footer={
                        <Space>
                            {preview?.editable && !preview.editing && (
                                <Button icon={<EditOutlined/>}
                                        onClick={() => setPreview({...preview, editing: true})}>
                                    {t('settings.skills.edit')}
                                </Button>
                            )}
                            {preview?.editing && (
                                <Button type="primary" loading={saving} onClick={() => void handleSaveDraft()}>
                                    {t('settings.skills.save')}
                                </Button>
                            )}
                            <Button onClick={() => setPreview(null)}>{t('common.cancel')}</Button>
                        </Space>
                    }
                    onCancel={() => setPreview(null)}
                    width={780}
                    centered
                    destroyOnClose
                    styles={{body: {maxHeight: '65vh', overflowY: 'auto'}}}
                >
                    {preview?.editing ? (
                        <Input.TextArea
                            value={draftText}
                            onChange={e => setDraftText(e.target.value)}
                            rows={20}
                            className="font-mono"
                        />
                    ) : preview?.mode === 'raw' ? (
                        <pre className="whitespace-pre-wrap break-words rounded-md bg-muted/40 p-4 font-mono text-xs leading-5">
                            {preview?.content}
                        </pre>
                    ) : (
                        <div>
                            {previewParts?.meta && (
                                <pre className="mb-3 whitespace-pre-wrap rounded-md bg-muted/40 p-3 font-mono text-xs leading-5 text-muted-foreground">
                                    {previewParts.meta}
                                </pre>
                            )}
                            <div className="prose prose-sm max-w-none dark:prose-invert">
                                <ReactMarkdown>{previewParts?.body || ''}</ReactMarkdown>
                            </div>
                        </div>
                    )}
                </Modal>
            </div>
        </ConfigProvider>
    )
}

export default SkillSettings
