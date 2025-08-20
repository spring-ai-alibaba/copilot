import React, { FC, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { SettingRow, SettingSubtitle } from '..'
import classNames from 'classnames'

const InstallNpxUv: FC = () => {
    const [isUvInstalled, setIsUvInstalled] = useState(true)
    const [isBunInstalled, setIsBunInstalled] = useState(true)
    const [isInstallingUv, setIsInstallingUv] = useState(false)
    const [isInstallingBun, setIsInstallingBun] = useState(false)
    const { t } = useTranslation()

    const checkBinaries = async () => {
        try {
            
            const uvExists = await window.myAPI.isBinaryExist('uv')
            
            const bunExists = await window.myAPI.isBinaryExist('bun')

            setIsUvInstalled(uvExists)
            setIsBunInstalled(bunExists)
        } catch (error) {
            console.error(error)
        }
    }

    const installUV = async () => {
        try {
            setIsInstallingUv(true)
            await window.myAPI.installUVBinary()
            setIsUvInstalled(true)
        } catch (error: any) {
            console.error(`${t('settings.mcp.installError')}: ${error.message}`)
            checkBinaries()
        } finally {
            setIsInstallingUv(false)
        }
    }

    const installBun = async () => {
        try {
            setIsInstallingBun(true)
            await window.myAPI.installBunBinary()
            setIsBunInstalled(true)
        } catch (error: any) {
            console.error(`${t('settings.mcp.installError')}: ${error.message}`)
            checkBinaries()
        } finally {
            setIsInstallingBun(false)
        }
    }

    useEffect(() => {
        checkBinaries()
    }, [])

    if (isUvInstalled && isBunInstalled) {
        return null
    }

    const AlertBanner = ({ isInstalled, isInstalling, name, onInstall }) => (
        <div className={classNames(
            "rounded-lg border px-4 py-3",
            "bg-amber-50 dark:bg-amber-900/20",
            "border-amber-200 dark:border-amber-800/30"
        )}>
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <svg 
                        className="h-5 w-5 text-amber-400 dark:text-amber-300" 
                        viewBox="0 0 20 20" 
                        fill="currentColor"
                    >
                        <path 
                            fillRule="evenodd" 
                            d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" 
                            clipRule="evenodd" 
                        />
                    </svg>
                    <SettingSubtitle className="!m-0 text-amber-800 dark:text-amber-200">
                        {isInstalled ? `${name} Installed` : `${name} ${t('settings.mcp.missingDependencies')}`}
                    </SettingSubtitle>
                </div>
                <button
                    onClick={onInstall}
                    disabled={isInstalling}
                    className={classNames(
                        "px-3 py-1.5 text-sm font-medium rounded-lg",
                        "text-white bg-amber-500 hover:bg-amber-600",
                        "dark:bg-amber-600 dark:hover:bg-amber-700",
                        "focus:outline-none focus:ring-2 focus:ring-amber-500/50",
                        "transition-colors duration-200",
                        "disabled:opacity-50 disabled:cursor-not-allowed",
                        "flex items-center gap-2"
                    )}
                >
                    {isInstalling && (
                        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                            <circle 
                                className="opacity-25" 
                                cx="12" 
                                cy="12" 
                                r="10" 
                                stroke="currentColor" 
                                strokeWidth="4" 
                            />
                            <path 
                                className="opacity-75" 
                                fill="currentColor" 
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" 
                            />
                        </svg>
                    )}
                    {isInstalling ? t('settings.mcp.dependenciesInstalling') : t('settings.mcp.install')}
                </button>
            </div>
        </div>
    )

    return (
        <div className="flex flex-col gap-3 mb-5">
            {!isUvInstalled && (
                <AlertBanner
                    isInstalled={isUvInstalled}
                    isInstalling={isInstallingUv}
                    name="UV"
                    onInstall={installUV}
                />
            )}
            {!isBunInstalled && (
                <AlertBanner
                    isInstalled={isBunInstalled}
                    isInstalling={isInstallingBun}
                    name="Bun"
                    onInstall={installBun}
                />
            )}
        </div>
    )
}

export default InstallNpxUv