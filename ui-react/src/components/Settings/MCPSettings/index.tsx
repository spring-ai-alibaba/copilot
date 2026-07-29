import React, {FC, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {ConfigProvider, Tabs} from 'antd'
import {ToolOutlined} from '@ant-design/icons'
import InstallNpxUv from './InstallNpxUv'
import McpToolsTab from './McpToolsTab'

interface MCPSettingsProps {
    isActive?: boolean
}

const MCPSettings: FC<MCPSettingsProps> = ({isActive = false}) => {
    const {t} = useTranslation()
    const [activeKey, setActiveKey] = useState('tools')

    const tabItems = [
        {
            key: 'tools',
            label: (
                <span className="flex items-center gap-2">
                    <ToolOutlined/>
                    {t('settings.mcp.tools.title')}
                </span>
            ),
            children: <McpToolsTab/>,
        },
    ]

    return (
        <ConfigProvider
            theme={{
                token: {
                    colorPrimary: 'hsl(var(--foreground))',
                    colorPrimaryHover: 'hsl(var(--foreground) / 0.82)',
                    colorPrimaryActive: 'hsl(var(--foreground) / 0.72)',
                    colorLink: 'hsl(var(--foreground))',
                    colorLinkHover: 'hsl(var(--foreground) / 0.82)',
                    colorLinkActive: 'hsl(var(--foreground) / 0.72)',
                },
                components: {
                    Button: {
                        colorPrimary: 'hsl(var(--foreground))',
                        colorPrimaryHover: 'hsl(var(--foreground) / 0.82)',
                        colorPrimaryActive: 'hsl(var(--foreground) / 0.72)',
                        borderRadius: 8,
                    },
                    Tag: {
                        borderRadiusSM: 12,
                    },
                    Tabs: {
                        itemSelectedColor: 'hsl(var(--foreground))',
                        itemHoverColor: 'hsl(var(--foreground) / 0.82)',
                        inkBarColor: 'hsl(var(--foreground))',
                    },
                },
            }}
        >
            <div className="flex flex-col h-full">
                <InstallNpxUv/>
                <Tabs
                    activeKey={activeKey}
                    onChange={setActiveKey}
                    items={tabItems}
                    className="mcp-tabs flex-1"
                />
            </div>
        </ConfigProvider>
    )
}

export default MCPSettings
