import { openFile } from "../../../../WeIde/emit";
import React, { useMemo, useState, useEffect } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useFileStore } from "../../../../WeIde/stores/fileStore";
import classNames from "classnames";

import useTerminalStore from "@/stores/terminalSlice";

import {
  CodeBlock,
  isThinkContent,
  processStreamParts,
  processThinkContent,
} from "../MessageItem";
import { parseFileFromContext } from "../../../utils/index";
import { Message } from "ai";

interface Task {
  status: "done" | "parsing";
  text: string;
  filePath?: string;
}

// 添加新的类型定义
type CommandStatus = "idle" | "running" | "completed";

interface ArtifactViewProps {
  isUser: boolean;
  title: string;
  message: Message;
  isComplete?: boolean;
}

export const ArtifactView: React.FC<ArtifactViewProps> = ({
  isUser,
  title,
  isComplete,
  message,
}) => {
  const content = message.content;
  const { setFiles, updateContent } = useFileStore();
  const [isExpanded, setIsExpanded] = useState(true);
  const [fileStates, setFileStates] = useState<
    Map<string, { status: Task["status"]; order: number }>
  >(new Map());
  // 添加命令状态管理
  const [commandStatus, setCommandStatus] = useState<
    Record<string, CommandStatus>
  >({});

  const {getTerminal} = useTerminalStore();

  // 处理 pre/post artifact 内容，应用 think 标签处理
  const preArtifactContent = useMemo(() => {
    const artifactIndex = content.indexOf("<boltArtifact");
    const preContent =
      artifactIndex > 0 ? content.substring(0, artifactIndex) : "";
    return message.reasoning ? processStreamParts(message.parts) : preContent;
  }, [content]);
  const postArtifactContent = useMemo(() => {
    const artifactEndIndex = content.lastIndexOf("</boltArtifact>");
    const postContent =
      artifactEndIndex !== -1
        ? content.substring(artifactEndIndex + "</boltArtifact>".length)
        : "";
    return isThinkContent(postContent)
      ? processThinkContent(postContent)
      : postContent;
  }, [content]);

  // 提取文件路径
  const filePaths = useMemo(() => {
    const matches = Array.from(
      content.matchAll(
        /<boltAction[^>]*type="file"[^>]*filePath="([^"]*)"[^>]*>/g
      )
    );
    return matches.map((match) => match[1]);
  }, [content]);

  // 使用 useEffect 处理文件打开
  useEffect(() => {
    const matches = Array.from(
      content.matchAll(
        /<boltAction[^>]*type="file"[^>]*filePath="([^"]*)"[^>]*>/g
      )
    );
    if (matches.length > 0 && !isComplete) {
      openFile(matches[matches.length - 1][1]);
    }
  }, [content]);

  // 初始化文件状态
  useEffect(() => {
    const newFileStates = new Map();
    filePaths.forEach((path, index) => {
      if (index === filePaths.length - 1) {
        newFileStates.set(path, {
          status: "parsing",
          order: index,
        });
      } else {
        newFileStates.set(path, {
          status: "done",
          order: index,
        });
      }
    });
    setFileStates(newFileStates);
  }, [filePaths]);

  // 当消息完成时，将所有任务标记为完成
  useEffect(() => {
    if (isComplete) {
      setFileStates((prev) => {
        const newStates = new Map(prev);
        filePaths.forEach((path) => {
          const fileState = newStates.get(path);
          if (fileState) {
            newStates.set(path, { ...fileState, status: "done" });
          }
        });
        return newStates;
      });
    }
  }, [isComplete, filePaths]);

  // 构建任务列表
  const tasks = useMemo(() => {
    return filePaths.map((filePath) => ({
      text: filePath,
      status: fileStates.get(filePath)?.status || "parsing",
    }));
  }, [filePaths, fileStates]);

  const npmCommands = useMemo(() => {
    const commands = [];
    // 使用正则表达式匹配 shell 命令
    const shellCommandRegex =
      /<boltAction\s+type="shell"\s*>([\s\S]*?)<\/boltAction>/g;
    // start情况下
    const startCommandRegex =
      /<boltAction\s+type="start"\s*>([\s\S]*?)<\/boltAction>/g;
    const matches = Array.from(content.matchAll(shellCommandRegex));
    const matchesByStart = Array.from(content.matchAll(startCommandRegex));
    
    matches.forEach((match) => {
      const command = match[1].trim();
      if (command.startsWith("npm install")) {
        commands.push({
          type: "install",
          command: command,
        });
      } else {
        commands.push({
          type: "other",
          command: command.replace(/\n/g, ""),
        });
      }
    });
    matchesByStart.forEach((match) => {
      const command = match[1].trim();
      if (command.startsWith("npm run")) {
        commands.push({
          type: "dev",
          command: command,
        });
      } else {
        commands.push({
          type: "other",
          command: command.replace(/\n/g, ""),
        });
      }
    });

    return commands;
  }, [content]);

  // 修改恢复单个文件的处理函数
  const handleRestoreFile = (filePath: string): void => {
    const fileContent = parseFileFromContext(filePath, content);
    if (fileContent) {
      updateContent(filePath, fileContent);
      openFile(filePath);
    }
  };

  return (
    <div className="space-y-3">
      {/* 显示 boltArtifact 之前的文本内容 */}
      {preArtifactContent && (
        <div className="text-gray-900 dark:text-gray-100 leading-relaxed prose dark:prose-invert prose-sm max-w-none">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              code({ node, className, children, ...props }) {
                const match = /language-(\w+)(?::(.+))?/.exec(className || "");
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
                      ? children.join('') 
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
                  <ul className="pl-4 mb-2 space-y-1 list-disc">{children}</ul>
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
                const [isCollapsed, setIsCollapsed] = useState(false);
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
            {preArtifactContent}
          </ReactMarkdown>
        </div>
      )}

      {/* 任务列表卡片 */}
      <div className="bg-white dark:bg-[#1a1a1c] rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700/50">
        <div
          className="border-b border-gray-200 dark:border-gray-700/50 px-3 py-2 flex items-center justify-between cursor-pointer hover:bg-gray-50 dark:hover:bg-[#28292b] transition-colors group"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          <div className="flex items-center gap-2">
            <h3 className="text-gray-700 dark:text-gray-200 font-medium text-sm">
              {title}
            </h3>
          </div>
          <div className="flex items-center gap-2">
            {/* 只在所有任务都完成时显示 Restore 按钮 */}
            {tasks.every((task) => task.status === "done") && !isUser && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  tasks.forEach((task) => handleRestoreFile(task.text));
                }}
                className="invisible group-hover:visible text-xs px-1.5 py-0.5 rounded bg-gray-100 hover:bg-gray-200 dark:bg-[#333] dark:hover:bg-[#444] text-gray-700 dark:text-gray-300 transition-all flex items-center gap-1 flex-shrink-0"
              >
                <svg
                  className="w-3 h-3"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path d="M3 12h1m8-9v1m8 8h1M5.6 5.6l.7.7m12.1-.7l-.7.7M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
                <span>Restore All</span>
              </button>
            )}
          </div>
        </div>

        <div
          className={`transition-all duration-200 ease-in-out overflow-hidden ${
            isExpanded ? " opacity-100" : "max-h-0 opacity-0"
          }`}
        >
          <div className="py-1 space-y-0.5">
            {tasks.map((task, i) => (
              <div
                key={i}
                className="flex items-center gap-2 px-2 py-1.5 hover:bg-gray-50 dark:hover:bg-[#28292b] group/item transition-colors"
              >
                <div className="flex-shrink-0">
                  {task.status === "done" && (
                    <span className="text-green-500 dark:text-green-400 text-sm">
                      ✓
                    </span>
                  )}
                  {task.status === "parsing" && (
                    <div className="w-4 h-4">
                      <div className="w-4 h-4 border-2 border-blue-500 dark:border-blue-400 border-t-transparent rounded-full animate-spin" />
                    </div>
                  )}
                </div>
                <div className="flex-1 flex items-center justify-between min-w-0">
                  <span
                    onClick={() => {
                      openFile(task.text);
                    }}
                    className={`text-sm ${
                      task.status === "done"
                        ? "text-gray-700 dark:text-gray-300"
                        : "text-gray-500 dark:text-gray-400"
                    } hover:underline cursor-pointer truncate`}
                  >
                    {task.text}
                  </span>
                  {(!isUser || title === "the current file") && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRestoreFile(task.text);
                      }}
                      className="invisible group-hover/item:visible text-xs px-1.5 py-0.5 rounded bg-gray-100 hover:bg-gray-200 dark:bg-[#333] dark:hover:bg-[#444] text-gray-700 dark:text-gray-300 transition-all flex items-center gap-1 flex-shrink-0 ml-2"
                    >
                      <svg
                        className="w-3 h-3"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <path d="M3 12h1m8-9v1m8 8h1M5.6 5.6l.7.7m12.1-.7l-.7.7M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
                      </svg>
                      <span>Restore</span>
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* NPM 命令区域 */}
        {isExpanded && npmCommands.length > 0 && (
          <div className="border-t border-gray-200 dark:border-gray-700/50">
            {npmCommands.map((cmd, index) => (
              <div
                key={cmd.type}
                className={classNames(
                  "bg-gray-50 dark:bg-[#232426] px-3 py-2 font-mono text-sm flex items-center gap-1.5 justify-between group/npm",
                  index !== npmCommands.length - 1 &&
                    "border-b border-gray-200 dark:border-gray-700/50"
                )}
              >
                <div className="flex items-center justify-between w-full">
                  {cmd.type === "install" ? (
                    <div
                      className="cursor-pointer"
                      onClick={async () => {
                        if (
                          commandStatus[cmd.command] === "running" ||
                          commandStatus[cmd.command] === "completed"
                        ) {
                          return;
                        }
                        try {
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "running",
                          }));
                          await getTerminal(0).executeCommand("npm install");
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "completed",
                          }));
                        } catch (error) {
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "idle",
                          }));
                        }
                      }}
                    >
                      <div className="flex w-full gap-1.5">
                        <>
                          <span className="text-green-600 dark:text-[#7ee787]">
                            npm
                          </span>
                          <span className="text-gray-600 dark:text-gray-400">
                            install
                          </span>
                        </>
                        <button
                          className={classNames(
                            "text-xs px-1.5 py-0.5 rounded transition-all flex items-center gap-1 flex-shrink-0 ml-24",
                            {
                              "invisible group-hover/npm:visible bg-gray-100 hover:bg-gray-200 dark:bg-[#333] dark:hover:bg-[#444] text-gray-700 dark:text-gray-300":
                                !commandStatus[cmd.command],
                              "bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-400 cursor-not-allowed":
                                commandStatus[cmd.command] === "completed",
                              "bg-blue-100 dark:bg-blue-600 text-blue-700 dark:text-white cursor-wait":
                                commandStatus[cmd.command] === "running",
                            }
                          )}
                          disabled={
                            commandStatus[cmd.command] === "running" ||
                            commandStatus[cmd.command] === "completed"
                          }
                        >
                          {commandStatus[cmd.command] === "running" ? (
                            <div className="flex items-center gap-1">
                              <div className="w-3 h-3 border-2 border-blue-500 dark:border-white border-t-transparent rounded-full animate-spin" />
                              <span>Installing...</span>
                            </div>
                          ) : commandStatus[cmd.command] === "completed" ? (
                            <div className="flex items-center gap-1">
                              <span className="text-green-500 dark:text-green-400">
                                ✓
                              </span>
                              <span>Installed</span>
                            </div>
                          ) : (
                            "install"
                          )}
                        </button>
                      </div>
                    </div>
                  ) : cmd.type === "dev" ? (
                    <div
                      className="cursor-pointer"
                      onClick={async () => {
                        if (
                          commandStatus[cmd.command] === "running" ||
                          commandStatus[cmd.command] === "completed"
                        ) {
                          return;
                        }
                        try {
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "running",
                          }));
                          await getTerminal(0).executeCommand("npm run dev");
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "completed",
                          }));
                        } catch (error) {
                          setCommandStatus((prev) => ({
                            ...prev,
                            [cmd.command]: "idle",
                          }));
                        }
                      }}
                    >
                      <div className="flex w-full gap-1.5">
                        <>
                          <span className="text-green-600 dark:text-[#7ee787]">
                            npm
                          </span>
                          <span className="text-gray-600 dark:text-gray-400">
                            run
                          </span>
                          <span className="text-[#79c0ff]">dev</span>
                        </>
                        <button
                          className={classNames(
                            "text-xs px-1.5 py-0.5 rounded transition-all flex items-center gap-1 flex-shrink-0 ml-24",
                            {
                              "invisible group-hover/npm:visible bg-gray-100 hover:bg-gray-200 dark:bg-[#333] dark:hover:bg-[#444] text-gray-700 dark:text-gray-300":
                                !commandStatus[cmd.command],
                              "bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-400 cursor-not-allowed":
                                commandStatus[cmd.command] === "completed",
                              "bg-blue-100 dark:bg-blue-600 text-blue-700 dark:text-white cursor-wait":
                                commandStatus[cmd.command] === "running",
                            }
                          )}
                          disabled={
                            commandStatus[cmd.command] === "running" ||
                            commandStatus[cmd.command] === "completed"
                          }
                        >
                          {commandStatus[cmd.command] === "running" ? (
                            <div className="flex items-center gap-1">
                              <div className="w-3 h-3 border-2 border-blue-500 dark:border-white border-t-transparent rounded-full animate-spin" />
                              <span>Starting...</span>
                            </div>
                          ) : commandStatus[cmd.command] === "completed" ? (
                            <div className="flex items-center gap-1">
                              <span className="text-green-500 dark:text-green-400">
                                ✓
                              </span>
                              <span>Started</span>
                            </div>
                          ) : (
                            "start"
                          )}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <span className="text-green-600 dark:text-[#7ee787]">
                        {cmd.command}
                      </span>
                    </>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 显示 boltArtifact 结尾后的文本内容 */}
      {postArtifactContent && (
        <div className="text-gray-900 dark:text-gray-100 leading-relaxed prose dark:prose-invert prose-sm max-w-none ">
          <ReactMarkdown
            components={{
              blockquote({ children }) {
                return (
                  <blockquote className="border-l-4 border-gray-300 dark:border-gray-600 pl-4 my-2 text-sm text-gray-600 dark:text-gray-400">
                    {children}
                  </blockquote>
                );
              },
            }}
          >
            {postArtifactContent}
          </ReactMarkdown>
        </div>
      )}
    </div>
  );
};
