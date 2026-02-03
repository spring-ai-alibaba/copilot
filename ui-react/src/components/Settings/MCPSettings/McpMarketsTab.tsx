import React, {FC, useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Button, Input, message, Modal, Popconfirm, Space, Table, Tag, Tooltip} from 'antd'
import {AddMarketModal} from './AddMarketModal'
import type {ColumnsType} from 'antd/es/table'
import {
    AppstoreOutlined,
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    GlobalOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    PlusOutlined,
    ReloadOutlined,
    SyncOutlined
} from '@ant-design/icons'
import {
    batchLoadTools,
    deleteMcpMarket,
    fetchMarketTools,
    fetchMcpMarkets,
    loadToolToLocal,
    refreshMarketTools,
    updateMarketStatus
} from '@/api/mcpMarkets'
import type {McpMarketInfo, McpMarketTool} from '@/types/mcp'

const McpMarketsTab: FC = () => {
    const {t} = useTranslation()

    // 市场列表状态
    const [loading, setLoading] = useState(false)
    const [markets, setMarkets] = useState<McpMarketInfo[]>([])
    const [searchKeyword, setSearchKeyword] = useState('')
    const [marketsTotal, setMarketsTotal] = useState(0)

    // 市场表单弹窗
    const [formModalVisible, setFormModalVisible] = useState(false)
    const [editingMarket, setEditingMarket] = useState<McpMarketInfo | null>(null)

    // 市场工具列表弹窗
    const [toolsModalVisible, setToolsModalVisible] = useState(false)
    const [selectedMarket, setSelectedMarket] = useState<McpMarketInfo | null>(null)
    const [marketTools, setMarketTools] = useState<McpMarketTool[]>([])
    const [toolsLoading, setToolsLoading] = useState(false)
    const [refreshingMarketId, setRefreshingMarketId] = useState<number | null>(null)
    const [selectedToolKeys, setSelectedToolKeys] = useState<React.Key[]>([])
    const [toolsPagination, setToolsPagination] = useState({
        current: 1,
        pageSize: 10,
        total: 0,
    })

    const fetchingRef = useRef(false)
    const mountedRef = useRef(true)

    // 获取市场列表
    const fetchData = useCallback(async (params?: { keyword?: string }, force = false) => {
        if (fetchingRef.current && !force) {
            return
        }
        fetchingRef.current = true
        setLoading(true)
        try {
            const response = await fetchMcpMarkets(params)
            if (!mountedRef.current) return
            // 过滤掉 null 值，确保数据完整性
            const data = (response.data || []).filter(item => item != null)
            setMarkets(data)
            setMarketsTotal(response.total || 0)
        } catch (error: any) {
            if (!mountedRef.current) return
            message.error(error.message || t('settings.mcp.markets.messages.loadError'))
        } finally {
            if (mountedRef.current) {
                setLoading(false)
            }
            fetchingRef.current = false
        }
    }, [t])

    useEffect(() => {
        mountedRef.current = true
        fetchData()
        return () => {
            mountedRef.current = false
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const handleSearch = useCallback(() => {
        fetchData({keyword: searchKeyword || undefined})
    }, [fetchData, searchKeyword])

    const handleRefresh = useCallback(() => {
        setSearchKeyword('')
        fetchData(undefined, true)
    }, [fetchData])

    // 打开新增/编辑弹窗
    const openFormModal = (market?: McpMarketInfo) => {
        setEditingMarket(market || null)
        setFormModalVisible(true)
    }

    // 删除市场
    const handleDelete = async (id: number) => {
        try {
            await deleteMcpMarket(id)
            message.success(t('settings.mcp.markets.messages.deleteSuccess'))
            setMarkets(prev => prev.filter(m => m.id !== id))
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.messages.deleteError'))
        }
    }

    // 切换市场状态
    const handleToggleStatus = async (market: McpMarketInfo) => {
        const newStatus = market.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
        try {
            await updateMarketStatus(market.id, newStatus)
            const statusText = newStatus === 'ENABLED' ? t('settings.mcp.markets.status.enabled') : t('settings.mcp.markets.status.disabled')
            message.success(t('settings.mcp.markets.messages.toggleSuccess', {status: statusText}))
            setMarkets(prev => prev.map(m =>
                m.id === market.id ? {...m, status: newStatus} : m
            ))
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.messages.toggleError'))
        }
    }

    // 刷新市场工具列表
    const handleRefreshMarketTools = async (marketId: number) => {
        setRefreshingMarketId(marketId)
        try {
            await refreshMarketTools(marketId)
            message.success(t('settings.mcp.markets.toolsModal.messages.refreshSuccess'))
            // 如果当前正在查看该市场的工具列表，重新获取
            if (selectedMarket?.id === marketId) {
                await fetchToolsData(marketId, 1, toolsPagination.pageSize)
            }
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.toolsModal.messages.refreshError'))
        } finally {
            setRefreshingMarketId(null)
        }
    }

    // 查看市场工具
    const openToolsModal = async (market: McpMarketInfo) => {
        setSelectedMarket(market)
        setSelectedToolKeys([])
        setToolsModalVisible(true)
        await fetchToolsData(market.id, 1, 10)
    }

    // 获取市场工具数据
    const fetchToolsData = async (marketId: number, page: number, size: number) => {
        setToolsLoading(true)
        try {
            const response = await fetchMarketTools(marketId, page, size)
            setMarketTools(response.data)
            setToolsPagination({
                current: response.page,
                pageSize: response.size,
                total: response.total,
            })
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.toolsModal.messages.loadError'))
        } finally {
            setToolsLoading(false)
        }
    }

    // 加载单个工具到本地
    const handleLoadTool = async (toolId: number) => {
        try {
            await loadToolToLocal(toolId)
            message.success(t('settings.mcp.markets.toolsModal.messages.loadSuccess'))
            // 更新工具状态
            setMarketTools(prev => prev.map(t =>
                t.id === toolId ? {...t, isLoaded: true} : t
            ))
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.toolsModal.messages.loadError'))
        }
    }

    // 批量加载工具到本地
    const handleBatchLoadTools = async () => {
        if (selectedToolKeys.length === 0) {
            message.warning(t('settings.mcp.markets.toolsModal.messages.selectTools'))
            return
        }

        // 过滤掉已加载的工具
        const unloadedTools = marketTools.filter(
            t => selectedToolKeys.includes(t.id) && !t.isLoaded
        )

        if (unloadedTools.length === 0) {
            message.warning(t('settings.mcp.markets.toolsModal.messages.allLoaded'))
            return
        }

        try {
            const result = await batchLoadTools(unloadedTools.map(t => t.id))
            message.success(result.message || t('settings.mcp.markets.toolsModal.messages.batchLoadSuccess', {count: result.successCount}))
            // 更新工具状态
            setMarketTools(prev => prev.map(t =>
                selectedToolKeys.includes(t.id) ? {...t, isLoaded: true} : t
            ))
            setSelectedToolKeys([])
        } catch (error: any) {
            message.error(error.message || t('settings.mcp.markets.toolsModal.messages.batchLoadError'))
        }
    }

    // 市场列表列定义
    const marketColumns: ColumnsType<McpMarketInfo> = useMemo(() => [
        {
            title: t('settings.mcp.markets.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 100,
            ellipsis: true,
            render: (name: string) => (
                <Space>
                    <GlobalOutlined/>
                    <span className="font-medium">{name}</span>
                </Space>
            ),
        },
        {
            title: t('settings.mcp.markets.columns.url'),
            dataIndex: 'url',
            key: 'url',
            width: 200,
            ellipsis: true,
            render: (url: string) => (
                <a href={url} target="_blank" rel="noopener noreferrer" className="text-blue-500 hover:underline">
                    {url}
                </a>
            ),
        },
        {
            title: t('settings.mcp.markets.columns.description'),
            dataIndex: 'description',
            key: 'description',
            width: 100,
            ellipsis: true,
            render: (desc: string | null) => desc || <span className="text-gray-400">-</span>,
        },
        {
            title: t('settings.mcp.markets.columns.status'),
            dataIndex: 'status',
            key: 'status',
            width: 100,
            align: 'center',
            render: (status: string | null | undefined) => {
                const statusValue = status || 'DISABLED'
                return (
                    <Tag color={statusValue === 'ENABLED' ? 'green' : 'default'}>
                        {statusValue === 'ENABLED' ? t('settings.mcp.markets.status.enabled') : t('settings.mcp.markets.status.disabled')}
                    </Tag>
                )
            },
        },
        {
            title: t('settings.mcp.markets.columns.actions'),
            key: 'action',
            width: 220,
            align: 'center',
            fixed: 'right',
            render: (_, record) => {
                if (!record) return null
                const isEnabled = record.status === 'ENABLED'
                const isRefreshing = refreshingMarketId === record.id
                return (
                    <Space size="small">
                        <Tooltip title={t('settings.mcp.markets.tooltips.viewTools')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<AppstoreOutlined/>}
                                onClick={() => openToolsModal(record)}
                            />
                        </Tooltip>
                        <Tooltip title={t('settings.mcp.markets.tooltips.refreshTools')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<SyncOutlined spin={isRefreshing}/>}
                                loading={isRefreshing}
                                onClick={() => handleRefreshMarketTools(record.id)}
                            />
                        </Tooltip>
                        <Tooltip title={t('settings.mcp.markets.tooltips.edit')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<EditOutlined/>}
                                onClick={() => openFormModal(record)}
                            />
                        </Tooltip>
                        <Tooltip
                            title={isEnabled ? t('settings.mcp.markets.tooltips.disable') : t('settings.mcp.markets.tooltips.enable')}>
                            <Button
                                type="link"
                                size="small"
                                icon={isEnabled ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                                onClick={() => handleToggleStatus(record)}
                            />
                        </Tooltip>
                        <Popconfirm
                            title={t('settings.mcp.markets.messages.confirmDelete')}
                            onConfirm={() => handleDelete(record.id)}
                            okText={t('settings.mcp.markets.messages.ok')}
                            cancelText={t('settings.mcp.markets.messages.cancel')}
                        >
                            <Tooltip title={t('settings.mcp.markets.tooltips.delete')}>
                                <Button
                                    type="link"
                                    size="small"
                                    danger
                                    icon={<DeleteOutlined/>}
                                />
                            </Tooltip>
                        </Popconfirm>
                    </Space>
                )
            },
        },
    ], [refreshingMarketId, t])

    // 市场工具列定义
    const toolColumns: ColumnsType<McpMarketTool> = useMemo(() => [
        {
            title: t('settings.mcp.markets.toolsModal.columns.toolName'),
            dataIndex: 'toolName',
            key: 'toolName',
            width: 200,
            ellipsis: true,
        },
        {
            title: t('settings.mcp.markets.toolsModal.columns.toolDescription'),
            dataIndex: 'toolDescription',
            key: 'toolDescription',
            width: 300,
            ellipsis: true,
            render: (desc: string | null) => desc || <span className="text-gray-400">-</span>,
        },
        {
            title: t('settings.mcp.markets.toolsModal.columns.status'),
            dataIndex: 'isLoaded',
            key: 'isLoaded',
            width: 100,
            align: 'center',
            render: (isLoaded: boolean) => (
                <Tag color={isLoaded ? 'green' : 'default'}>
                    {isLoaded ? t('settings.mcp.markets.toolsModal.status.loaded') : t('settings.mcp.markets.toolsModal.status.notLoaded')}
                </Tag>
            ),
        },
        {
            title: t('settings.mcp.markets.toolsModal.columns.actions'),
            key: 'action',
            width: 100,
            align: 'center',
            render: (_, record) => (
                record.isLoaded ? (
                    <Tag color="green">{t('settings.mcp.markets.toolsModal.actions.loaded')}</Tag>
                ) : (
                    <Button
                        type="primary"
                        size="small"
                        icon={<DownloadOutlined/>}
                        onClick={() => handleLoadTool(record.id)}
                    >
                        {t('settings.mcp.markets.toolsModal.actions.load')}
                    </Button>
                )
            ),
        },
    ], [t, handleLoadTool])

    return (
        <>
            {/* 工具栏 */}
            <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <span className="text-base font-medium">{t('settings.mcp.markets.listTitle')}</span>
                </div>
                <div className="flex items-center gap-2">
                    <Input.Search
                        placeholder={t('settings.mcp.markets.searchPlaceholder')}
                        allowClear
                        value={searchKeyword}
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        onSearch={handleSearch}
                        style={{width: 200}}
                    />
                    <Tooltip title={t('settings.mcp.markets.tooltips.refresh')}>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={handleRefresh}
                            loading={loading}
                        />
                    </Tooltip>
                    <Button
                        type="primary"
                        icon={<PlusOutlined/>}
                        onClick={() => openFormModal()}
                    >
                        {t('settings.mcp.markets.add')}
                    </Button>
                </div>
            </div>

            {/* 市场列表表格 */}
            <Table<McpMarketInfo>
                columns={marketColumns}
                dataSource={markets}
                rowKey="id"
                loading={loading}
                pagination={{
                    pageSize: 10,
                    showSizeChanger: true,
                    total: marketsTotal,
                    showTotal: (total) => t('settings.mcp.markets.pagination.total', {total}),
                }}
                // scroll={{x: 1000}}
                locale={{emptyText: t('settings.mcp.markets.messages.empty')}}
            />

            {/* 新增/编辑市场弹窗 - 使用 AddMarketModal */}
            <AddMarketModal
                visible={formModalVisible}
                market={editingMarket}
                onCancel={() => {
                    setFormModalVisible(false)
                    setEditingMarket(null)
                }}
                onOk={(savedMarket) => {
                    // 确保 savedMarket 不为 null
                    if (!savedMarket) {
                        console.warn('savedMarket is null, refreshing list')
                        fetchData(undefined, true)
                        setFormModalVisible(false)
                        setEditingMarket(null)
                        return
                    }
                    if (editingMarket && editingMarket.id) {
                        // 更新 - 使用原 ID 替换，确保 ID 一致性（避免 Long 精度问题）
                        const updatedMarket: McpMarketInfo = {
                            ...savedMarket,
                            id: editingMarket.id  // 强制使用原 ID
                        }
                        setMarkets(prev => {
                            const newList = prev.map(m =>
                                m.id === editingMarket.id ? updatedMarket : m
                            )
                            console.log('Updated market list:', newList)
                            return newList
                        })
                    } else {
                        // 新增 - 刷新列表确保获取正确的 ID
                        fetchData(undefined, true)
                    }
                    setFormModalVisible(false)
                    setEditingMarket(null)
                }}
            />

            {/* 市场工具列表弹窗 */}
            <Modal
                title={
                    <Space>
                        <AppstoreOutlined/>
                        <span>{t('settings.mcp.markets.toolsModal.title', {name: selectedMarket?.name || t('settings.mcp.markets.title')})}</span>
                    </Space>
                }
                open={toolsModalVisible}
                onCancel={() => {
                    setToolsModalVisible(false)
                    setSelectedMarket(null)
                    setMarketTools([])
                    setSelectedToolKeys([])
                }}
                footer={null}
                width={900}
            >
                {/* 工具列表工具栏 */}
                <div className="mb-4 flex items-center justify-between">
                    <Space>
                        {selectedToolKeys.length > 0 && (
                            <>
                                <span className="text-sm text-gray-500">
                                    {t('settings.mcp.markets.toolsModal.selected', {count: selectedToolKeys.length})}
                                </span>
                                <Button
                                    type="primary"
                                    size="small"
                                    icon={<DownloadOutlined/>}
                                    onClick={handleBatchLoadTools}
                                >
                                    {t('settings.mcp.markets.toolsModal.batchLoad')}
                                </Button>
                            </>
                        )}
                    </Space>
                    <Button
                        icon={<SyncOutlined spin={refreshingMarketId === selectedMarket?.id}/>}
                        loading={refreshingMarketId === selectedMarket?.id}
                        onClick={() => selectedMarket && handleRefreshMarketTools(selectedMarket.id)}
                    >
                        {t('settings.mcp.markets.toolsModal.refreshFromRemote')}
                    </Button>
                </div>

                {/* 工具列表表格 */}
                <Table<McpMarketTool>
                    columns={toolColumns}
                    dataSource={marketTools}
                    rowKey="id"
                    loading={toolsLoading}
                    rowSelection={{
                        selectedRowKeys: selectedToolKeys,
                        onChange: (keys) => setSelectedToolKeys(keys),
                        getCheckboxProps: (record) => ({
                            disabled: record.isLoaded,
                        }),
                    }}
                    pagination={{
                        ...toolsPagination,
                        showSizeChanger: true,
                        showTotal: (total, range) => {
                            if (total === 0) {
                                return t('settings.mcp.markets.toolsModal.pagination.total', {total: 0});
                            }
                            return t('settings.mcp.markets.toolsModal.pagination.total', {total});
                        },
                        onChange: (page, pageSize) => {
                            if (selectedMarket) {
                                fetchToolsData(selectedMarket.id, page, pageSize)
                            }
                        },
                    }}
                    // scroll={{y: 400}}
                    locale={{emptyText: t('settings.mcp.markets.toolsModal.empty')}}
                    size="small"
                />
            </Modal>
        </>
    )
}

export default McpMarketsTab

