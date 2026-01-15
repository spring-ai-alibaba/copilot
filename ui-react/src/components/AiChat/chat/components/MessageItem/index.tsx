import React, {memo, useCallback, useMemo, useState} from "react";
import ReactMarkdown from "react-markdown";
import {ArtifactView} from "../ArtifactView";
import {ImageGrid} from "../ImageGrid";
import {Message} from "ai";

import classNames from "classnames";
import useUserStore from "../../../../../stores/userSlice";
import useThemeStore from "@/stores/themeSlice";
import hljs from "highlight.js";
import remarkGfm from "remark-gfm";
import "highlight.js/styles/github.css"; // 亮色主题
import "highlight.js/styles/github-dark.css"; // 暗色主题
import {message} from "antd";
import {useTranslation} from 'react-i18next';

const codeStyles = `
  .hljs-attr {
    color: #36ACE3;
  }
  .hljs-string {
    color: #FF6B6B;
  }
  .hljs-number {
    color: #FF9F43;
  }
  .hljs-boolean {
    color: #2ED573;
  }
  .hljs-null {
    color: #A367DC;
  }
  
  .dark .hljs-attr {
    color: #9CDCFE;
  }
  .dark .hljs-string {
    color: #CE9178;
  }
  .dark .hljs-number {
    color: #B5CEA8;
  }
  .dark .hljs-boolean {
    color: #4EC9B0;
  }
  .dark .hljs-null {
    color: #C586C0;
  }
`;

function filterContent(message) {
  let cloneMessage
  if (message.role === 'user') {
    cloneMessage = JSON.parse(JSON.stringify(message))
    // 使用正则表达式移除<weD2c>标签及其内容，添加 s 标志以匹配多行内容
    const weD2cRegex = /<weD2c>[\s\S]*?<\/weD2c>/g;
    cloneMessage.content = cloneMessage.content.replace(weD2cRegex, '');
    cloneMessage.parts = cloneMessage.parts.map(item => {
      if(item.type === 'text'){
        item.text = item.text.replace(weD2cRegex, '')
        return item
      }
      return item
    })
  }
  return cloneMessage ? cloneMessage : message;
}
// 添加处理流式parts的函数
export const processStreamParts = (parts: Message["parts"]): string => {
  let result = "";
  let thinkContent = "";

  // 首先处理所有reasoning类型的内容
  parts?.forEach((part) => {
    if (part.type === "reasoning") {
      thinkContent += part.reasoning;
    }
  });

  // 如果有reasoning内容，将其转换为markdown引用格式
  if (thinkContent) {
    result +=
      thinkContent
        .split("\n")
        .map((line) => `> ${line}`)
        .join("\n") + "\n\n";
  }

  // 添加其他类型的内容
  parts?.forEach((part) => {
    if (part.type === "text") {
      // 检查是否包含think标签，如果有则进行处理
      if (isThinkContent(part.text)) {
        result += processThinkContent(part.text);
      } else {
        result += part.text;
      }
    }
  });

  const artifactIndex = result.indexOf("<boltArtifact");
  const preContent =
    artifactIndex > 0 ? result.substring(0, artifactIndex) : result;
  return preContent.trim();
};

interface MessageItemProps {
  message: Message & {
    experimental_attachments?: Array<{
      id: string;
      name: string;
      type: string;
      localUrl: string;
      contentType: string;
      url: string;
    }>;
  };
  isLoading: boolean;
  isEndMessage: boolean;
  handleRetry: () => void;
  onUpdateMessage?: (messageId: string, content: {
    text: string;
    type: string;
  }[]) => void;
}

const isArtifactContent = (content: string) => {
  return content.includes("<boltArtifact");
};

const getArtifactTitle = (content: string) => {
  const match = content.match(/title="([^"]+)"/);
  return match ? match[1] : "Task";
};

// 如果生成结束了，user在最后，就要展示重试
const isShowRetry = (isUser: boolean, isLoading: boolean, isEndMessage:boolean) => {
  return isUser && !isLoading && isEndMessage;
};

// 添加图片预览组件
const ImagePreview = ({
  src,
  onClose,
}: {
  src: string;
  onClose: () => void;
}) => {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
      onClick={onClose}
    >
      <div className="relative max-w-[90vw] max-h-[90vh]">
        <img
          src={src}
          alt="Preview"
          className="object-contain max-w-full max-h-[90vh]"
        />
        <button
          className="absolute text-white top-4 right-4 hover:text-gray-300"
          onClick={onClose}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="w-6 h-6"
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
    </div>
  );
};

// 添加获取首字母的辅助函数
const getInitial = (name: string | null | undefined): string => {
  if (!name) return "U";

  // 尝试获取第一个英文字母
  const englishMatch = name.match(/[a-zA-Z]/);
  if (englishMatch) {
    return englishMatch[0].toUpperCase();
  }

  // 如果没有英文字母，返回第一个字符
  return name.charAt(0).toUpperCase();
};

// 添加自定义样式处理
const customHighlight = (code: string, language: string) => {
  try {
    if (language.toLowerCase() === 'json') {
      // 自定义 JSON 语法高亮
      const jsonStr = code.trim();
      return jsonStr.replace(
        /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
        function (match) {
          let colorClass = 'hljs-string'; // 字符串颜色
          if (/^"/.test(match)) {
            if (/:$/.test(match)) {
              colorClass = 'hljs-attr'; // key 的颜色
            }
          } else if (/true|false/.test(match)) {
            colorClass = 'hljs-boolean'; // 布尔值颜色
          } else if (/null/.test(match)) {
            colorClass = 'hljs-null'; // null 的颜色
          } else {
            colorClass = 'hljs-number'; // 数字颜色
          }
          return `<span class="${colorClass}">${match}</span>`;
        }
      );
    }

    // 其他语言使用 highlight.js
    return hljs.highlight(code.trim(), {
      language: language || "plaintext",
      ignoreIllegals: true,
    }).value;
  } catch (e) {
    return code;
  }
};

// 使用 memo 包裹 CodeBlock 组件以避免不必要的重渲染
export const CodeBlock = memo(
  ({
    language,
    filePath,
    children,
  }: {
    language: string;
    filePath?: string;
    children: string;
  }) => {
    const [copied, setCopied] = useState(false);
    const [isExpanded, setIsExpanded] = useState(true); // 添加展开/折叠状态
    const { isDarkMode } = useThemeStore();

    const highlightedCode = useMemo(() => {
      return customHighlight(children, language);
    }, [children, language]);

    const handleCopy = useCallback(async () => {
      try {
        await navigator.clipboard.writeText(children);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      } catch (err) {
        // Failed to copy
      }
    }, [children]);

    // 判断是否为 JSON 内容
    const isJson = language.toLowerCase() === 'json';

    return (
      <>
        <style>{codeStyles}</style>
        <div className="my-1">
          <div className="rounded-lg overflow-hidden group border border-[#E1E4E8] dark:border-[#333] shadow-sm">
            <div className="flex items-center justify-between px-2 py-0.5 border-b border-[#E1E4E8] dark:border-[#333] bg-[#F6F8FA] dark:bg-[#2d2d2d]">
              <div className="flex items-center gap-2.5">
                {filePath ? (
                  <div className="flex items-center gap-2">
                    <svg
                      className="w-4 h-4 text-[#6e7681] dark:text-gray-400"
                      viewBox="0 0 16 16"
                      fill="currentColor"
                    >
                      <path d="M2 1.75C2 .784 2.784 0 3.75 0h6.586c.464 0 .909.184 1.237.513l2.914 2.914c.329.328.513.773.513 1.237v9.586A1.75 1.75 0 0 1 13.25 16h-9.5A1.75 1.75 0 0 1 2 14.25Zm1.75-.25a.25.25 0 0 0-.25.25v12.5c0 .138.112.25.25.25h9.5a.25.25 0 0 0 .25-.25V6h-2.75A1.75 1.75 0 0 1 9 4.25V1.5Zm6.75.062V4.25c0 .138.112.25.25.25h2.688l-.011-.013-2.914-2.914-.013-.011Z" />
                    </svg>
                    <span className="text-xs font-medium text-gray-600 dark:text-gray-400">
                      {filePath}
                    </span>
                  </div>
                ) : language ? (
                  <div className="text-xs font-medium text-gray-600 dark:text-gray-400">
                    {language}
                  </div>
                ) : null}
              </div>
              <div className="flex items-center gap-1">
                {/* 为 JSON 添加展开/折叠按钮 */}
                {isJson && (
                  <button
                    onClick={() => setIsExpanded(!isExpanded)}
                    className="flex items-center justify-center w-6 h-6 p-1 text-gray-500 transition-opacity opacity-0 group-hover:opacity-100 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
                    title={isExpanded ? "折叠" : "展开"}
                  >
                    <svg
                      className={`w-4 h-4 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path
                        d="M19 9l-7 7-7-7"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </button>
                )}
                <button
                  onClick={handleCopy}
                  className="flex items-center justify-center w-6 h-6 p-1 text-gray-500 transition-opacity opacity-0 group-hover:opacity-100 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
                >
                  {copied ? (
                    <svg
                      className="w-3 h-3"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <path
                        d="M20 6L9 17l-5-5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  ) : (
                    <svg
                      className="w-3 h-3"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                    >
                      <rect x="9" y="9" width="13" height="13" rx="2" />
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                    </svg>
                  )}
                </button>
              </div>
            </div>
            <div className="overflow-hidden bg-[#FAFBFC] dark:bg-[#1E1E1E]">
              <div
                className={`overflow-x-auto scrollbar-none px-3 py-1 ${
                  isDarkMode ? "hljs-dark" : "hljs-light"
                }`}
              >
                <pre className={`!m-0 leading-[1.2] transition-all duration-200 ${
                  isJson && !isExpanded ? 'max-h-0' : 'max-h-none'
                }`}>
                  <code
                    dangerouslySetInnerHTML={{ __html: highlightedCode }}
                    className={`language-${language || "plaintext"} text-xs text-[#1A1A1A] dark:text-[#D4D4D4]`}
                  />
                </pre>
                {/* JSON 内容折叠时显示渐变遮罩 */}
                {isJson && !isExpanded && (
                  <div className="h-8 -mt-8 bg-gradient-to-t from-[#FAFBFC] dark:from-[#1E1E1E] to-transparent pointer-events-none" />
                )}
              </div>
            </div>
          </div>
        </div>
      </>
    );
  },
  (prevProps, nextProps) => {
    return (
      prevProps.language === nextProps.language &&
      prevProps.filePath === nextProps.filePath &&
      prevProps.children === nextProps.children
    );
  }
);

CodeBlock.displayName = "CodeBlock";

// 添加检查是否是 think 内容的函数
export const isThinkContent = (content: string) => {
  // 使用正则表达式检查完整的 <think>...</think> 标签对
  return /<think>[\s\S]*?<\/think>/.test(content);
};

// 修改 processThinkContent 函数
export const processThinkContent = (content: string) => {
  // 使用正则表达式直接提取 think 标签内的内容和外面的内容
  const thinkRegex = /<think>([\s\S]*?)<\/think>/g;
  let lastIndex = 0;
  let result = "";

  let match;
  while ((match = thinkRegex.exec(content)) !== null) {
    // 处理标签前的文本
    if (match.index > lastIndex) {
      result += content.substring(lastIndex, match.index);
    }

    // 处理 think 标签内的内容，转换为引用格式
    const thinkText = match[1];
    result +=
      thinkText
        .split("\n")
        .map((line) => `> ${line}`)
        .join("\n") + "\n\n";

    lastIndex = match.index + match[0].length;
  }

  // 处理剩余的文本
  if (lastIndex < content.length) {
    result += content.substring(lastIndex);
  }

  return result.trim();
};

// 修改 ToolInvocationCard 组件
const ToolInvocationCard = ({
  toolInvocation,
  messageId,
  onUpdateMessage,
}: {
  toolInvocation: {
    args: any;
    state: string;
    step?: number;
    toolCallId: string;
    toolName: string;
  };
  messageId: string;
  onUpdateMessage?: (messageId: string, content: {
    text: string;
    type: string;
  }[]) => void;
}) => {
  const [isLoading, setIsLoading] = useState(false);
  const [hasInvoked, setHasInvoked] = useState(false);  // 添加状态跟踪是否已调用
  const { t } = useTranslation();
  const toolName = toolInvocation.toolName.split('.');
  if (toolName.length > 2){
    throw new Error(`Tool name: ${toolInvocation.toolName} must be 'string.string'`);
  }
  const handleRetry = async () => {
    try {
      setIsLoading(true);
      // MCP tool calls are not available in Web mode
      message.error(t('settings.mcp.notAvailableInWebMode') || 'MCP tools are not available in Web mode');
    } catch (error) {
      message.error(t('settings.mcp.addError'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-2 mb-4">
      {/* MCP 工具使用提示 */}
      <div className="text-xs text-gray-500 dark:text-gray-400">
        {toolName?.[1]} {t('chat.buttons.mcp_tools')}: {toolName?.[2]}
      </div>

      <div className="relative rounded-lg border dark:border-gray-700 bg-gray-50 dark:bg-[#1e1e1e] overflow-hidden">
        <div className="flex items-center gap-2 px-3 py-2 border-b dark:border-gray-700 bg-white dark:bg-[#2d2d2d]">
          <svg className="w-4 h-4 text-gray-500" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {toolName?.[2] || t('settings.mcp.title')}
          </span>
        </div>
        <div className="p-3">
          <pre className="whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300 font-mono">
            {JSON.stringify(toolInvocation?.args, null, 2)}
          </pre>
        </div>

        {/* 右下角按钮 - 只在未调用过时显示 */}
        {!hasInvoked && (
          <div className="absolute bottom-3 right-3">
            <button
              onClick={handleRetry}
              disabled={isLoading}
              className={classNames(
                "flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-md transition-colors",
                "text-white bg-purple-600 hover:bg-purple-700 dark:bg-purple-600 dark:hover:bg-purple-700",
                "disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
              )}
            >
              {isLoading ? (
                <svg className="w-3 h-3 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path
                    className="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  />
                </svg>
              ) : (
                <svg className="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              )}
              <span>
                {isLoading ? t('settings.mcp.invoke_tooling') : t('settings.mcp.invoke_tool')}
              </span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export const MessageItem: React.FC<MessageItemProps> = ({
  message,
  isLoading,
  isEndMessage,
  handleRetry,
  onUpdateMessage,
}) => {
  const { user } = useUserStore();
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [isCollapsed, setIsCollapsed] = useState<boolean>(false);
  const [copied, setCopied] = useState(false);
  const isUser = message.role === "user";
  const handleCopyMessage = useCallback(async () => {
    try {
      const textContent = processStreamParts(message.parts);
      await navigator.clipboard.writeText(textContent);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("复制失败:", err);
    }
  }, [message.parts]);

  const initial = isUser ? getInitial(user?.username) : "AI";
  const avatarColor = isUser
    ? "bg-purple-500 dark:bg-purple-600"
    : "bg-gray-100 dark:bg-[rgba(45,45,45)]";
  return (
    <div className="group relative">
      <div className="flex flex-col gap-2 px-2 py-1.5 rounded-lg hover:bg-gray-50 dark:hover:bg-white/[0.02] transition-colors">
        <div className="flex items-start gap-2">
          <div
            className={classNames(
              "w-6 h-6 rounded-full flex items-center justify-center text-xs border border-gray-200 dark:border-gray-700/50 overflow-hidden",
              avatarColor,
              isUser ? "text-white" : "text-gray-700 dark:text-gray-300"
            )}
          >
            {isUser ? (
              user?.avatar ? (
                <img
                  src={user.avatar}
                  alt={user.username || "User"}
                  className="object-cover w-full h-full"
                />
              ) : (
                <span className="font-medium">{initial}</span>
              )
            ) : (
              <span className="font-medium">AI</span>
            )}
          </div>
          <div className="flex-1 min-w-0">
            {isArtifactContent(message.content) ? (
              <ArtifactView
                isUser={isUser}
                title={getArtifactTitle(message.content)}
                message={message}
                isComplete={!isLoading}
                conversationId={message.id?.split('-')[0]} // 从消息ID提取会话ID
                userId={localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user') || '{}').id || 'default-user' : 'default-user'} // 从本地存储获取用户ID
              />
            ) : (
              <div className="flex flex-col gap-1">
                <div className="leading-relaxed prose-sm prose text-gray-900 dark:text-gray-100 dark:prose-invert max-w-none">
                  {/* 修改工具调用卡片的渲染 */}
                  {message.parts?.map((part, index) => {
                    if (part.type === "tool-invocation") {
                      return (
                        <ToolInvocationCard
                          key={index}
                          toolInvocation={part.toolInvocation}
                          messageId={message.id}
                          onUpdateMessage={onUpdateMessage}
                        />
                      );
                    }
                    return null;
                  })}

                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      code({ node, className, children, ...props }) {
                        const match = /language-(\w+)(?::(.+))?/.exec(
                          className || ""
                        );
                        const isInline = !match;

                        if (isInline) {
                          return (
                            <code
                              className="font-mono text-sm px-1.5 py-0.5 rounded bg-gray-50 dark:bg-[#282828] text-gray-800 dark:text-gray-300"
                              {...props}
                            >
                              {children}
                            </code>
                          );
                        }

                        const language = match?.[1] || "";
                        const filePath = match?.[2];
                        // 确保 children 是字符串类型
                        const content = Array.isArray(children)
                          ? children.join("")
                          : String(children).replace(/\n$/, "");

                        return (
                          <CodeBlock language={language} filePath={filePath}>
                            {content}
                          </CodeBlock>
                        );
                      },
                      pre({ children }) {
                        // 直接返回子元素，不需要额外的包装
                        return children;
                      },
                      p({ children }) {
                        return <p className="mb-2 last:mb-0">{children}</p>;
                      },
                      ul({ children }) {
                        return (
                          <ul className="pl-4 mb-2 space-y-1 list-disc">
                            {children}
                          </ul>
                        );
                      },
                      ol({ children }) {
                        return (
                          <ol className="pl-4 mb-2 space-y-1 list-decimal">
                            {children}
                          </ol>
                        );
                      },
                      li({ children }) {
                        return (
                          <li className="text-gray-700 dark:text-gray-300">
                            {children}
                          </li>
                        );
                      },
                      a({ children, href }) {
                        return (
                          <a
                            href={href}
                            className="text-purple-600 dark:text-purple-400 hover:text-purple-700 dark:hover:text-purple-300 hover:underline"
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {children}
                          </a>
                        );
                      },
                      blockquote({ children }) {
                        return (
                          <blockquote className="relative py-2 pl-4 my-2 text-sm text-gray-600 border-l-4 border-purple-200 rounded dark:border-purple-800 dark:text-gray-400 bg-purple-50 dark:bg-purple-900/10 group">
                            <div
                              className={`overflow-hidden transition-all duration-200 ${
                                isCollapsed ? "h-4" : "max-h-none"
                              }`}
                            >
                              {children}
                            </div>
                            {/* 渐变遮罩 */}
                            {isCollapsed && (
                              <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-purple-50 dark:from-[rgba(88,28,135,0.1)] to-transparent" />
                            )}
                            {/* 折叠/展开按钮 */}
                            <button
                              onClick={() => setIsCollapsed(!isCollapsed)}
                              className="absolute p-1 text-purple-600 rounded-full bottom-1 right-2 dark:text-purple-400 hover:text-purple-700 dark:hover:text-purple-300 hover:bg-purple-100 dark:hover:bg-purple-900/20"
                            >
                              <svg
                                className="w-4 h-4"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                              >
                                {isCollapsed ? (
                                  <path
                                    d="M12 5v14M5 12h14"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                ) : (
                                  <path
                                    d="M5 12h14"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                  />
                                )}
                              </svg>
                            </button>
                          </blockquote>
                        );
                      },
                      strong({ children }) {
                        return <strong>{children}</strong>;
                      },
                      em({ children }) {
                        return <em>{children}</em>;
                      },
                      table({ children }) {
                        return (
                          <div className="my-4 overflow-x-auto">
                            <table className="min-w-full border-collapse border dark:border-gray-700">
                              {children}
                            </table>
                          </div>
                        );
                      },
                      thead({ children }) {
                        return (
                          <thead className="bg-purple-50 dark:bg-purple-900/20">
                            {children}
                          </thead>
                        );
                      },
                      tbody({ children }) {
                        return (
                          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                            {children}
                          </tbody>
                        );
                      },
                      tr({ children }) {
                        return (
                          <tr className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                            {children}
                          </tr>
                        );
                      },
                      th({ children }) {
                        return (
                          <th className="px-4 py-2 text-sm font-medium text-left text-purple-700 dark:text-purple-200 border dark:border-gray-700">
                            {children}
                          </th>
                        );
                      },
                      td({ children }) {
                        return (
                          <td className="px-4 py-2 text-sm text-gray-700 dark:text-gray-300 border dark:border-gray-700">
                            {children}
                          </td>
                        );
                      },
                    }}
                  >
                    {(() => {
                      const filterMessages = filterContent(message)
                      return processStreamParts(filterMessages.parts);
                    })()}
                  </ReactMarkdown>
                </div>
              </div>
            )}

            {message.experimental_attachments &&
              message.experimental_attachments.length > 0 && (
                <div className="mt-2">
                  <ImageGrid
                    images={message.experimental_attachments}
                    onImageClick={(url) => setPreviewImage(url)}
                  />
                </div>
              )}
          </div>
        </div>
      </div>
      {previewImage && (
        <ImagePreview
          src={previewImage}
          onClose={() => setPreviewImage(null)}
        />
      )}
      <>
        {!isArtifactContent(message.content) ? (
          <div className="flex items-center justify-end ">
            <button
              onClick={handleCopyMessage}
              className="opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800"
            >
              {copied ? (
                <svg
                  className="w-4 h-4 text-green-500"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    d="M20 6L9 17l-5-5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              ) : (
                <svg
                  className="w-4 h-4 text-gray-500 dark:text-gray-400"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              )}
            </button>
            {isShowRetry(isUser, isLoading, isEndMessage) ? (
              <button
                onClick={() => {
                  handleRetry?.()
                }}
                className="opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800"
                title="重试"
              >
                <svg
                  className="w-4 h-4 text-gray-500 dark:text-gray-400"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
            ) : null}
          </div>
        ) : null}
      </>
    </div>
  );
};
