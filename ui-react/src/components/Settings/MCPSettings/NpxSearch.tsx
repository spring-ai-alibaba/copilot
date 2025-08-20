import React, { FC, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { npxFinder } from 'npx-scope-finder'
import { SettingDivider, SettingGroup, SettingTitle } from '..'
import AddMcpServerPopup from './AddMcpServerPopup'
import { MCPServer } from "@/types/mcp"
import useThemeStore from "@/stores/themeSlice"
import classNames from 'classnames'

interface SearchResult {
    name: string
    description: string
    version: string
    usage: string
    npmLink: string
    fullName: string
}

const NpxSearch: FC = () => {
    const { isDarkMode } = useThemeStore()
    const { t } = useTranslation()
    const [npmScope, setNpmScope] = useState('@modelcontextprotocol')
    const [searchLoading, setSearchLoading] = useState(false)
    const [searchResults, setSearchResults] = useState<SearchResult[]>([])

    const handleMarketClick = (market: string) => {
        setNpmScope(market)
        handleMarketSearch(market)
    }

    const handleMarketSearch = async (market: string) => {
        if (!market.trim()) {
            console.log(t('settings.mcp.npx_list.scope_required'))
            return
        }
        setSearchLoading(true)

        try {
            const packages = await npxFinder(market)
            const formattedResults = packages.map((pkg) => ({
                key: pkg.name,
                name: pkg.name || '',
                description: pkg.description || 'No description available',
                version: pkg.version || 'Latest',
                usage: `npx ${pkg.name}`,
                npmLink: pkg.links?.npm || `https://www.npmjs.com/package/${pkg.name}`,
                fullName: pkg.name || ''
            }))

            setSearchResults(formattedResults)

            if (formattedResults.length === 0) {
                console.info(t('settings.mcp.npx_list.no_packages'))
            }
        } catch (error: unknown) {
            if (error instanceof Error) {
                console.error(`${t('settings.mcp.npx_list.search_error')}: ${error.message}`)
            } else {
                console.error(t('settings.mcp.npx_list.search_error'))
            }
        } finally {
            setSearchLoading(false)
        }
    }

    const handleNpmSearch = async () => {
        await handleMarketSearch(npmScope)
    }

    return (
        <SettingGroup theme={isDarkMode ? 'dark' : 'light'}>
            <SettingTitle>{t('settings.mcp.npx_list.title')}</SettingTitle>
            <SettingDivider />
            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                {t('settings.mcp.npx_list.desc')}
            </p>

            <div className="space-y-4">
                <div className="flex gap-2">
                    <input
                        type="text"
                        value={npmScope}
                        onChange={(e) => setNpmScope(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleNpmSearch()}
                        placeholder={t('settings.mcp.npx_list.scope_placeholder')}
                        className={classNames(
                            "flex-1 px-3 py-2 text-sm rounded-lg",
                            "bg-white dark:bg-gray-900",
                            "border border-gray-300 dark:border-gray-600",
                            "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                            "placeholder-gray-400 dark:placeholder-gray-500",
                            "transition duration-200"
                        )}
                    />
                    <button
                        onClick={handleNpmSearch}
                        disabled={searchLoading}
                        className={classNames(
                            "px-4 py-2 text-sm font-medium rounded-lg",
                            "text-white bg-purple-600 hover:bg-purple-700",
                            "dark:bg-purple-600 dark:hover:bg-purple-700",
                            "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                            "transition-colors duration-200",
                            "flex items-center gap-2",
                            "disabled:opacity-50 disabled:cursor-not-allowed"
                        )}
                    >
                        {searchLoading ? (
                            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                            </svg>
                        ) : (
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                            </svg>
                        )}
                        {t('settings.mcp.npx_list.search')}
                    </button>
                </div>

                <div className="flex flex-wrap gap-2 mt-2">
                    <button 
                        className="px-3 py-1 text-xs font-medium rounded-lg bg-gray-100 hover:bg-gray-200 dark:bg-gray-800 dark:hover:bg-gray-700 text-gray-800 dark:text-gray-200 transition-colors"
                        onClick={() => handleMarketClick('@mcpmarket')}
                    >
                        @mcpmarket
                    </button>
                    <button 
                        className="px-3 py-1 text-xs font-medium rounded-lg bg-gray-100 hover:bg-gray-200 dark:bg-gray-800 dark:hover:bg-gray-700 text-gray-800 dark:text-gray-200 transition-colors"
                        onClick={() => handleMarketClick('@modelcontextprotocol')}
                    >
                        @modelcontextprotocol
                    </button>
                    <button 
                        className="px-3 py-1 text-xs font-medium rounded-lg bg-gray-100 hover:bg-gray-200 dark:bg-gray-800 dark:hover:bg-gray-700 text-gray-800 dark:text-gray-200 transition-colors"
                        onClick={() => handleMarketClick('@gongrzhe')}
                    >
                        @gongzhe
                    </button>
                </div>

                {searchLoading ? (
                    <div className="flex justify-center items-center py-12">
                        <div className="animate-spin rounded-full h-8 w-8 border-2 border-purple-500 border-t-transparent" />
                    </div>
                ) : searchResults.length > 0 ? (
                    <div className="overflow-x-auto overflow-y-auto rounded-lg border border-gray-200 dark:border-gray-700">
                        <table className="w-full table-fixed border-collapse bg-white dark:bg-gray-800">
                            <thead>
                                <tr className="border-b border-gray-200 dark:border-gray-700">
                                    <th className="px-2 py-2 text-left text-sm font-medium text-gray-900 dark:text-gray-100 w-[20%]">
                                        {t('settings.mcp.npx_list.package_name')}
                                    </th>
                                    <th className="px-2 py-2 text-left text-sm font-medium text-gray-900 dark:text-gray-100 w-[50%]">
                                        {t('settings.mcp.npx_list.description')}
                                    </th>
                                    <th className="px-2 py-2 text-left text-sm font-medium text-gray-900 dark:text-gray-100 w-[15%]">
                                        {t('settings.mcp.npx_list.version')}
                                    </th>
                                    <th className="px-2 py-2 text-center text-sm font-medium text-gray-900 dark:text-gray-100 w-[15%]">
                                        {t('settings.mcp.npx_list.actions')}
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {searchResults.map((result) => (
                                    <tr key={result.name} className="border-b border-gray-200 dark:border-gray-700">
                                        <td className="px-2 py-2 text-sm text-gray-900 dark:text-gray-100 break-words">
                                            {result.name}
                                        </td>
                                        <td className="px-2 py-2">
                                            <div className="space-y-1">
                                                <p className="text-sm text-gray-900 dark:text-gray-100 line-clamp-2" title={result.description}>
                                                    {result.description}
                                                </p>
                                                <p className="text-sm text-gray-500 dark:text-gray-400 truncate" title={`${t('settings.mcp.npx_list.usage')}: ${result.usage}`}>
                                                    {t('settings.mcp.npx_list.usage')}: {result.usage}
                                                </p>
                                                <a
                                                    href={result.npmLink}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="text-sm text-purple-600 hover:text-purple-700 dark:text-purple-400 dark:hover:text-purple-300 block truncate"
                                                    title={result.npmLink}
                                                >
                                                    {result.npmLink}
                                                </a>
                                            </div>
                                        </td>
                                        <td className="px-2 py-2 text-sm text-gray-900 dark:text-gray-100 break-words">
                                            {result.version}
                                        </td>
                                        <td className="px-2 py-2 text-center">
                                            <button
                                                onClick={() => {
                                                    const tempServer: MCPServer = {
                                                        name: result.name,
                                                        description: `${result.description}\n\n${t('settings.mcp.npx_list.usage')}: ${result.usage}\n${t('settings.mcp.npx_list.npm')}: ${result.npmLink}`,
                                                        command: 'npx',
                                                        args: ['-y', result.fullName],
                                                        isActive: true
                                                    }
                                                    AddMcpServerPopup.show({ server: tempServer, create: true })
                                                }}
                                                className={classNames(
                                                    "w-full px-2 py-1 text-xs font-medium rounded-lg",
                                                    "text-white bg-purple-600 hover:bg-purple-700",
                                                    "dark:bg-purple-600 dark:hover:bg-purple-700",
                                                    "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                                                    "transition-colors duration-200"
                                                )}
                                            >
                                                {t('settings.mcp.addServer')}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : null}
            </div>
        </SettingGroup>
    )
}

export default NpxSearch