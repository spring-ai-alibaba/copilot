import React, {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {Form, Input, message, Modal, Radio} from 'antd'
import {saveMcpMarket, updateMcpMarket} from '@/api/mcpMarkets'
import type {McpMarketInfo} from '@/types/mcp'
import {JsonEditorWrapper} from './JsonEditor'

const {TextArea} = Input

interface AddMarketModalProps {
    visible: boolean
    market?: McpMarketInfo | null
    onCancel: () => void
    onOk: (data: McpMarketInfo) => void
}

export function AddMarketModal({visible, market, onCancel, onOk}: AddMarketModalProps) {
    const {t} = useTranslation()
    const [form] = Form.useForm()
    const [loading, setLoading] = useState(false)
    const [jsonError, setJsonError] = useState("")

    useEffect(() => {
        if (visible) {
            if (market) {
                const validStatus = (market.status === 'ENABLED' || market.status === 'DISABLED')
                    ? market.status
                    : 'ENABLED'
                form.setFieldsValue({
                    name: market.name,
                    url: market.url,
                    description: market.description || "",
                    authConfig: market.authConfig || "",
                    status: validStatus
                })
            } else {
                form.resetFields()
            }
            setJsonError("")
        }
    }, [visible, market, form])

    const handleOk = async () => {
        try {
            const values = await form.validateFields()

            // 验证 JSON 格式
            if (values.authConfig && values.authConfig.trim()) {
                try {
                    JSON.parse(values.authConfig)
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

            const marketData: Partial<McpMarketInfo> = {
                name: values.name.trim(),
                url: values.url.trim(),
                description: values.description?.trim() || null,
                authConfig: values.authConfig?.trim() || null,
                status: validStatus
            }

            let savedMarket: McpMarketInfo
            if (market && market.id) {
                savedMarket = await updateMcpMarket({...marketData, id: market.id})
                message.success(t('settings.mcp.markets.messages.updateSuccess'))
            } else {
                savedMarket = await saveMcpMarket(marketData)
                message.success(t('settings.mcp.markets.messages.addSuccess'))
            }

            form.resetFields()
            setJsonError("")
            onOk(savedMarket)
        } catch (error: any) {
            if (error.errorFields) return // 表单验证错误
            message.error(error.message || (market ? t('settings.mcp.markets.messages.saveError') : t('settings.mcp.markets.messages.saveError')))
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
            title={market ? t('settings.mcp.markets.form.editTitle') : t('settings.mcp.markets.form.addTitle')}
            open={visible}
            onOk={handleOk}
            onCancel={handleCancel}
            confirmLoading={loading}
            width={800}
            okText={t('settings.mcp.markets.form.save')}
            cancelText={t('settings.mcp.markets.form.cancel')}
        >
            <Form
                form={form}
                layout="vertical"
                requiredMark={false}
                style={{padding: '8px 0'}}
            >
                <Form.Item
                    name="name"
                    label={t('settings.mcp.markets.form.name')}
                    rules={[
                        {required: true, message: t('settings.mcp.markets.form.nameRequired')}
                    ]}
                >
                    <Input placeholder={t('settings.mcp.markets.form.namePlaceholder')}/>
                </Form.Item>

                <Form.Item
                    name="url"
                    label={t('settings.mcp.markets.form.url')}
                    rules={[
                        {required: true, message: t('settings.mcp.markets.form.urlRequired')},
                        {type: 'url', message: t('settings.mcp.markets.form.urlInvalid')}
                    ]}
                >
                    <Input placeholder={t('settings.mcp.markets.form.urlPlaceholder')}/>
                </Form.Item>

                <Form.Item
                    name="description"
                    label={t('settings.mcp.markets.form.description')}
                >
                    <TextArea
                        rows={3}
                        placeholder={t('settings.mcp.markets.form.descriptionPlaceholder')}
                    />
                </Form.Item>

                <Form.Item
                    name="authConfig"
                    label={t('settings.mcp.markets.form.authConfig')}
                    help={jsonError && <span style={{color: '#ff4d4f'}}>{jsonError}</span>}
                >
                    <JsonEditorWrapper
                        form={form}
                        fieldName="authConfig"
                        onError={(error) => {
                            if (error) {
                                setJsonError(error)
                            } else {
                                setJsonError("")
                            }
                        }}
                    />
                </Form.Item>

                <Form.Item
                    name="status"
                    label={t('settings.mcp.markets.columns.status')}
                    rules={[
                        {required: true}
                    ]}
                >
                    <Radio.Group>
                        <Radio value="ENABLED">{t('settings.mcp.markets.status.enabled')}</Radio>
                        <Radio value="DISABLED">{t('settings.mcp.markets.status.disabled')}</Radio>
                    </Radio.Group>
                </Form.Item>
            </Form>
        </Modal>
    )
}

