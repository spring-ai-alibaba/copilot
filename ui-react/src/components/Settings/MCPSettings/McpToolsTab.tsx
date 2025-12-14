import React, {FC, useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Button, Descriptions, Input, message, Modal, Popconfirm, Space, Table, Tag, Tooltip} from 'antd'
import type {ColumnsType, TablePaginationConfig} from 'antd/es/table'
import {
    DeleteOutlined,
    EditOutlined,
    ExperimentOutlined,
    EyeOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    PlusOutlined,
    ReloadOutlined
} from '@ant-design/icons'
import {AddMcpServerModal} from './AddMcpServerModal'
import useThemeStore from "@/stores/themeSlice"
import useMCPStore from "@/stores/useMCPSlice"
import {
    batchDeleteMcpServers,
    deleteMcpServer,
    fetchMcpServers,
    testMcpTool,
    updateMcpServerStatus
} from '@/api/mcpServers'
import type {McpToolData} from '@/types/mcp'

const McpToolsTab: FC = () => {
    const {t} = useTranslation()
    const {isDarkMode} = useThemeStore()
    const setServers = useMCPStore(state => state.setServers)

    const [loadingStates, setLoadingStates] = useState<{
        status?: number | null
        test?: number | null
    }>({})
    const [loading, setLoading] = useState(false)
    const [fetchError, setFetchError] = useState<string | null>(null)
    const [detailModalVisible, setDetailModalVisible] = useState(false)
    const [detailRecord, setDetailRecord] = useState<McpToolData | null>(null)
    const [tableData, setTableData] = useState<McpToolData[]>([])
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
    const [searchKeyword, setSearchKeyword] = useState('')
    const [addModalVisible, setAddModalVisible] = useState(false)
    const [editingServer, setEditingServer] = useState<McpToolData | null>(null)
    const [pagination, setPagination] = useState<TablePaginationConfig>({
        current: 1,
        pageSize: 10,
        total: 0,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: (total) => t('settings.mcp.tools.pagination.total', {total}),
        pageSizeOptions: ['10', '20', '50', '100'],
    })

    const fetchingRef = useRef(false)
    const mountedRef = useRef(true)

    const fetchData = useCallback(async (params?: {
        keyword?: string
        type?: string
        status?: string
    }, force = false) => {
        if (fetchingRef.current && !force) {
            return
        }
        fetchingRef.current = true
        setLoading(true)
        setFetchError(null)
        try {
            const response = await fetchMcpServers(params)
            if (!mountedRef.current) return
            const remoteServers = response.data || []
            const convertedServers = remoteServers.map(tool => ({
                name: tool.name,
                description: tool.description || undefined,
                isActive: tool.status === 'ENABLED',
                id: tool.id
            }))
            setServers(convertedServers as any)
            setTableData(remoteServers)
            setPagination(prev => ({...prev, total: response.total || 0}))
            return remoteServers
        } catch (error: unknown) {
            if (!mountedRef.current) return
            const errorMessage = error instanceof Error ? error.message : 'Failed to load MCP servers'
            setFetchError(errorMessage)
            console.error('Failed to load MCP servers:', error)
            throw error
        } finally {
            if (mountedRef.current) {
                setLoading(false)
            }
            fetchingRef.current = false
        }
    }, [setServers])

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

    const handleDelete = useCallback(async (id: number) => {
        try {
            const result = await deleteMcpServer(id)
            if (result.success) {
                message.success(result.message || t('settings.mcp.tools.messages.deleteSuccess'))
                setTableData(prevData => {
                    const updated = prevData.filter(item => item.id !== id)
                    const convertedServers = updated.map(tool => ({
                        name: tool.name,
                        description: tool.description || undefined,
                        isActive: tool.status === 'ENABLED',
                        id: tool.id
                    }))
                    setServers(convertedServers as any)
                    return updated
                })
            } else {
                message.error(result.message || t('settings.mcp.tools.messages.deleteError'))
            }
        } catch (error: any) {
            message.error(error.message || `${t('settings.mcp.tools.messages.deleteError')}: ${error.message}`)
        }
    }, [setServers, t])

    const handleBatchDelete = useCallback(async () => {
        if (selectedRowKeys.length === 0) {
            message.warning(t('settings.mcp.tools.messages.selectAtLeastOne'))
            return
        }

        try {
            const result = await batchDeleteMcpServers(selectedRowKeys as (number | string)[])
            if (result.success) {
                message.success(result.message || t('settings.mcp.tools.messages.batchDeleteSuccess'))
                setTableData(prevData => {
                    const updated = prevData.filter(item => !selectedRowKeys.includes(item.id))
                    const convertedServers = updated.map(tool => ({
                        name: tool.name,
                        description: tool.description || undefined,
                        isActive: tool.status === 'ENABLED',
                        id: tool.id
                    }))
                    setServers(convertedServers as any)
                    return updated
                })
                setSelectedRowKeys([])
            } else {
                message.error(result.message || t('settings.mcp.tools.messages.batchDeleteError'))
            }
        } catch (error: any) {
            message.error(error.message || `${t('settings.mcp.tools.messages.batchDeleteError')}: ${error.message}`)
        }
    }, [selectedRowKeys, setServers, t])

    const handleTest = async (id: number) => {
        setLoadingStates(prev => ({...prev, test: id}))
        try {
            const result = await testMcpTool(id)
            if (result.success) {
                message.success(t('settings.mcp.tools.messages.testSuccess') + ': ' + result.message)
            } else {
                message.error(t('settings.mcp.tools.messages.testError') + ': ' + result.message)
            }
        } catch (error: any) {
            message.error(t('settings.mcp.tools.messages.testError') + ': ' + error.message)
        } finally {
            setLoadingStates(prev => ({...prev, test: null}))
        }
    }

    const handleToggleActive = useCallback(async (id: number, currentStatus: 'ENABLED' | 'DISABLED') => {
        const newStatus = currentStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
        let previousData: McpToolData[] = []

        setTableData(prevData => {
            previousData = [...prevData]
            return prevData.map(item =>
                item.id === id
                    ? {...item, status: newStatus as 'ENABLED' | 'DISABLED'}
                    : item
            )
        })

        setLoadingStates(prev => ({...prev, status: id}))

        try {
            const result = await updateMcpServerStatus(id, newStatus)
            if (result.success) {
                const statusText = newStatus === 'ENABLED' ? t('settings.mcp.tools.status.enabled') : t('settings.mcp.tools.status.disabled')
                message.success(result.message || t('settings.mcp.tools.messages.toggleSuccess', {status: statusText}))
                setTableData(prevData => {
                    const convertedServers = prevData.map(tool => ({
                        name: tool.name,
                        description: tool.description || undefined,
                        isActive: tool.status === 'ENABLED',
                        id: tool.id
                    }))
                    setServers(convertedServers as any)
                    return prevData
                })
            } else {
                setTableData(previousData)
                message.error(result.message || t('settings.mcp.tools.messages.toggleError'))
            }
        } catch (error: any) {
            setTableData(previousData)
            message.error(error.message || `${t('settings.mcp.tools.messages.toggleError')}: ${error.message}`)
        } finally {
            setLoadingStates(prev => ({...prev, status: null}))
        }
    }, [setServers, t])

    const columns: ColumnsType<McpToolData> = useMemo(() => [
        {
            title: t('settings.mcp.tools.columns.name'),
            dataIndex: 'name',
            key: 'name',
            width: 150,
            ellipsis: true,
        },
        {
            title: t('settings.mcp.tools.columns.type'),
            dataIndex: 'type',
            key: 'type',
            width: 100,
            align: 'center',
            filters: [
                {text: t('settings.mcp.tools.types.remote'), value: 'REMOTE'},
                {text: t('settings.mcp.tools.types.local'), value: 'LOCAL'},
            ],
            onFilter: (value, record) => record.type === value,
            render: (type: string) => (
                <Tag color={type === 'REMOTE' ? 'blue' : 'cyan'}>
                    {type === 'REMOTE' ? t('settings.mcp.tools.types.remote') : t('settings.mcp.tools.types.local')}
                </Tag>
            ),
        },
        {
            title: t('settings.mcp.tools.columns.status'),
            dataIndex: 'status',
            key: 'status',
            width: 100,
            align: 'center',
            filters: [
                {text: t('settings.mcp.tools.status.enabled'), value: 'ENABLED'},
                {text: t('settings.mcp.tools.status.disabled'), value: 'DISABLED'},
            ],
            onFilter: (value, record) => record.status === value,
            render: (status: string) => (
                <Tag color={status === 'ENABLED' ? 'green' : 'default'}>
                    {status === 'ENABLED' ? t('settings.mcp.tools.status.enabled') : t('settings.mcp.tools.status.disabled')}
                </Tag>
            ),
        },
        {
            title: t('settings.mcp.tools.columns.actions'),
            key: 'action',
            width: 180,
            align: 'center',
            fixed: 'right',
            render: (_, record) => {
                const currentStatus = (record.status === 'ENABLED' || record.status === 'DISABLED')
                    ? record.status as 'ENABLED' | 'DISABLED'
                    : 'ENABLED'
                const isEnabled = currentStatus === 'ENABLED'
                return (
                    <Space size="small">
                        <Tooltip title={t('settings.mcp.tools.tooltips.view')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<EyeOutlined/>}
                                onClick={() => {
                                    setDetailRecord(record)
                                    setDetailModalVisible(true)
                                }}
                            />
                        </Tooltip>
                        <Tooltip title={t('settings.mcp.tools.tooltips.edit')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<EditOutlined/>}
                                onClick={() => {
                                    setEditingServer(record)
                                    setAddModalVisible(true)
                                }}
                            />
                        </Tooltip>
                        <Tooltip
                            title={isEnabled ? t('settings.mcp.tools.tooltips.disable') : t('settings.mcp.tools.tooltips.enable')}>
                            <Button
                                type="link"
                                size="small"
                                icon={isEnabled ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                                loading={loadingStates.status === record.id}
                                onClick={() => handleToggleActive(record.id, currentStatus)}
                            />
                        </Tooltip>
                        <Tooltip title={t('settings.mcp.tools.tooltips.test')}>
                            <Button
                                type="link"
                                size="small"
                                icon={<ExperimentOutlined/>}
                                loading={loadingStates.test === record.id}
                                onClick={() => handleTest(record.id)}
                            />
                        </Tooltip>
                        <Popconfirm
                            title={t('settings.mcp.tools.confirm.delete')}
                            onConfirm={() => handleDelete(record.id)}
                            okText={t('settings.mcp.tools.confirm.ok')}
                            cancelText={t('settings.mcp.tools.confirm.cancel')}
                        >
                            <Tooltip title={t('settings.mcp.tools.tooltips.delete')}>
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
    ], [loadingStates, handleToggleActive, handleDelete, setServers, t])

    const handleTableChange = (newPagination: TablePaginationConfig) => {
        setPagination(newPagination)
    }

    return (
        <>
            {fetchError && (
                <div
                    className="mb-2 flex items-center justify-between rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600 dark:border-red-400/40 dark:bg-red-900/20 dark:text-red-200">
                    <span>{fetchError}</span>
                    <button
                        onClick={() => fetchData()}
                        className="text-xs font-medium underline underline-offset-4"
                    >
                        {t('settings.mcp.tools.messages.retry')}
                    </button>
                </div>
            )}

            {/* 工具栏 */}
            <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <span className="text-base font-medium">{t('settings.mcp.tools.listTitle')}</span>
                </div>
                <div className="flex items-center gap-2">
                    <Input.Search
                        placeholder={t('settings.mcp.tools.searchPlaceholder')}
                        allowClear
                        value={searchKeyword}
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        onSearch={handleSearch}
                        style={{width: 240}}
                    />
                    <Tooltip title={t('settings.mcp.tools.tooltips.refresh')}>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={handleRefresh}
                            loading={loading}
                        />
                    </Tooltip>
                    <Button
                        type="primary"
                        icon={<PlusOutlined/>}
                        onClick={() => {
                            setEditingServer(null)
                            setAddModalVisible(true)
                        }}
                    >
                        {t('settings.mcp.tools.add')}
                    </Button>
                </div>
            </div>

            {/* 批量操作栏 */}
            {selectedRowKeys.length > 0 && (
                <div className="mb-3 flex items-center gap-4 rounded-md bg-blue-50 px-4 py-2 dark:bg-blue-900/20">
                    <span
                        className="text-sm">{t('settings.mcp.tools.batch.selected', {count: selectedRowKeys.length})}</span>
                    <Button size="small" type="link" onClick={() => setSelectedRowKeys([])}>
                        {t('settings.mcp.tools.batch.cancel')}
                    </Button>
                    <Popconfirm
                        title={t('settings.mcp.tools.batch.confirmDelete', {count: selectedRowKeys.length})}
                        onConfirm={handleBatchDelete}
                        okText={t('settings.mcp.tools.confirm.ok')}
                        cancelText={t('settings.mcp.tools.confirm.cancel')}
                    >
                        <Button
                            size="small"
                            danger
                            icon={<DeleteOutlined/>}
                        >
                            {t('settings.mcp.tools.batch.delete')}
                        </Button>
                    </Popconfirm>
                </div>
            )}

            {/* 表格 */}
            <Table<McpToolData>
                columns={columns}
                dataSource={tableData}
                rowKey="id"
                loading={loading}
                pagination={{
                    ...pagination,
                    showTotal: (total) => t('settings.mcp.tools.pagination.total', {total})
                }}
                onChange={handleTableChange}
                rowSelection={{
                    selectedRowKeys,
                    onChange: (keys) => setSelectedRowKeys(keys),
                }}
                scroll={{x: 'max-content', y: 'calc(100vh - 520px)'}}
                rowClassName={(record) =>
                    record.status === 'DISABLED' ? 'opacity-70' : ''
                }
                locale={{emptyText: t('settings.mcp.tools.messages.empty')}}
                size="middle"
            />

            {/* 详情弹框 */}
            <Modal
                title={t('settings.mcp.tools.detail.title')}
                open={detailModalVisible}
                onCancel={() => setDetailModalVisible(false)}
                footer={[
                    <Button key="close" onClick={() => setDetailModalVisible(false)}>
                        {t('settings.mcp.tools.detail.close')}
                    </Button>
                ]}
                width={800}
            >
                {detailRecord && (
                    <Descriptions column={1} bordered>
                        <Descriptions.Item
                            label={t('settings.mcp.tools.detail.name')}>{detailRecord.name}</Descriptions.Item>
                        <Descriptions.Item label={t('settings.mcp.tools.detail.type')}>
                            <Tag color={detailRecord.type === 'REMOTE' ? 'blue' : 'cyan'}>
                                {detailRecord.type === 'REMOTE' ? t('settings.mcp.tools.types.remote') : t('settings.mcp.tools.types.local')}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('settings.mcp.tools.detail.description')}>
                            {detailRecord.description || <span
                                className="italic text-gray-400">{t('settings.mcp.tools.detail.noDescription')}</span>}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('settings.mcp.tools.detail.status')}>
                            <Tag color={detailRecord.status === 'ENABLED' ? 'green' : 'default'}>
                                {detailRecord.status === 'ENABLED' ? t('settings.mcp.tools.status.enabled') : t('settings.mcp.tools.status.disabled')}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('settings.mcp.tools.detail.config')}>
                            <pre style={{
                                maxHeight: '300px',
                                overflow: 'auto',
                                padding: '12px',
                                background: isDarkMode ? '#1f1f1f' : '#f5f5f5',
                                borderRadius: '4px',
                                fontSize: '12px',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-all'
                            }}>
                                {detailRecord.configJson ? JSON.stringify(JSON.parse(detailRecord.configJson), null, 2) : t('settings.mcp.tools.detail.noConfig')}
                            </pre>
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Modal>

            {/* 添加/编辑 MCP 服务器弹窗 */}
            <AddMcpServerModal
                visible={addModalVisible}
                server={editingServer}
                onCancel={() => {
                    setAddModalVisible(false)
                    setEditingServer(null)
                }}
                onOk={(savedTool) => {
                    if (editingServer && editingServer.id) {
                        // 更新
                        setTableData(prevData => {
                            const updatedData = prevData.map(item =>
                                item.id === editingServer.id ? savedTool : item
                            )
                            const convertedServers = updatedData.map(tool => ({
                                name: tool.name,
                                description: tool.description || undefined,
                                isActive: tool.status === 'ENABLED',
                                id: tool.id
                            }))
                            setServers(convertedServers as any)
                            return updatedData
                        })
                    } else {
                        // 新增
                        setTableData(prevData => {
                            const updated = [...prevData, savedTool]
                            const convertedServers = updated.map(tool => ({
                                name: tool.name,
                                description: tool.description || undefined,
                                isActive: tool.status === 'ENABLED',
                                id: tool.id
                            }))
                            setServers(convertedServers as any)
                            return updated
                        })
                    }
                    setAddModalVisible(false)
                    setEditingServer(null)
                    fetchData()
                }}
            />
        </>
    )
}

export default McpToolsTab

