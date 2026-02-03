import React, {FC, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {ConfigProvider, Tabs} from 'antd'
import {ShopOutlined, ToolOutlined} from '@ant-design/icons'
import InstallNpxUv from './InstallNpxUv'
import McpToolsTab from './McpToolsTab'
import McpMarketsTab from './McpMarketsTab'
import useThemeStore from "@/stores/themeSlice"

interface MCPSettingsProps {
    isActive?: boolean
}

const MCPSettings: FC<MCPSettingsProps> = ({isActive = false}) => {
    const {t} = useTranslation()
    const {isDarkMode} = useThemeStore()
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
        {
            key: 'markets',
            label: (
                <span className="flex items-center gap-2">
                    <ShopOutlined/>
                    {t('settings.mcp.markets.title')}
                </span>
            ),
            children: <McpMarketsTab/>,
        },
    ]

    return (
        <ConfigProvider
            theme={{
                token: {
                    colorPrimary: '#9333EA',
                    colorPrimaryHover: '#A855F7',
                    colorPrimaryActive: '#7E22CE',
                    colorLink: '#9333EA',
                    colorLinkHover: '#A855F7',
                    colorLinkActive: '#7E22CE',
                },
                components: {
                    Button: {
                        colorPrimary: '#9333EA',
                        colorPrimaryHover: '#A855F7',
                        colorPrimaryActive: '#7E22CE',
                        borderRadius: 8,
                    },
                    Tag: {
                        borderRadiusSM: 12,
                    },
                    Tabs: {
                        itemSelectedColor: '#9333EA',
                        itemHoverColor: '#A855F7',
                        inkBarColor: '#9333EA',
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
