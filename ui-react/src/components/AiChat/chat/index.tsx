import {useEffect, useMemo, useRef, useState, useCallback} from "react";
import {Message, useChat} from "ai/react";
import {toast} from "react-toastify";
import {uploadImage} from "@/api/chat";
import { useMemoryStore } from "@/stores/memorySlice";
import useChatStore from "../../../stores/chatSlice";
import {useFileStore} from "../../WeIde/stores/fileStore";
import {db} from "../../../utils/indexDB";
import {v4 as uuidv4} from "uuid";
import {eventEmitter} from "../utils/EventEmitter";
import {MessageItem} from "./components/MessageItem";
import {ChatInput, ChatMode} from "./components/ChatInput";
import Tips from "./components/Tips";
import {parseMessage} from "../../../utils/messagepParseJson";
import useUserStore from "../../../stores/userSlice";
import {useLimitModalStore} from "../../UserModal";
import {updateFileSystemNow} from "../../WeIde/services";
import {parseMessages, streamingFileManager, normalizeFilePath} from "../useSseMessageParser";
import {useTranslation} from "react-i18next";
import { apiUrl } from "@/api/base";
import useChatModeStore from "../../../stores/chatModeSlice";
import useTerminalStore from "@/stores/terminalSlice";
import {checkExecList, checkFinish} from "../utils/checkFinish";
import {useUrlData} from "@/hooks/useUrlData";
import useMCPTools from "@/hooks/useMCPTools";
import {FileSystemStatus} from "./components/FileSystemStatus";
import {handleFileSystemEvent, isFileSystemEvent} from "../utils/fileSystemEventHandler";
import {useConversationStore} from "@/stores/conversationSlice";
import {getConversationMessages} from "@/api/conversation";
import { LoaderCircle } from "lucide-react";
import { AppLogo } from "@/components/AppLogo";

type WeMessages = (Message & {
    experimental_attachments?: Array<{
        id: string;
        name: string;
        type: string;
        localUrl: string;
        contentType: string;
        url: string;
    }>
})[]
type TextUIPart = {
    type: 'text';
    /**
     * The text content.
     */
    text: string;
};
export const excludeFiles = [
    "components/weicon/base64.js",
    "components/weicon/icon.css",
    "components/weicon/index.js",
    "components/weicon/index.json",
    "components/weicon/index.wxml",
    "components/weicon/icondata.js",
    "components/weicon/index.css",
    "/miniprogram/components/weicon/base64.js",
    "/miniprogram/components/weicon/icon.css",
    "/miniprogram/components/weicon/index.js",
    "/miniprogram/components/weicon/index.json",
    "/miniprogram/components/weicon/index.wxml",
    "/miniprogram/components/weicon/icondata.js",
    "/miniprogram/components/weicon/index.css",
];

// 统一通过 apiUrl 构造请求地址，避免 APP_BASE_URL 未配置导致的 undefined 前缀

enum ModelTypes {
    Claude37sonnet = "claude-3-7-sonnet-20250219",
    Claude35sonnet = "claude-3-5-sonnet-20240620",
    gpt4oMini = "gpt-4o-mini",
    DeepseekR1 = "DeepSeek-R1",
    DeepseekV3 = "deepseek-chat",
}

export interface IModelOption {
    key: string;
    name: string;
    useImage: boolean;
    quota: number;
    from?: string;
    icon?: React.FC<React.SVGProps<SVGSVGElement>>;
    provider?: string;
    functionCall?: boolean;
}

function convertToBoltAction(obj: Record<string, string>): string {
    return Object.entries(obj)
        .filter(([filePath]) => !excludeFiles.includes(filePath))
        .map(
            ([filePath, content]) =>
                `<boltAction type="file" filePath="${filePath}">\n${content}\n</boltAction>`
        )
        .join("\n\n");
}

export const BaseChat = ({uuid: propUuid}: { uuid?: string }) => {
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const {otherConfig} = useChatStore();
    const {t} = useTranslation();
    const [checkCount, setCheckCount] = useState(0);
    // 切换会话/加载历史后，等消息真正渲染完成再滚动到底部（展示最新一条）
    const pendingScrollToBottomRef = useRef(false);
    // 流内首次拿到会话 ID 时只同步选中态，不把它误判成用户主动切换会话。
    const streamAssignedConversationIdRef = useRef<string | null>(null);

    const [baseModal, setBaseModal] = useState<IModelOption>({
        key: ModelTypes.Claude35sonnet,
        name: "Claude 3.5 Sonnet",
        useImage: true,
        from: "default",
        quota: 2,
        functionCall: true,
    });
    const {
        files,
        isFirstSend,
        isUpdateSend,
        setIsFirstSend,
        setIsUpdateSend,
        setFiles,
        setEmptyFiles,
        errors,
        updateContent,
        clearErrors,
        setOldFiles
    } = useFileStore();
    const {mode} = useChatModeStore();
    // 使用全局状态
    const {
        uploadedImages,
        addImages,
        removeImage,
        clearImages,
        setModelOptions,
        modelOptions,
    } = useChatStore();
    const {resetTerminals} = useTerminalStore();
    const {currentConversationId, setCurrentConversation} = useConversationStore();
    const filesInitObj = {} as Record<string, string>;
    const filesUpdateObj = {} as Record<string, string>;
    Object.keys(isFirstSend).forEach((key) => {
        isFirstSend[key] && (filesInitObj[key] = files[key]);
    });
    Object.keys(isUpdateSend).forEach((key) => {
        isUpdateSend[key] && (filesUpdateObj[key] = files[key]);
    });

    const initConvertToBoltAction = convertToBoltAction({
        ...filesInitObj,
        ...filesUpdateObj,
    });

    const updateConvertToBoltAction = convertToBoltAction(filesUpdateObj);

    const fetchingRef = useRef(false);
    const lastModeRef = useRef<ChatMode | null>(null);
    const modeRef = useRef(mode);

    useEffect(() => {
        modeRef.current = mode;
    }, [mode]);

    const fetchModelList = useCallback(() => {
        if (fetchingRef.current) {
            return;
        }

        fetchingRef.current = true;
        const currentMode = modeRef.current;
        const isBuilderMode = currentMode === ChatMode.Builder;
        const url = apiUrl(`/api/model/list?buildMode=${isBuilderMode}`);

        fetch(url, {
            method: "GET",
            headers: {
                "Content-Type": "application/json",
            },
        })
            .then((res) => res.json())
            .then((data) => {
                console.log("Fetched model list:", data);
                if (Array.isArray(data)) {
                    setModelOptions(data);
                    // 如果获取到模型列表，设置第一个模型为默认选择
                    if (data.length > 0) {
                        const firstModel = data[0];
                        console.log("Setting default model:", firstModel);
                        setBaseModal(firstModel);
                    }
                } else {
                    console.error("Invalid model list format:", data);
                    setModelOptions([]);
                }
            })
            .catch((error) => {
                console.error("Failed to fetch model list:", error);
                setModelOptions([]);
            })
            .finally(() => {
                fetchingRef.current = false;
            });
    }, []);

    useEffect(() => {
        if (lastModeRef.current === null || lastModeRef.current !== mode) {
            lastModeRef.current = mode;
            fetchModelList();
        }
    }, [mode, fetchModelList]);

    // 监听模型状态变化事件，重新获取模型列表
    useEffect(() => {
        const unsubscribe = eventEmitter.on('model:status-changed', () => {
            console.log('Model status changed, refreshing model list...');
            fetchModelList();
        });

        return () => {
            unsubscribe();
        };
    }, [fetchModelList]);

    // 监听list-progress事件状态更新
    useEffect(() => {
        const unsubscribe = eventEmitter.on('list-progress-update', (data: { operationId: string; filePath: string; content?: string; isLoading: boolean }) => {
            setListProgressStates(prev => ({
                ...prev,
                [data.operationId]: {
                    filePath: data.filePath,
                    content: data.content,
                    isLoading: data.isLoading
                }
            }));

            // 如果不是加载状态，延迟清除状态
            if (!data.isLoading) {
                setTimeout(() => {
                    setListProgressStates(prev => {
                        const newState = { ...prev };
                        delete newState[data.operationId];
                        return newState;
                    });
                }, 2000);
            }
        });

        return () => {
            unsubscribe();
        };
    }, []);



    useEffect(() => {
        if (
            (messages.length === 0 &&
                initConvertToBoltAction &&
                mode === ChatMode.Builder) ||
            (messages.length === 1 &&
                messages[0].id === "1" &&
                initConvertToBoltAction &&
                mode === ChatMode.Builder)
        ) {
            setMessagesa([
                {
                    id: "1",
                    role: "user",
                    content: `<boltArtifact id="hello-js" title="the current file">\n${initConvertToBoltAction}\n</boltArtifact>\n\n`,
                },
            ]);
            setMessages([
                {
                    id: "1",
                    role: "user",
                    content: `<boltArtifact id="hello-js" title="the current file">\n${initConvertToBoltAction}\n</boltArtifact>\n\n`,
                },
            ])
            scrollToBottom();
        }
    }, [initConvertToBoltAction]);

    useEffect(() => {
        if (
            messages.length > 1 &&
            updateConvertToBoltAction &&
            mode === ChatMode.Builder
        ) {
            setMessages((list) => {
                const newList = [...list];
                if (newList[newList.length - 1].id !== "2") {
                    newList.push({
                        id: "2",
                        role: "user",
                        content: `<boltArtifact id="hello-js" title="Currently modified files">\n${updateConvertToBoltAction}\n</boltArtifact>\n\n`,
                    });
                } else if (newList[newList.length - 1].id === "2") {
                    newList[newList.length - 1].content =
                        `<boltArtifact id="hello-js" title="Currently modified files">\n${updateConvertToBoltAction}\n</boltArtifact>\n\n`;
                }
                scrollToBottom();
                return newList;
            });
        }
    }, [updateConvertToBoltAction]);

    // 修改 UUID 的初始化逻辑和消息加载
    const [chatUuid, setChatUuid] = useState(() => propUuid || uuidv4());

    const refUuidMessages = useRef([]);

    useEffect(() => {
        if (checkCount >= 1) {
            checkFinish(messages[messages.length - 1].content, append, t);
            checkExecList(messages);
            setCheckCount(0);
        }
    }, [checkCount]);

    // 添加加载历史消息的函数（兼容旧版本）
    const loadChatHistory = async (uuid: string) => {
        try {
            const records = await db.getByUuid(uuid);
            if (records.length > 0) {
                const latestRecord = records[0];
                if (latestRecord?.data?.messages) {
                    const historyFiles = {};
                    const oldHistoryFiles = {};
                    // setEmptyFiles();
                    latestRecord.data.messages.forEach((message) => {
                        const {files: messageFiles} = parseMessage(message.content);
                        Object.assign(historyFiles, messageFiles);
                    });
                    const assistantRecord = latestRecord.data.messages.filter(e => e.role === "assistant")
                    if (assistantRecord.length > 1) {
                        const oldRecords = assistantRecord[1];
                        const {files: messageFiles} = parseMessage(oldRecords.content);
                        Object.assign(oldHistoryFiles, messageFiles);
                    }
                    if (mode === ChatMode.Builder) {
                        latestRecord.data.messages.push({
                            id: uuidv4(),
                            role: "user",
                            content: `<boltArtifact id="hello-js" title="the current file">\n${convertToBoltAction(historyFiles)}\n</boltArtifact>\n\n`,
                        });
                    }
                    pendingScrollToBottomRef.current = true;
                    setMessages(latestRecord.data.messages);
                    setFiles(historyFiles);
                    setOldFiles(oldHistoryFiles);
                    // 重置其他状态
                    clearImages();
                    setIsFirstSend();
                    setIsUpdateSend();
                    resetTerminals();
                }
            } else {
                // 如果是新对话，清空所有状态
                setMessages([]);
                clearImages();
                setIsFirstSend();
                setIsUpdateSend();
            }
        } catch (error) {
            toast.error("加载聊天记录失败");
        }
    };

    // 加载会话历史消息（新版本）
    const loadConversationHistory = async (conversationId: string) => {
        try {
            const historyMessages = await getConversationMessages(conversationId);
            if (historyMessages.length > 0) {
                // 转换为 useChat 期望的格式
                const formattedMessages: WeMessages = historyMessages
                    .filter((msg) => (msg.role === "user" || msg.role === "assistant") && !!msg.content?.trim())
                    .map((msg) => ({
                        id: uuidv4(),
                        role: msg.role as "user" | "assistant",
                        content: msg.content,
                    }));

                // 解析文件
                const historyFiles = {};
                formattedMessages.forEach((message) => {
                    const {files: messageFiles} = parseMessage(message.content);
                    Object.assign(historyFiles, messageFiles);
                });

                pendingScrollToBottomRef.current = true;
                setMessages(formattedMessages);
                setFiles(historyFiles);
                clearImages();
                setIsFirstSend();
                setIsUpdateSend();
                resetTerminals();
            } else {
                // 如果是新会话，清空所有状态
                setMessages([]);
                clearImages();
                setIsFirstSend();
                setIsUpdateSend();
            }
        } catch (error) {
            console.error("加载会话历史失败:", error);
            toast.error("加载会话历史失败");
        }
    };

    // 监听会话切换事件
    useEffect(() => {
        const isStreamAssignment =
            currentConversationId !== null &&
            streamAssignedConversationIdRef.current === currentConversationId;
        streamAssignedConversationIdRef.current = null;

        if (isStreamAssignment) {
            return;
        }

        if (currentConversationId) {
            // 加载会话历史消息
            loadConversationHistory(currentConversationId);
            refUuidMessages.current = [];
        } else {
            // 如果没有选中会话，清空消息
            setMessages([]);
            setFiles({});
            clearImages();
            setIsFirstSend();
            setIsUpdateSend();
            resetTerminals();
        }
    }, [currentConversationId]);

    // 监听聊天选择事件（兼容旧版本）
    useEffect(() => {
        const unsubscribe = eventEmitter.on("chat:select", (uuid: string) => {
            console.log("chat:select event received", { uuid, currentChatUuid: chatUuid });

            // 如果是新聊天（uuid为空字符串）或者切换到不同的聊天
            if (!uuid || uuid !== chatUuid) {
                console.log("Processing chat selection", { isNewChat: !uuid, isDifferentChat: uuid !== chatUuid });
                refUuidMessages.current = [];

                if (uuid) {
                    // 切换到已存在的聊天，使用传入的uuid
                    setChatUuid(uuid);
                    // 加载历史记录
                    loadChatHistory(uuid);
                } else {
                    // 新对话，生成新的uuid并清空所有状态
                    const newUuid = uuidv4();
                    console.log("Starting new chat with UUID:", newUuid);
                    setChatUuid(newUuid);
                    setMessages([]);
                    setFiles({});
                    clearImages();
                    setIsFirstSend();
                    setIsUpdateSend();
                    setEmptyFiles();
                    setFiles({});
                    clearImages();
                    setIsFirstSend();
                    setIsUpdateSend();
                    resetTerminals();
                }
            } else {
                console.log("Chat selection ignored - same chat UUID");
            }
        });

        // 清理订阅
        return () => unsubscribe();
    }, [chatUuid, files]);
    const token = useUserStore((state) => state.token);
    const isAuthenticated = useUserStore((state) => state.isAuthenticated);
    const openLoginModal = useUserStore((state) => state.openLoginModal);
    const {openModal} = useLimitModalStore();

    const [messages, setMessagesa] = useState<WeMessages>([]);
    const {enabledMCPs} = useMCPTools()

    // 自定义 fetch 函数来处理 SSE 流数据
    const customFetch = async (url: string, options: any) => {
        try {
            const latestToken = useUserStore.getState().token;
            if (!latestToken) {
                useUserStore.getState().openLoginModal();
                const authError = new Error(
                    t("chat.errors.auth_required", {defaultValue: "请先登录后再发送消息"}),
                );
                (authError as Error & {status?: number}).status = 401;
                throw authError;
            }
            options.headers = {
                ...(options.headers || {}),
                Authorization: `Bearer ${latestToken}`,
            };

            // 解析原始请求体
            let requestBody;
            try {
                requestBody = JSON.parse(options.body);
            } catch (e) {
                console.error("Failed to parse request body:", e);
                requestBody = options.body;
            }

            // 如果有messages数组，只取最新一条
            if (requestBody.messages && Array.isArray(requestBody.messages)) {
                const latestMessage = requestBody.messages[requestBody.messages.length - 1];

                // 构建工具列表 - 将启用的 MCP 工具发送到后端
                const toolsForBackend = enabledMCPs
                    .filter(mcp => mcp.id) // 只发送有 ID 的工具
                    .map(mcp => ({
                        id: String(mcp.id), // 确保 ID 是字符串
                        name: mcp.name,
                    }));

                // 修改请求体格式：用message替换messages数组
                const memoryFlags = useMemoryStore.getState();
                const modifiedBody = {
                    ...requestBody,
                    message: latestMessage, // 单个消息对象
                    modelConfigId: (baseModal as any).modelConfigId, // 添加必需的modelConfigId参数
                    conversationId: currentConversationId || undefined, // 添加会话ID
                    tools: toolsForBackend, // 添加启用的 MCP 工具
                    enablePreferences: memoryFlags.enablePreferencesInChat,
                    enablePreferenceLearning: memoryFlags.enablePreferenceLearningInChat,
                };
                delete modifiedBody.messages; // 删除原来的messages数组

                // 更新options中的body
                options.body = JSON.stringify(modifiedBody);

                if (toolsForBackend.length > 0) {
                    console.log('[customFetch] 发送 MCP 工具到后端:', toolsForBackend);
                }
            }

            const response = await fetch(url, options);

            if (!response.ok) {
                const responseText = await response.text();
                let errorMessage = responseText;
                try {
                    const payload = JSON.parse(responseText);
                    errorMessage = payload.msg || payload.message || payload.error || responseText;
                } catch {
                    // 非 JSON 错误响应保留纯文本；HTML 等长响应改用统一提示。
                    if (!responseText || responseText.length > 240) {
                        errorMessage = "";
                    }
                }
                const requestError = new Error(
                    errorMessage ||
                        t("chat.errors.request_failed", {
                            defaultValue: "请求失败，请稍后重试",
                        }),
                );
                (requestError as Error & {status?: number}).status = response.status;
                throw requestError;
            }

            // 如果不是流式响应，直接返回
            if (!response.body) {
                return response;
            }

            // 创建一个新的 ReadableStream 来拦截数据
            const originalStream = response.body;
            console.log('[customFetch] 原始流（AG-UI）:', originalStream);
            const reader = originalStream.getReader();

            // AG-UI 时间线累积器。文件操作仍由 handleToolCallEnd 驱动；
            // 同时把工具参数、结果与 reasoning 编码进 AI SDK 的 text stream，供消息层结构化渲染。
            const toolCallStates = new Map<
                string,
                { name: string; argsBuffer: string; ended: boolean }
            >();
            let reasoningBuffer = '';

            const encodeTimelineBlock = (language: string, value: unknown) =>
                `\n\n\`\`\`${language}\n${encodeURIComponent(
                    typeof value === 'string'
                        ? value
                        : (JSON.stringify(value) ?? String(value ?? ''))
                )}\n\`\`\`\n\n`;

            const flushToolCall = (
                toolCallId: string,
                state: { name: string; argsBuffer: string; ended: boolean },
                result?: unknown,
            ) => {
                let args: unknown = {};
                try {
                    args = state.argsBuffer ? JSON.parse(state.argsBuffer) : {};
                } catch {
                    args = { raw: state.argsBuffer };
                }
                return encodeTimelineBlock('arc-tool', {
                    toolCallId,
                    toolName: state.name,
                    args,
                    result,
                    state: result === undefined ? (state.ended ? 'completed' : 'call') : 'result',
                });
            };

            // 单个 decoder 贯穿整个响应，避免 UTF-8 多字节字符跨 chunk 时被截断。
            const decoder = new TextDecoder();
            // SSE 帧累积缓冲（跨 chunk 的不完整帧）
            let sseBuffer = '';

            const transformSseFrame = (frame: string) => {
                if (!frame.trim()) return '';

                let eventName = '';
                let dataStr = '';
                for (const line of frame.split(/\r?\n/)) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        dataStr = line.slice(5).replace(/^\s/, '');
                    }
                }
                if (!dataStr) return '';

                // 后端会先发一个 conversationId 控制事件（旧协议残留，data 是 JSON）
                let parsed: any;
                try {
                    parsed = JSON.parse(dataStr);
                } catch (e) {
                    // 可能是纯文本控制帧（如 [DONE]），忽略
                    return '';
                }

                // 用 SSE event 名（UPPER 枚举名）路由；回退到 JSON.type
                const type = (eventName || parsed.type || '').toUpperCase();
                let transformedText = '';

                // 会话ID控制事件：后端在新建会话时回传，前端需写入 store 以支撑后续多轮
                if (eventName === 'conversation-id' && parsed.conversationId) {
                    const conversationId = String(parsed.conversationId);
                    if (useConversationStore.getState().currentConversationId !== conversationId) {
                        streamAssignedConversationIdRef.current = conversationId;
                        setCurrentConversation(conversationId);
                    }
                    return '';
                }

                if (type.startsWith('REASONING_')) {
                    const delta = parsed.delta ?? parsed.content ?? parsed.text ?? '';
                    if (typeof delta === 'string' && delta) reasoningBuffer += delta;
                    if (type.endsWith('_END') && reasoningBuffer.trim()) {
                        transformedText += encodeTimelineBlock(
                            'arc-reasoning',
                            reasoningBuffer,
                        );
                        reasoningBuffer = '';
                    }
                    return transformedText;
                }

                switch (type) {
                    case 'TEXT_MESSAGE_CONTENT': {
                        const delta = parsed.delta;
                        if (delta) transformedText += delta;
                        break;
                    }
                    case 'TOOL_CALL_START': {
                        const tcId = parsed.toolCallId;
                        const tcName = parsed.toolCallName;
                        if (tcId && tcName) {
                            toolCallStates.set(tcId, {
                                name: tcName,
                                argsBuffer: '',
                                ended: false,
                            });
                        }
                        break;
                    }
                    case 'TOOL_CALL_ARGS': {
                        const tcId = parsed.toolCallId;
                        const delta = parsed.delta;
                        const st = tcId ? toolCallStates.get(tcId) : undefined;
                        if (st && delta) {
                            st.argsBuffer += delta;
                        }
                        break;
                    }
                    case 'TOOL_CALL_END': {
                        const tcId = parsed.toolCallId;
                        const st = tcId ? toolCallStates.get(tcId) : undefined;
                        if (st) {
                            handleToolCallEnd(st.name, st.argsBuffer);
                            st.ended = true;
                        }
                        break;
                    }
                    case 'TOOL_CALL_RESULT': {
                        const tcId = parsed.toolCallId;
                        const st = tcId ? toolCallStates.get(tcId) : undefined;
                        if (tcId && st) {
                            transformedText += flushToolCall(tcId, st, parsed.content);
                            toolCallStates.delete(tcId);
                        } else {
                            transformedText += encodeTimelineBlock('arc-tool', {
                                toolCallId: tcId || `tool-${Date.now()}`,
                                toolName: parsed.toolCallName || 'tool',
                                args: {},
                                result: parsed.content,
                                state: 'result',
                            });
                        }
                        break;
                    }
                    case 'RUN_FINISHED': {
                        if (reasoningBuffer.trim()) {
                            transformedText += encodeTimelineBlock(
                                'arc-reasoning',
                                reasoningBuffer,
                            );
                            reasoningBuffer = '';
                        }
                        toolCallStates.forEach((state, toolCallId) => {
                            transformedText += flushToolCall(toolCallId, state);
                        });
                        toolCallStates.clear();
                        transformedText += 'data: [DONE]\n\n';
                        break;
                    }
                    case 'RUN_ERROR': {
                        const errorMessage = parsed.message || parsed.error || 'Agent 运行失败';
                        console.error('[AG-UI] run error:', errorMessage);
                        transformedText += encodeTimelineBlock('arc-error', String(errorMessage));
                        break;
                    }
                    default:
                        // STEP / STATE 事件由后端配置关闭；保留前向兼容。
                        break;
                }

                return transformedText;
            };

            const drainSseBuffer = (flush = false) => {
                let transformedText = '';
                let separator = sseBuffer.match(/\r?\n\r?\n/);

                while (separator?.index !== undefined) {
                    const frame = sseBuffer.slice(0, separator.index);
                    sseBuffer = sseBuffer.slice(separator.index + separator[0].length);
                    transformedText += transformSseFrame(frame);
                    separator = sseBuffer.match(/\r?\n\r?\n/);
                }

                // 有些服务端在 EOF 前不会补最后一个空行，结束时仍需消费尾帧。
                if (flush) {
                    transformedText += transformSseFrame(sseBuffer);
                    sseBuffer = '';
                }

                return transformedText;
            };

            const stream = new ReadableStream({
                start(controller) {
                    function pump(): Promise<void> {
                        return reader.read().then(({ done, value }) => {
                            if (done) {
                                sseBuffer += decoder.decode();
                                const transformedText = drainSseBuffer(true);
                                if (transformedText) {
                                    controller.enqueue(new TextEncoder().encode(transformedText));
                                }
                                controller.close();
                                return;
                            }

                            sseBuffer += decoder.decode(value, { stream: true });
                            const transformedText = drainSseBuffer();
                            if (transformedText) {
                                controller.enqueue(new TextEncoder().encode(transformedText));
                            }
                            return pump();
                        }).catch(error => {
                            controller.error(error);
                        });
                    }
                    return pump();
                },
                cancel(reason) {
                    return reader.cancel(reason);
                },
            });

            // 返回修改后的响应
            return new Response(stream, {
                status: response.status,
                statusText: response.statusText,
                headers: response.headers
            });

        } catch (error) {
            throw error;
        }
    };

    /**
     * 工具调用完成时，根据工具名与累积的 args JSON 驱动文件操作。
     * write_file/edit_file → StreamingFileManager 打字机渲染（与旧 add/edit 协议行为一致）；
     * delete_file → 删除文件；list_files/grep_files/glob_files/read_file → 暂不渲染。
     */
    const handleToolCallEnd = (toolName: string, argsJson: string) => {
        let args: any = {};
        try {
            args = argsJson ? JSON.parse(argsJson) : {};
        } catch (e) {
            console.error('[AG-UI] 解析工具参数失败:', toolName, argsJson, e);
            return;
        }
        const rawPath = args.path || args.filePath;
        if (!rawPath) return;
        const filePath = normalizeFilePath(rawPath);

        switch (toolName) {
            case 'write_file':
            case 'edit_file': {
                // write_file 的 content / edit_file 的 new_string
                const content = args.content != null ? args.content : (args.new_string != null ? args.new_string : '');
                streamingFileManager.addContent(filePath, String(content)).catch(e =>
                    console.error('[AG-UI] 流式写入失败:', filePath, e)
                );
                // edit_file 完成后需校准（写入是整段替换）
                if (toolName === 'edit_file') {
                    // 延迟一帧让 addContent 入池后标记完成
                    setTimeout(() => streamingFileManager.completeFile(filePath), 50);
                }
                break;
            }
            case 'delete_file': {
                Promise.resolve(useFileStore.getState().deleteFile(filePath)).catch(() => {});
                break;
            }
            default:
                break;
        }
    };

    // 修改 useChat 配置
    const {
        messages: realMessages,
        input,
        handleInputChange,
        isLoading,
        setMessages,
        append,
        setInput,
        stop,
        reload,
    } = useChat({
        api: apiUrl('/api/chat'),
        fetch: customFetch,
        streamProtocol: 'text',  // 使用文本流模式
        headers: {
            ...(token && {Authorization: `Bearer ${token}`}),
        },
        id: chatUuid,
        onResponse: async (response) => {
            // 数据格式转换已由 customFetch 处理，这里无需额外处理
            // customFetch 将后端的 OpenAI 兼容格式转换为 ai/react 期望的格式
        },
        onFinish: async (message) => {
            clearImages();
            scrollToBottom();

            try {
                const needParseMessages = [...messages, message].filter(
                    (m) => !refUuidMessages.current.includes(m.id)
                );

                refUuidMessages.current = [
                    ...refUuidMessages.current,
                    ...needParseMessages.map((m) => m.id),
                ];

                // 生成消息完成后，不再默认解析XML
                // 若需要，可保留作为后备方案（当服务端文件系统不可用时）
                if (message && message.content) {
                    const parseResult = parseMessage(message.content);
                    const {files: messagefiles} = parseResult;
                    for (let key in messagefiles) {
                        await updateContent(key, messagefiles[key], false, true);
                    }
                }

                setIsFirstSend();
                setIsUpdateSend();

                // 优化：已登录用户使用会话功能，消息已由后端自动保存，不需要保存到 IndexedDB
                // 未登录用户继续使用 IndexedDB 作为本地存储
                if (!useUserStore.getState().isAuthenticated) {
                    // 未登录用户：保存到 IndexedDB（本地存储）
                    let initMessage = [];
                    initMessage = [
                        {
                            id: uuidv4(),
                            role: "user",
                            content: input,
                        },
                    ];

                    await db.insert(chatUuid, {
                        messages: [...messages, ...initMessage, message],
                        title:
                            [...initMessage, ...messages]
                                .find(
                                    (m) => m.role === "user" && !m.content.includes("<boltArtifact")
                                )
                                ?.content?.slice(0, 50) || "New Chat",
                    });
                }
                // 已登录用户：消息已由后端 ConversationSaveHook 自动保存，无需额外操作
            } catch (error) {
                // 静默处理错误
            }
            setCheckCount(checkCount => checkCount + 1);
        },
        onError: (error: Error & {status?: number}) => {
            const message = error?.message || String(error);
            console.error("Chat request failed", error);

            if (
                error?.status === 401 ||
                /authentication required|unauthorized|not login|未登录/i.test(message)
            ) {
                openLoginModal();
                toast.error(
                    t("chat.errors.auth_required", {
                        defaultValue: "请先登录后再发送消息",
                    }),
                );
                return;
            }
            if (/quota not enough|quota|limit reached|次数已达上限/i.test(message)) {
                openModal();
                return;
            }
            toast.error(
                message ||
                    t("chat.errors.request_failed", {
                        defaultValue: "请求失败，请稍后重试",
                    }),
            );
        },
    });
    const {status, type} = useUrlData({append});

    // 官网跳转进来监听 url
    useEffect(() => {
        if (status && type === "sketch") {
            showGuide();
        }
    }, [status, type]);


    const parseTimeRef = useRef(0);

    useEffect(() => {
        const visibleFun = () => {
            if (isLoading) return;
            setTimeout(() => {
                updateFileSystemNow();
            }, 600);
        };
        document.addEventListener("visibilitychange", visibleFun);
        return () => {
            document.removeEventListener("visibilitychange", visibleFun);
        };
    }, [isLoading, files]);

    useEffect(() => {
        if (Date.now() - parseTimeRef.current > 200 && isLoading) {
            setMessagesa(realMessages as WeMessages);
            parseTimeRef.current = Date.now();

            const needParseMessages = messages.filter(
                (m) => !refUuidMessages.current.includes(m.id)
            );
            parseMessages(needParseMessages);
            scrollToBottom();
        }
        if (errors.length > 0 && isLoading) {
            clearErrors();
        }
        if (!isLoading) {
            setMessagesa(realMessages as WeMessages);
            // 非流式状态下（加载历史/切换会话后），确保默认展示最新一条
            if (pendingScrollToBottomRef.current) {
                pendingScrollToBottomRef.current = false;
                // 等 DOM 更新后再滚动，避免只停在顶部显示第一条
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        const messageContainer = document.querySelector('.message-container') as HTMLDivElement | null;
                        if (messageContainer) {
                            messageContainer.scrollTop = messageContainer.scrollHeight;
                        }
                    });
                });
            }
        }
    }, [realMessages, isLoading]);

    useEffect(() => {
        if (isLoading) return;
        const isMiniProgram = Object.keys(files).some(
            (path) => path === "app.json" || path.endsWith("/app.json"),
        );
        if (!isMiniProgram) return;
        void import("@/utils/createWtrite")
            .then(({createMpIcon}) => createMpIcon(files))
            .catch((error) => console.error("Failed to generate mini-program icons", error));
    }, [files, isLoading]);

    const [userScrolling, setUserScrolling] = useState(false)
    const userScrollTimeoutRef = useRef<NodeJS.Timeout>()

    // 处理用户滚动
    const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const target = e.target as HTMLDivElement
        const isScrolledToBottom = Math.abs(target.scrollHeight - target.scrollTop - target.clientHeight) < 10

        if (!isScrolledToBottom) {
            // 用户正在滚动查看历史消息
            setUserScrolling(true)

            // 清除之前的定时器
            if (userScrollTimeoutRef.current) {
                clearTimeout(userScrollTimeoutRef.current)
            }

            // 设置新的定时器，3秒后允许自动滚动
            userScrollTimeoutRef.current = setTimeout(() => {
                setUserScrolling(false)
            }, 3000)
        }
    }

    // 修改滚动到底部的函数
    const scrollToBottom = () => {
        if (userScrolling) return // 如果用户正在滚动，不执行自动滚动

        const messageContainer = document.querySelector('.message-container')
        if (messageContainer) {
            messageContainer.scrollTop = messageContainer.scrollHeight
        }
    }

    // 在组件卸载时清理定时器
    useEffect(() => {
        return () => {
            if (userScrollTimeoutRef.current) {
                clearTimeout(userScrollTimeoutRef.current)
            }
        }
    }, [])

    // 添加上传状态跟踪
    const [isUploading, setIsUploading] = useState(false);

    // 跟踪list-progress事件的状态
    const [listProgressStates, setListProgressStates] = useState<Record<string, { filePath: string; content?: string; isLoading: boolean }>>({});
    const composerOverlayRef = useRef<HTMLDivElement>(null);
    const [composerOverlayHeight, setComposerOverlayHeight] = useState(190);

    useEffect(() => {
        const element = composerOverlayRef.current;
        if (!element || typeof ResizeObserver === 'undefined') return;
        const updateHeight = () => setComposerOverlayHeight(Math.ceil(element.getBoundingClientRect().height));
        updateHeight();
        const observer = new ResizeObserver(updateHeight);
        observer.observe(element);
        return () => observer.disconnect();
    }, []);

    // 仅展示 user/assistant，过滤 tool/system/空消息
    const filterMessages = messages.filter(
        (e) => (e.role === "user" || e.role === "assistant") && !!e.content?.trim()
    );
    // 修改上传处理函数
    const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
        if (!e.target.files?.length || isUploading) return;
        setIsUploading(true);

        const selectedFiles = Array.from(e.target.files);
        const MAX_FILE_SIZE = 5 * 1024 * 1024;

        const validFiles = selectedFiles.filter((file) => {
            if (file.size > MAX_FILE_SIZE) {
                toast.error(t("chat.errors.file_size_limit", {fileName: file.name}));
                return false;
            }
            return true;
        });

        try {
            const uploadResults = await Promise.all(
                validFiles.map(async (file) => {
                    const url = await uploadImage(file);
                    return {
                        id: uuidv4(),
                        file,
                        url,
                        localUrl: URL.createObjectURL(file),
                        status: "done" as const,
                    };
                })
            );

            addImages(uploadResults);
            if (uploadResults.length === 1) {
                toast.success(t("chat.success.images_uploaded"));
            } else {
                toast.success(
                    t("chat.success.images_uploaded_multiple", {
                        count: uploadResults.length,
                    })
                );
            }
        } catch (error) {
            console.error("Upload failed:", error);
            toast.error(t("chat.errors.upload_failed"));
        } finally {
            setIsUploading(false);
        }

        e.target.value = "";
    };

    // 修改提交处理函数
    const handleSubmitWithFiles = async (
        _: React.KeyboardEvent,
        text?: string
    ) => {
        if (!text && !input.trim() && uploadedImages.length === 0) return;

        if (!isAuthenticated || !token) {
            openLoginModal();
            toast.info(
                t("chat.errors.auth_required", {
                    defaultValue: "请先登录后再发送消息",
                }),
            );
            return;
        }

        // 检查模型列表是否为空
        if (!modelOptions || modelOptions.length === 0) {
            toast.error(t('models.errors.no_models_configured') || '请先配置模型，然后再发送消息');
            return;
        }

        try {
            // 处理文件引用
            // const processedInput = await processFileReferences(input);
            // 如果是 ollama类型 模型 需要走单独逻辑，不走云端

            // 保存当前的图片附件
            const currentAttachments = uploadedImages.map((img) => ({
                id: img.id,
                name: img.id,
                type: img.file.type,
                localUrl: img.localUrl,
                contentType: img.file.type,
                url: img.url,
            }));

            // 先清理图片状态
            clearImages();

            append(
                {
                    role: "user",
                    content: text || input,
                },
                {
                    experimental_attachments: currentAttachments,
                }
            );
            setInput("");
            setTimeout(() => {
                scrollToBottom();
            }, 100);
        } catch (error) {
            toast.error("Failed to upload files");
        }
    };

    // 修改键盘提交处理
    const handleKeySubmit = (e: React.KeyboardEvent) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSubmitWithFiles(e);
        }
    };

    // 修改粘贴处理函数
    const handlePaste = async (e: ClipboardEvent) => {
        if (isUploading) return;

        const items = e.clipboardData?.items;
        if (!items) return;

        const hasImages = Array.from(items).some(
            (item) => item.type.indexOf("image") !== -1
        );
        if (hasImages) {
            e.preventDefault();
            setIsUploading(true);

            const imageItems = Array.from(items).filter(
                (item) => item.type.indexOf("image") !== -1
            );

            try {
                const uploadResults = await Promise.all(
                    imageItems.map(async (item) => {
                        const file = item.getAsFile();
                        if (!file) throw new Error("Failed to get file from clipboard");

                        const url = await uploadImage(file);
                        return {
                            id: uuidv4(),
                            file,
                            url,
                            localUrl: URL.createObjectURL(file),
                            status: "done" as const,
                        };
                    })
                );

                addImages(uploadResults);

                if (uploadResults.length === 1) {
                    toast.success(t("chat.success.image_pasted"));
                } else {
                    toast.success(
                        t("chat.success.images_pasted_multiple", {
                            count: uploadResults.length,
                        })
                    );
                }
            } catch (error) {
                toast.error(t("chat.errors.paste_failed"));
            } finally {
                setIsUploading(false);
            }
        }
    };

    // 添加粘贴事件监听
    useEffect(() => {
        const textarea = textareaRef.current;
        if (!textarea) return;

        textarea.addEventListener("paste", handlePaste);
        return () => {
            textarea.removeEventListener("paste", handlePaste);
        };
    }, []);

    // 添加拖拽处理函数
    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
    };

    const handleDrop = async (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();

        if (isUploading) return;
        setIsUploading(true);

        try {
            const items = Array.from(e.dataTransfer.items);
            const imageItems = items.filter((item) => item.type.startsWith("image/"));

            const uploadResults = await Promise.all(
                imageItems.map(async (item) => {
                    const file = item.getAsFile();
                    if (!file) throw new Error("Failed to get file from drop");

                    const url = await uploadImage(file);
                    return {
                        id: uuidv4(),
                        file,
                        url,
                        localUrl: URL.createObjectURL(file),
                        status: "done" as const,
                    };
                })
            );

            addImages(uploadResults);

            if (uploadResults.length === 1) {
                toast.success("图片已添加到输入框");
            } else {
                toast.success(`${uploadResults.length} 张图片已添加到输入框`);
            }
        } catch (error) {
            toast.error("添加图片失败");
        } finally {
            setIsUploading(false);
        }
    };

    const showJsx = (
        <div
            className="message-container min-h-0 flex-1 overflow-y-auto [scrollbar-width:thin]"
            style={{ paddingBottom: composerOverlayHeight + 20 }}
            onScroll={handleScroll}
        >
            <div className="mx-auto w-full max-w-[760px] px-3 pb-8 pt-6 sm:px-5">
                {filterMessages.length === 0 && !isLoading ? (
                    <Tips
                        append={append}
                        setInput={setInput}
                        handleFileSelect={handleFileSelect}
                    />
                ) : null}

                <div className="space-y-1">
                    {filterMessages.map((message, index) => (
                        <MessageItem
                            handleRetry={() => reload()}
                            key={`${message.id}-${index}`}
                            message={message}
                            isEndMessage={filterMessages[filterMessages.length - 1].id === message.id}
                            isLoading={isLoading}
                            listProgressStates={listProgressStates}
                            onUpdateMessage={(_messageId, content) => {
                                append({
                                    role: "user",
                                    content: ` ${content?.[0]?.text}`,
                                });
                            }}
                        />
                    ))}

                    {isLoading ? (
                        <div className="flex items-start gap-3 px-1 py-3" key="loading-indicator">
                            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-border/70 bg-card shadow-sm">
                                <AppLogo className="h-7 w-7 rounded-lg border-0 shadow-none" />
                            </div>
                            <div className="min-w-0 pt-0.5">
                                <div className="flex items-center gap-2 text-xs font-medium text-foreground/85">
                                    <LoaderCircle className="h-3.5 w-3.5 animate-spin text-muted-foreground" />
                                    {t("chat.processing.title", {
                                        defaultValue: "Agent 正在处理",
                                    })}
                                </div>
                                <div className="mt-1 text-[11px] text-muted-foreground">
                                    {t("chat.processing.description", {
                                        defaultValue: "正在分析上下文并准备回复…",
                                    })}
                                </div>
                            </div>
                        </div>
                    ) : null}
                    <div ref={messagesEndRef} className="h-px" />
                </div>
            </div>
        </div>
    );

    // 显示引导弹窗
    const showGuide = () => {};





    return (
        <div
            className="relative flex h-full max-w-full flex-col overflow-hidden bg-background"
            onDragOver={handleDragOver}
            onDrop={handleDrop}
        >
            {showJsx}

            <div className="absolute right-3 top-2 z-20">
                <FileSystemStatus />
            </div>

            <div
                ref={composerOverlayRef}
                className="pointer-events-none absolute inset-x-0 bottom-0 z-30 bg-gradient-to-t from-background via-background/95 to-transparent pt-9"
            >
                <div className="pointer-events-auto">
                    <ChatInput
                        input={input}
                        setMessages={setMessages}
                        append={append}
                        messages={messages}
                        stopRuning={stop}
                        setInput={setInput}
                        isLoading={isLoading}
                        isUploading={isUploading}
                        uploadedImages={uploadedImages}
                        baseModal={baseModal}
                        handleInputChange={handleInputChange}
                        handleKeySubmit={handleKeySubmit}
                        handleSubmitWithFiles={handleSubmitWithFiles}
                        handleFileSelect={handleFileSelect}
                        removeImage={removeImage}
                        addImages={addImages}
                        setIsUploading={setIsUploading}
                        setBaseModal={setBaseModal}
                    />
                </div>
            </div>
        </div>
    );
};
