import React, {useEffect, useRef, useState} from "react"
import classNames from "classnames"
import {Tooltip} from "antd"
import {ChevronDown, Image} from "lucide-react"
import type {UploadButtonsProps} from "./types"

import {useTranslation} from "react-i18next"
import {IModelOption} from "../.."
import useChatStore from "@/stores/chatSlice"
import {aiProvierIcon} from "./config"
import MCPToolsButton from "./MCPToolsButton"

export const UploadButtons: React.FC<UploadButtonsProps> = ({
  isLoading,
  isUploading,
  append,
  onImageClick,
  baseModal,
  messages,
  handleSubmitWithFiles,
  setMessages,
  setBaseModal,
}) => {
  const [isOpen, setIsOpen] = useState(false)
  const { t } = useTranslation()
  const dropdownRef = useRef<HTMLDivElement>(null)
  const { modelOptions, clearImages } = useChatStore()
  const [isFigmaModalOpen, setIsFigmaModalOpen] = useState(false)
  const [figmaUrl, setFigmaUrl] = useState(() => localStorage.getItem('figmaUrl') || '')
  const [figmaToken, setFigmaToken] = useState(() => localStorage.getItem('figmaToken') || '')

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false)
      }
    }

    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  const handleModelSelect = (model: IModelOption) => {
    setBaseModal(model)
    setIsOpen(false)
    console.log("Selected model:", model.key)
  }

  const handleFigmaSubmit = () => {
    localStorage.setItem('figmaUrl', figmaUrl)
    localStorage.setItem('figmaToken', figmaToken)
    setIsFigmaModalOpen(false)
  }

  // 定义一个可复用的按钮样式组件
  const ToolbarButton = React.forwardRef<HTMLButtonElement, any>((props, ref) => (
    <button
      ref={ref}
      {...props}
      className={classNames(
        "p-2 text-gray-600 dark:text-gray-500 flex hover:text-gray-900 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-500/20 rounded-lg transition-all duration-200",
        props.disabled && "opacity-50 cursor-not-allowed",
        props.className
      )}
    >
      {props.children}
    </button>
  ))
  const isElectron = typeof window !== 'undefined' && !!window.electron;

  const canUseMCP = isElectron && baseModal.functionCall;


  return (
    <div className="flex items-center">
      <div className="flex items-center gap-2">
        {/* MCP Tools Button - Disabled when functionCall is false */}
        {isElectron && (
          <Tooltip
            title={
              <div className="text-xs">
                <div className="font-medium mb-1">
                  {!canUseMCP
                    ? t("chat.buttons.mcp_disabled")
                    : t("chat.buttons.mcp_tools")}
                </div>
                <div className="text-gray-300">
                  {!canUseMCP
                    ? t("chat.buttons.not_support_mcp")
                    : t("chat.buttons.click_to_use_mcp")}
                </div>
              </div>
            }
            placement="bottom"
          >
            <span className={!canUseMCP ? "cursor-not-allowed" : ""}>
              <MCPToolsButton
                ToolbarButton={ToolbarButton}
                disabled={!canUseMCP}
              />
            </span>
          </Tooltip>
        )}

        {/* figma todo */}
        {/* <Tooltip
          title={
            <div className="text-xs">
              <div className="font-medium mb-1">
                {t("chat.buttons.figma_integration")}
              </div>
            </div>
          }
          placement="bottom"
        >
          <button
            type="button"
            onClick={() => setIsFigmaModalOpen(true)}
            className="p-2 text-gray-600 dark:text-gray-500 flex hover:text-gray-900 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-500/20 rounded-lg transition-all duration-200"
          >
            <Figma className="w-4 h-4" />
          </button>
        </Tooltip> */}

        <Tooltip
          title={
            <div className="text-xs">
              <div className="font-medium mb-1">
                {isLoading || isUploading || !baseModal.useImage
                  ? t("chat.buttons.upload_disabled")
                  : t("chat.buttons.upload_image")}
              </div>
              <div className="text-gray-300">
                {isLoading || isUploading
                  ? t("chat.buttons.waiting")
                  : !baseModal.useImage
                    ? t("chat.buttons.not_support_image")
                    : t("chat.buttons.click_to_upload")}
              </div>
            </div>
          }
          placement="bottom"
        >
          <ToolbarButton
            type="button"
            onClick={onImageClick}
            disabled={isLoading || isUploading || !baseModal.useImage}
            // className={classNames(
            //   "p-2 text-gray-600 dark:text-gray-500 flex hover:text-gray-900 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-500/20 rounded-lg transition-all duration-200",
            //   (isLoading || isUploading || !baseModal.useImage) &&
            //   "opacity-50 cursor-not-allowed"
            // )}
          >
            <Image className="w-4 h-4" />
          </ToolbarButton>
        </Tooltip>

      </div>

      <div className="relative ml-2" ref={dropdownRef}>
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          className={classNames(
            "flex items-center justify-between w-[150px] px-2 py-1 text-[11px] text-gray-700 dark:text-gray-300 bg-transparent dark:bg-[#252525] rounded-md transition-colors duration-200",
            isOpen
              ? "bg-gray-100 dark:bg-[#252525]"
              : "hover:bg-gray-100 dark:hover:bg-[#252525]"
          )}
        >
          <div className="flex items-center gap-2">
            {(() => {
              const Icon =
                aiProvierIcon[(baseModal.provider || "").toLowerCase()];
              return Icon ? <Icon className="w-4 h-4" /> : null;
            })()}
            <span>{baseModal.name}</span>
          </div>
          <ChevronDown
            className={classNames(
              "w-3 h-3 text-gray-500 dark:text-gray-400 transition-transform duration-200",
              isOpen ? "-rotate-180" : "rotate-0"
            )}
          />
        </button>

        {isOpen && (
          <div className="absolute bottom-full left-0 mb-1 w-[160px] bg-white dark:bg-[#18181a] border border-gray-200 dark:border-gray-600/30 rounded-lg shadow-lg overflow-hidden z-50">
            <div className="flex flex-col w-full">
              {modelOptions.map((model, index) => (
                <button
                  key={model.key || `model-${index}`}
                  onClick={(e) => {
                    e.stopPropagation()
                    e.preventDefault()
                    handleModelSelect(model as IModelOption)
                    clearImages()
                  }}
                  className={classNames(
                    "w-full px-3 py-1.5 flex justify-between text-left text-[11px] transition-colors duration-200",
                    "hover:bg-gray-100 dark:hover:bg-[#252525]",
                    baseModal.key === model.key
                      ? "text-blue-600 dark:text-blue-400"
                      : "text-gray-700 dark:text-gray-300"
                  )}
                >
                  <div className="flex items-center gap-2">
                    {(() => {
                      const Icon =
                        aiProvierIcon[(model.provider || "").toLowerCase()];
                      return Icon ? <Icon className="w-4 h-4" /> : null;
                    })()}
                    {model.name}
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
