import React, {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Form, Input, message, Modal, Radio} from 'antd'
import {saveMcpServer, updateMcpServer} from '@/api/mcpServers'
import type {McpToolData} from '@/types/mcp'
import {JsonEditorWrapper} from './JsonEditor'

const {TextArea} = Input

interface AddMcpServerModalProps {
    visible: boolean
    server?: McpToolData | null
    onCancel: () => void
    onOk: (data: McpToolData) => void
}

export function AddMcpServerModal({visible, server, onCancel, onOk}: AddMcpServerModalProps) {
    const {t} = useTranslation()
    const [form] = Form.useForm()
    const [loading, setLoading] = useState(false)
    const [jsonError, setJsonError] = useState("")

    useEffect(() => {
        if (visible) {
            if (server) {
                const validStatus = (server.status === 'ENABLED' || server.status === 'DISABLED')
                    ? server.status
                    : 'ENABLED'
                form.setFieldsValue({
                    name: server.name,
                    description: server.description || "",
                    type: server.type,
                    status: validStatus,
                    configJson: server.configJson || ""
                })
            } else {
                form.resetFields()
            }
            setJsonError("")
        }
    }, [visible, server, form])

    const handleOk = async () => {
        try {
            const values = await form.validateFields()

            // 验证 JSON 格式
            if (values.configJson && values.configJson.trim()) {
                try {
                    JSON.parse(values.configJson)
                } catch (e) {
                    setJsonError(t('settings.mcp.jsonFormatError'))
                    return
                }
            }

            setLoading(true)
            const validStatus: 'ENABLED' | 'DISABLED' =
                (values.status === 'ENABLED' || values.status === 'DISABLED')
                    ? values.status
                    : 'ENABLED'

            const toolData: Partial<McpToolData> = {
                name: values.name.trim(),
                description: values.description?.trim() || null,
                type: values.type,
                status: validStatus,
                configJson: values.configJson?.trim() || null
            }

            let savedTool: McpToolData
            if (server && server.id) {
                savedTool = await updateMcpServer({...toolData, id: server.id})
                message.success(t('settings.mcp.updateSuccess'))
            } else {
                savedTool = await saveMcpServer(toolData)
                message.success(t('settings.mcp.addSuccess'))
            }

            form.resetFields()
            setJsonError("")
            onOk(savedTool)
        } catch (error: any) {
            if (error.errorFields) return // 表单验证错误
            message.error(error.message || (server ? t('settings.mcp.updateError') : t('settings.mcp.addError')))
        } finally {
            setLoading(false)
        }
    }

    const handleCancel = () => {
        form.resetFields()
        setJsonError("")
        onCancel()
    }

    return (
        <Modal
            title={server ? t('settings.mcp.editServer') : t('settings.mcp.addServer')}
            open={visible}
            onOk={handleOk}
            onCancel={handleCancel}
            confirmLoading={loading}
            width={800}
            okText={server ? t('settings.mcp.updateSuccess') : t('settings.mcp.addServer')}
            cancelText={t('common.cancel')}
            destroyOnClose
        >
            <Form
                form={form}
                layout="vertical"
                requiredMark={false}
                style={{padding: '8px 0'}}
            >
                <Form.Item
                    name="name"
                    label={t('settings.mcp.name')}
                    rules={[
                        {required: true, message: t('settings.mcp.nameRequired')}
                    ]}
                >
                    <Input placeholder={t('settings.mcp.name')}/>
                </Form.Item>

                <Form.Item
                    name="description"
                    label={t('settings.mcp.description')}
                >
                    <TextArea
                        rows={3}
                        placeholder={t('settings.mcp.description')}
                    />
                </Form.Item>

                <Form.Item
                    name="type"
                    label={t('settings.mcp.type')}
                    rules={[
                        {required: true, message: t('settings.mcp.type') + ' ' + t('common.name')}
                    ]}
                >
                    <Radio.Group>
                        <Radio value="LOCAL">{t('settings.mcp.tools.types.local')}</Radio>
                        <Radio value="REMOTE">{t('settings.mcp.tools.types.remote')}</Radio>
                    </Radio.Group>
                </Form.Item>

                <Form.Item
                    name="status"
                    label={t('settings.mcp.tools.columns.status')}
                    rules={[
                        {required: true}
                    ]}
                >
                    <Radio.Group>
                        <Radio value="ENABLED">{t('settings.mcp.tools.status.enabled')}</Radio>
                        <Radio value="DISABLED">{t('settings.mcp.tools.status.disabled')}</Radio>
                    </Radio.Group>
                </Form.Item>

                <Form.Item
                    name="configJson"
                    label={t('settings.mcp.editJson')}
                    help={jsonError && <span style={{color: '#ff4d4f'}}>{jsonError}</span>}
                >
                    <JsonEditorWrapper
                        form={form}
                        fieldName="configJson"
                        onError={(error) => {
                            if (error) {
                                setJsonError(error)
                            } else {
                                setJsonError("")
                            }
                        }}
                    />
                </Form.Item>
            </Form>
        </Modal>
    )
}

