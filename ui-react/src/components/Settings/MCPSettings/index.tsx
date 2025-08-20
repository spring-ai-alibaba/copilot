import React, { FC, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { SettingContainer, SettingDivider, SettingGroup, SettingTitle } from '..'
import AddMcpServerPopup from './AddMcpServerPopup'
import EditMcpJsonPopup from './EditMcpJsonPopup'
import InstallNpxUv from './InstallNpxUv'
import NpxSearch from './NpxSearch'
import { MCPServer } from "@/types/mcp"
import useThemeStore from "@/stores/themeSlice"
import { HStack } from "@/components/Layout"
import useMCPStore from "@/stores/useMCPSlice"
import classNames from 'classnames'

const MCPSettings: FC = () => {
    const { t } = useTranslation()
    const { isDarkMode } = useThemeStore()
    const mcpServers = useMCPStore(state => state.getAllServers)
    const deleteServer = useMCPStore(state => state.deleteServer)
    const setServerActive = useMCPStore(state => state.setServerActive)
    const [loadingServer, setLoadingServer] = useState<string | null>(null)

    const handleDelete = async (serverName: string) => {
        if (window.confirm(t('settings.mcp.confirmDeleteMessage'))) {
            try {
                // 在 Electron 环境下使用 window.myAPI，否则使用 Zustand store
                if (window.electron && window.myAPI?.mcp?.deleteServer) {
                    await window.myAPI.mcp.deleteServer(serverName)
                } else {
                    await deleteServer(serverName)
                }
                console.info(t('settings.mcp.deleteSuccess'))
            } catch (error: any) {
                console.error(`${t('settings.mcp.deleteError')}: ${error.message}`)
            }
        }
    }

    const handleToggleActive = async (name: string, isActive: boolean) => {
        setLoadingServer(name)
        try {
            // 在 Electron 环境下使用 window.myAPI，否则使用 Zustand store
            if (window.electron && window.myAPI?.mcp?.setServerActive) {
                await window.myAPI.mcp.setServerActive(name, isActive)
            } else {
                await setServerActive(name, isActive)
            }
        } catch (error: any) {
            console.error(`${t('settings.mcp.toggleError')}: ${error.message}`)
        } finally {
            setLoadingServer(null)
        }
    }

    // 移除 Electron 限制，支持 Web 环境下的 MCP 服务器管理

    return (
        <SettingContainer theme={isDarkMode ? 'dark' : 'light'}>
            <InstallNpxUv />
            <SettingGroup theme={isDarkMode ? 'dark' : 'light'}>
                <SettingTitle>
                    <div className="flex items-center gap-2">
                        MCP 服务器
                        <button
                            className="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
                            title="配置模型上下文协议服务器"
                        >
                            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z" />
                            </svg>
                        </button>
                    </div>
                </SettingTitle>
                <SettingDivider />
                
                <div className="flex justify-between items-center mb-4">
                    <HStack gap={15} className="items-center">
                        <button
                            onClick={() => AddMcpServerPopup.show()}
                            className={classNames(
                                "px-4 py-2 text-sm font-medium rounded-lg",
                                "text-white bg-purple-600 hover:bg-purple-700",
                                "dark:bg-purple-600 dark:hover:bg-purple-700",
                                "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                                "transition-colors duration-200",
                                "flex items-center gap-2"
                            )}
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                            </svg>
                            添加 MCP 服务器
                        </button>
                        <button
                            onClick={() => EditMcpJsonPopup.show()}
                            className={classNames(
                                "px-4 py-2 text-sm font-medium rounded-lg",
                                "text-gray-700 dark:text-gray-300",
                                "bg-white dark:bg-gray-800",
                                "border border-gray-300 dark:border-gray-600",
                                "hover:bg-gray-50 dark:hover:bg-gray-700",
                                "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                                "transition-colors duration-200",
                                "flex items-center gap-2"
                            )}
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                            编辑 JSON
                        </button>
                    </HStack>
                </div>

                <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
                    <table className="w-full border-collapse bg-white dark:bg-gray-800">
                        <thead>
                            <tr className="border-b border-gray-200 dark:border-gray-700">
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-900 dark:text-gray-100 w-[160px]">
                                    名称
                                </th>
                                <th className="px-4 py-3 text-center text-sm font-medium text-gray-900 dark:text-gray-100 w-[70px]">
                                    类型
                                </th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-900 dark:text-gray-100">
                                    描述
                                </th>
                                <th className="px-4 py-3 text-center text-sm font-medium text-gray-900 dark:text-gray-100 w-[80px]">
                                    启用
                                </th>
                                <th className="px-4 py-3 text-center text-sm font-medium text-gray-900 dark:text-gray-100 w-[100px]">
                                    操作
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {mcpServers().length === 0 ? (
                                <tr>
                                    <td colSpan={5} className="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
                                        未配置服务器
                                    </td>
                                </tr>
                            ) : (
                                mcpServers().map((server) => (
                                    <tr 
                                        key={server.name}
                                        className={classNames(
                                            "border-b border-gray-200 dark:border-gray-700",
                                            !server.isActive && "bg-gray-50 dark:bg-gray-900/50 opacity-70"
                                        )}
                                    >
                                        <td className="px-4 py-3 text-sm text-gray-900 dark:text-gray-100 font-medium truncate">
                                            {server.name}
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <span className={classNames(
                                                "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
                                                server.baseUrl ? 
                                                    "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300" :
                                                    "bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-300"
                                            )}>
                                                {server.baseUrl ? 'SSE' : 'STDIO'}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
                                            <div className="line-clamp-2 break-all">
                                                {server.description || (
                                                    <span className="italic">无描述</span>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <button
                                                onClick={() => handleToggleActive(server.name, !server.isActive)}
                                                className={classNames(
                                                    "relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out",
                                                    server.isActive ? "bg-purple-600" : "bg-gray-200 dark:bg-gray-700"
                                                )}
                                                disabled={loadingServer === server.name}
                                            >
                                                <span 
                                                    className={classNames(
                                                        "pointer-events-none inline-block h-4 w-4 transform rounded-full shadow ring-0 transition-all duration-300 ease-in-out",
                                                        server.isActive ? "translate-x-5" : "translate-x-0.5",
                                                        "mt-0.5",
                                                        loadingServer === server.name ? "bg-transparent flex items-center justify-center" : "bg-white"
                                                    )}
                                                    style={{ willChange: 'transform' }}
                                                >
                                                    {loadingServer === server.name && (
                                                        <svg 
                                                            className={classNames(
                                                                "w-4 h-4 animate-spin", 
                                                                server.isActive ? "text-white" : "text-gray-700 dark:text-gray-300"
                                                            )} 
                                                            xmlns="http://www.w3.org/2000/svg" 
                                                            fill="none" 
                                                            viewBox="0 0 24 24"
                                                        >
                                                            <circle className="opacity-40" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3"></circle>
                                                            <path className="opacity-80" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                        </svg>
                                                    )}
                                                </span>
                                            </button>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <div className="flex items-center justify-center gap-2">
                                                <button
                                                    onClick={() => AddMcpServerPopup.show({ server })}
                                                    className={classNames(
                                                        "p-1 rounded-md",
                                                        "text-purple-600 hover:text-purple-700",
                                                        "dark:text-purple-500 dark:hover:text-purple-400",
                                                        "focus:outline-none focus:ring-2 focus:ring-purple-500/50"
                                                    )}
                                                >
                                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                                    </svg>
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(server.name)}
                                                    className={classNames(
                                                        "p-1 rounded-md",
                                                        "text-red-600 hover:text-red-700",
                                                        "dark:text-red-500 dark:hover:text-red-400",
                                                        "focus:outline-none focus:ring-2 focus:ring-red-500/50"
                                                    )}
                                                >
                                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                    </svg>
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </SettingGroup>
            <NpxSearch />
        </SettingContainer>
    )
}

export default MCPSettings