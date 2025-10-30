import React, {forwardRef, useEffect, useImperativeHandle, useState,} from "react";
import {useTranslation} from "react-i18next";
import useMCPStore from "@/stores/useMCPSlice";
import {MCPServer} from "@/types/mcp";
import {TopView} from "@/components/TopView";
import classNames from "classnames";
import {EditorState} from "@codemirror/state";
import {EditorView} from "@codemirror/view";
import {json} from "@codemirror/lang-json";
import {HighlightStyle, syntaxHighlighting} from "@codemirror/language";
import {tags as t} from "@lezer/highlight";

interface Props {
  resolve: (data: any) => void;
}

const CustomModal = ({
  children,
  title,
  open,
  onOk,
  onCancel,
  okText,
  cancelText,
  confirmLoading,
}) => {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/30 backdrop-blur-sm"
        onClick={onCancel}
      />

      {/* Modal */}
      <div
        className="fixed inset-0 overflow-y-auto"
        style={{ isolation: "isolate" }}
      >
        <div className="flex min-h-full items-center justify-center p-6">
          <div className="relative w-full max-w-4xl transform rounded-xl bg-white dark:bg-gray-800 shadow-2xl transition-all">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200/80 dark:border-gray-700/80">
              <h3 className="text-base font-semibold text-gray-900 dark:text-gray-100">
                {title}
              </h3>
              <button
                onClick={onCancel}
                className="rounded-md p-1 text-gray-400 hover:text-gray-500 dark:text-gray-500 dark:hover:text-gray-400"
              >
                <svg
                  className="h-5 w-5"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>

            {/* Content */}
            <div className="px-6 py-4">{children}</div>

            {/* Footer */}
            <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-200/80 dark:border-gray-700/80">
              <button
                type="button"
                onClick={onCancel}
                className={classNames(
                  "px-4 py-2 text-sm font-medium rounded-lg",
                  "text-gray-700 dark:text-gray-300",
                  "bg-white dark:bg-gray-800",
                  "border border-gray-300 dark:border-gray-600",
                  "hover:bg-gray-50 dark:hover:bg-gray-700/50",
                  "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
                  "transition duration-150 ease-in-out"
                )}
              >
                {cancelText}
              </button>
              <button
                type="button"
                onClick={onOk}
                disabled={confirmLoading}
                className={classNames(
                  "px-4 py-2 text-sm font-medium rounded-lg",
                  "text-white",
                  "bg-purple-600 hover:bg-purple-700",
                  "dark:bg-purple-600 dark:hover:bg-purple-700",
                  "focus:outline-none focus:ring-2 focus:ring-purple-500",
                  "transition duration-150 ease-in-out",
                  "disabled:opacity-50 disabled:cursor-not-allowed",
                  "flex items-center gap-2"
                )}
              >
                {confirmLoading && (
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
                {okText}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// 自定义主题样式，匹配图片中的配色
const jsonHighlightStyle = HighlightStyle.define([
  { tag: t.string, color: "#CE9178" }, // 字符串值为红褐色
  { tag: t.propertyName, color: "#9CDCFE" }, // 属性名为浅蓝色
  { tag: t.number, color: "#B5CEA8" }, // 数字为浅绿色
  { tag: t.bool, color: "#569CD6" }, // 布尔值为蓝色
  { tag: t.null, color: "#569CD6" }, // null 为蓝色
  { tag: t.punctuation, color: "#D4D4D4" }, // 标点符号为浅灰色
  { tag: t.bracket, color: "#D4D4D4" }, // 括号为浅灰色
  { tag: t.invalid, color: "#F44747" }, // 无效的 JSON 为红色
]);

const JsonEditor = forwardRef<
  { format: () => void },
  {
    value: string;
    onChange: (e: any) => void;
    onFocus: () => void;
    onError: (error: string) => void;
  }
>(({ value, onChange, onFocus, onError }, ref) => {
  const editorRef = React.useRef<HTMLDivElement>(null);
  const viewRef = React.useRef<EditorView>();
  const { t } = useTranslation();

  // 添加防抖函数
  const debounce = (func: Function, wait: number) => {
    let timeout: NodeJS.Timeout;
    return (...args: any[]) => {
      clearTimeout(timeout);
      timeout = setTimeout(() => func(...args), wait);
    };
  };

  // 格式化方法
  const formatEditorContent = () => {
    if (!viewRef.current) return;
    try {
      const currentText = viewRef.current.state.doc.toString();
      const selection = viewRef.current.state.selection.main;
      const parsed = JSON.parse(currentText);
      const formatted = JSON.stringify(parsed, null, 2);

      // 计算文本映射关系
      const oldLines = currentText.split("\n");
      const newLines = formatted.split("\n");

      let fromLine = 0;
      let fromCol = 0;
      let toLine = 0;
      let toCol = 0;

      // 查找选区起始位置在新文本中的对应位置
      let pos = 0;
      for (let i = 0; i < oldLines.length; i++) {
        if (pos + oldLines[i].length >= selection.from) {
          fromLine = i;
          fromCol = selection.from - pos;
          break;
        }
        pos += oldLines[i].length + 1; // +1 for newline
      }

      // 查找选区结束位置在新文本中的对应位置
      pos = 0;
      for (let i = 0; i < oldLines.length; i++) {
        if (pos + oldLines[i].length >= selection.to) {
          toLine = i;
          toCol = selection.to - pos;
          break;
        }
        pos += oldLines[i].length + 1; // +1 for newline
      }

      // 尝试在新文本中找到对应的位置
      let newFromPos = 0;
      let newToPos = 0;

      // 计算新的起始位置
      for (let i = 0; i < Math.min(fromLine, newLines.length); i++) {
        newFromPos += i > 0 ? newLines[i - 1].length + 1 : 0;
      }
      newFromPos += Math.min(
        fromCol,
        newLines[Math.min(fromLine, newLines.length - 1)].length
      );

      // 计算新的结束位置
      for (let i = 0; i < Math.min(toLine, newLines.length); i++) {
        newToPos += i > 0 ? newLines[i - 1].length + 1 : 0;
      }
      newToPos += Math.min(
        toCol,
        newLines[Math.min(toLine, newLines.length - 1)].length
      );

      // 确保位置有效
      newFromPos = Math.max(0, Math.min(newFromPos, formatted.length));
      newToPos = Math.max(newFromPos, Math.min(newToPos, formatted.length));

      // 应用格式化并尝试保持选区
      viewRef.current.dispatch({
        changes: {
          from: 0,
          to: viewRef.current.state.doc.length,
          insert: formatted,
        },
        selection: {
          anchor: newFromPos,
          head: newToPos,
        },
      });

      onError(""); // 清除错误信息
    } catch (error: any) {
      onError(error.message || t("settings.mcp.jsonFormatError"));
    }
  };

  // 只在手动格式化时调用，不再自动格式化
  useEffect(() => {
    if (!editorRef.current) return;

    const state = EditorState.create({
      doc: value,
      extensions: [
        EditorView.lineWrapping,
        json(),
        syntaxHighlighting(jsonHighlightStyle),
        EditorView.theme({
          "&": {
            fontSize: "13px",
            height: "60vh",
            backgroundColor: "#1E1E1E", // VS Code 暗色主题背景色
            isolation: "isolate", // 增加滚动隔离
          },
          ".cm-scroller": {
            fontFamily: "Consolas, Monaco, monospace",
            lineHeight: "1.5",
            overflow: "auto",
            padding: "8px 0",
            isolation: "isolate", // 增加滚动隔离
            scrollbarWidth: "thin",
            "&::-webkit-scrollbar": {
              width: "8px",
              height: "8px",
            },
            "&::-webkit-scrollbar-track": {
              background: "transparent",
            },
            "&::-webkit-scrollbar-thumb": {
              background: "#555",
              borderRadius: "4px",
            },
            "&::-webkit-scrollbar-thumb:hover": {
              background: "#666",
            },
          },
          ".cm-content": {
            caretColor: "#D4D4D4",
            minHeight: "60vh",
            padding: "0 12px",
            color: "#D4D4D4", // 默认文本颜色
          },
          ".cm-gutters": {
            backgroundColor: "#1E1E1E",
            border: "none",
            borderRight: "1px solid #404040",
            color: "#858585", // 行号颜色
            paddingRight: "8px",
          },
          ".cm-activeLineGutter": {
            backgroundColor: "#282828",
          },
          ".cm-activeLine": {
            backgroundColor: "#282828",
          },
          "&.cm-editor": {
            "&.cm-focused": {
              outline: "none",
            },
          },
          ".cm-selectionBackground": {
            backgroundColor: "#264F78",
          },
          "&.cm-focused .cm-selectionBackground": {
            backgroundColor: "#264F78",
          },
          ".cm-cursor": {
            borderLeftColor: "#D4D4D4",
          },
          ".cm-lineNumbers": {
            minWidth: "40px",
          },
        }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            const newValue = update.state.doc.toString();
            onChange(newValue);
            // 不再自动触发格式化
          }
        }),
      ],
    });

    const view = new EditorView({
      state,
      parent: editorRef.current,
    });

    viewRef.current = view;

    return () => {
      view.destroy();
    };
  }, []);

  useEffect(() => {
    if (viewRef.current && value !== viewRef.current.state.doc.toString()) {
      viewRef.current.dispatch({
        changes: {
          from: 0,
          to: viewRef.current.state.doc.length,
          insert: value,
        },
      });
    }
  }, [value]);

  // 暴露格式化方法给父组件
  useImperativeHandle(ref, () => ({
    format: formatEditorContent,
  }));

  return (
    <div
      ref={editorRef}
      className={classNames(
        "rounded-lg overflow-hidden",
        "border border-gray-200 dark:border-gray-700",
        "focus-within:ring-2 focus-within:ring-purple-500/50",
        "transition duration-200 ease-in-out",
        "mb-4",
        "bg-white dark:bg-gray-900"
      )}
      onFocus={onFocus}
      onWheel={(e) => e.stopPropagation()}
    />
  );
});

const PopupContainer: React.FC<Props> = ({ resolve }) => {
  const [open, setOpen] = useState(true);
  const [jsonConfig, setJsonConfig] = useState("");
  const [jsonSaving, setJsonSaving] = useState(false);
  const [jsonError, setJsonError] = useState("");
  const [isFormatting, setIsFormatting] = useState(false);
  const mcpServers = useMCPStore((state) => state.servers);
  const setServers = useMCPStore((state) => state.setServers);
  const { t } = useTranslation();
  const editorRef = React.useRef(null);

  useEffect(() => {
    try {
      const mcpServersObj: Record<string, any> = {};
      mcpServers.forEach((server) => {
        const { name, ...serverData } = server;
        mcpServersObj[name] = serverData;
      });
      const standardFormat = { mcpServers: mcpServersObj };
      const formattedJson = JSON.stringify(standardFormat, null, 2);
      setJsonConfig(formattedJson);
      setJsonError("");
    } catch (error) {
      console.error("Failed to format JSON:", error);
      setJsonError(t("settings.mcp.jsonFormatError"));
    }
  }, [mcpServers, t]);

  const onOk = async () => {
    setJsonSaving(true);
    try {
      if (!jsonConfig.trim()) {
        // 在 Electron 环境下使用 window.myAPI，否则使用 Zustand store
        if (window.electron && window.myAPI?.mcp?.setServers) {
          window.myAPI.mcp.setServers([]);
        } else {
          setServers([]);
        }
        console.log(t("settings.mcp.jsonSaveSuccess"));
        setJsonError("");
        setJsonSaving(false);
        setOpen(false);
        TopView.hide(TopViewKey);
        return;
      }
      const parsedConfig = JSON.parse(jsonConfig);

      if (
        !parsedConfig.mcpServers ||
        typeof parsedConfig.mcpServers !== "object"
      ) {
        throw new Error(t("settings.mcp.invalidMcpFormat"));
      }

      const serversArray: MCPServer[] = [];
      for (const [name, serverConfig] of Object.entries(
        parsedConfig.mcpServers
      )) {
        const server: MCPServer = {
          name,
          isActive: false,
          ...(serverConfig as any),
        };
        serversArray.push(server);
      }
      console.log("serversArray", serversArray);
      // 在 Electron 环境下使用 window.myAPI，否则使用 Zustand store
      if (window.electron && window.myAPI?.mcp?.setServers) {
        window.myAPI.mcp.setServers(serversArray);
      } else {
        setServers(serversArray);
      }
      console.log("serversArray", serversArray);

      console.log(t("settings.mcp.jsonSaveSuccess"));
      setJsonError("");
      setOpen(false);
      TopView.hide(TopViewKey);
    } catch (error: any) {
      console.error("Failed to save JSON config:", error);
      setJsonError(error.message || t("settings.mcp.jsonSaveError"));
      console.error(t("settings.mcp.jsonSaveError"));
    } finally {
      setJsonSaving(false);
    }
  };

  const onCancel = () => {
    setOpen(false);
    TopView.hide(TopViewKey);
  };

  const onClose = () => {
    resolve({});
  };

  EditMcpJsonPopup.hide = onCancel;

  const formatJson = () => {
    if (editorRef.current && !isFormatting) {
      setIsFormatting(true);
      editorRef.current.format();
      // 1秒后恢复按钮状态
      setTimeout(() => {
        setIsFormatting(false);
      }, 1000);
    }
  };

  return (
    <CustomModal
      title={t("settings.mcp.editJson")}
      open={open}
      onOk={onOk}
      onCancel={onCancel}
      okText={t("common.confirm")}
      cancelText={t("common.cancel")}
      confirmLoading={jsonSaving}
    >
      {jsonError && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800/30 dark:bg-red-900/10">
          <div className="flex">
            <svg
              className="h-5 w-5 text-red-400"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
              />
            </svg>
            <div className="ml-3">
              <p className="text-sm text-red-600 dark:text-red-400">
                {jsonError}
              </p>
            </div>
          </div>
        </div>
      )}

      <div className="space-y-4">
        <div className="flex justify-end mb-2">
          <button
            onClick={formatJson}
            disabled={isFormatting}
            className={classNames(
              "px-3 py-1.5 text-sm font-medium rounded-lg",
              "text-gray-700 dark:text-gray-300",
              "bg-white dark:bg-gray-800",
              "border border-gray-300 dark:border-gray-600",
              !isFormatting
                ? "hover:bg-gray-50 dark:hover:bg-gray-700/50"
                : "opacity-70 cursor-not-allowed",
              "focus:outline-none focus:ring-2 focus:ring-purple-500/50",
              "transition duration-150 ease-in-out",
              "flex items-center gap-2"
            )}
          >
            <svg
              className={classNames("w-4 h-4", isFormatting && "animate-pulse")}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 6h16M4 12h16m-7 6h7"
              />
            </svg>
            {isFormatting ? t("common.formatting") : t("common.format")}
          </button>
        </div>

        <JsonEditor
          ref={editorRef}
          value={jsonConfig}
          onChange={(e) => setJsonConfig(e)}
          onFocus={() => setJsonError("")}
          onError={setJsonError}
        />

        <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700/50 dark:bg-gray-900/50">
          <div className="flex items-start">
            <div className="flex-shrink-0">
              <svg
                className="h-5 w-5 text-gray-400"
                viewBox="0 0 20 20"
                fill="currentColor"
              >
                <path
                  fillRule="evenodd"
                  d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                />
              </svg>
            </div>
            <p className="ml-3 text-sm text-gray-600 dark:text-gray-400">
              {t("settings.mcp.jsonModeHint")}
            </p>
          </div>
        </div>
      </div>
    </CustomModal>
  );
};

const TopViewKey = "EditMcpJsonPopup";

export default class EditMcpJsonPopup {
  static topviewId = 0;

  static hide() {
    TopView.hide(TopViewKey);
  }

  static show() {
    return new Promise<any>((resolve) => {
      TopView.show(
        <PopupContainer
          resolve={(v) => {
            resolve(v);
            TopView.hide(TopViewKey);
          }}
        />,
        TopViewKey
      );
    });
  }
}
