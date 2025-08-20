import {CodeOutlined} from '@ant-design/icons'
import {Dropdown, Switch, Tooltip} from 'antd'
import {FC, useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import styled from 'styled-components'
import useMCPServers from '@/hooks/useMCPServers'
import useMCPTools from '@/hooks/useMCPTools'

interface Props {
    ToolbarButton: any
    disabled?: boolean
}

const MCPToolsButton: FC<Props> = ({ToolbarButton, disabled}) => {
    const {mcpServers, activedMcpServers} = useMCPServers()
    const {enabledMCPs, toggleEnableMCP, toggleAllMCPs} = useMCPTools()
    const [isOpen, setIsOpen] = useState(false)
    const dropdownRef = useRef<any>(null)
    const menuRef = useRef<HTMLDivElement>(null)
    const {t} = useTranslation()

    const truncateText = (text: string, maxLength: number = 50) => {
        if (!text || text.length <= maxLength) return text
        return text.substring(0, maxLength) + '...'
    }
    // 检查是否有任何激活的服务器已启用
    const anyEnable = mcpServers
        .filter((s) => s.isActive)
        .some((server) => enabledMCPs.some((enabledServer) => enabledServer.name === server.name))

    // 如果没有可用的激活服务器，不显示组件
    if (activedMcpServers.length === 0) {
        return null
    }

    return (
        <Dropdown
            dropdownRender={() => (
                <div ref={menuRef} className="ant-dropdown-menu">
                    <DropdownHeader className="dropdown-header">
                        <div className="header-content">
                            <h4>{t('settings.mcp.title')}</h4>
                            <div className="enable-all-container">
                                <Switch 
                                    size="small" 
                                    checked={anyEnable} 
                                    onChange={toggleAllMCPs}
                                    disabled={disabled}
                                />
                            </div>
                        </div>
                    </DropdownHeader>
                    {mcpServers.length > 0 ? (
                        mcpServers
                            .filter((s) => s.isActive)
                            .map((server) => (
                                <McpServerItems key={server.name} className="ant-dropdown-menu-item">
                                    <div className="server-info">
                                        <div className="server-name">{server.name}</div>
                                        {server.description && (
                                            <Tooltip title={server.description} placement="bottom">
                                                <div
                                                    className="server-description">{truncateText(server.description)}</div>
                                            </Tooltip>
                                        )}
                                        {server.baseUrl && <div className="server-url">{server.baseUrl}</div>}
                                    </div>
                                    <Switch
                                        size="small"
                                        checked={enabledMCPs.some((s) => s.name === server.name)}
                                        onChange={() => toggleEnableMCP(server)}
                                        disabled={disabled}
                                    />
                                </McpServerItems>
                            ))
                    ) : (
                        <div className="ant-dropdown-menu-item-group">
                            <div className="ant-dropdown-menu-item no-results">{t('settings.mcp.noServers')}</div>
                        </div>
                    )}
                </div>
            )}
            trigger={['click']}
            open={isOpen}
            onOpenChange={(open) => {
                if (!disabled) {
                    setIsOpen(open)
                }
            }}
            disabled={disabled}
            overlayClassName="mention-models-dropdown">
            <ToolbarButton type="text" ref={dropdownRef} disabled={disabled}>
                <CodeOutlined style={{
                    color: enabledMCPs.length > 0 ? '#d97757' : 'var(--color-icon)',
                    opacity: disabled ? 0.5 : 1
                }}/>
            </ToolbarButton>
        </Dropdown>
    )
}

const McpServerItems = styled.div`
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;

    .server-info {
        flex: 1;
        overflow: hidden;

        .server-name {
            font-weight: 500;
            font-size: 14px;
            @ts-ignore
            color: var(--color-text-1);
        }

        .server-description {
            font-size: 12px;
            @ts-ignore
            color: var(--color-text-3);
            margin-top: 2px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .server-url {
            font-size: 11px;
            @ts-ignore
            color: var(--color-text-4);
            margin-top: 2px;
        }
    }
`

const DropdownHeader = styled.div`
    padding: 8px 12px;
    @ts-ignore
    border-bottom: 1px solid var(--color-border);
    margin-bottom: 4px;

    .header-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 12px;
    }

    h4 {
        margin: 0;
        @ts-ignore
        color: var(--color-text-1);
        font-size: 14px;
        font-weight: 500;
    }

    .enable-all-container {
        display: flex;
        align-items: center;
        gap: 8px;
    }
`

export default MCPToolsButton