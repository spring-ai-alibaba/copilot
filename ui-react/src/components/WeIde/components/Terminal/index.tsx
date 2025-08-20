import React, { useEffect, useRef, useState } from "react";
import { Terminal as TerminalIcon, X, SquarePlus } from "lucide-react";
import weTerminal from "./utils/weTerminal"; // 导入 Terminal 类
import useTerminalStore from "../../../../stores/terminalSlice";
import "xterm/css/xterm.css";
import "./styles.css";
import { cn } from "@/utils/cn";
import { eventEmitter } from "@/components/AiChat/utils/EventEmitter";
import { use } from "i18next";
import useChatModeStore from "@/stores/chatModeSlice";
import useThemeStore from "@/stores/themeSlice";
import useChatStore from "@/stores/chatSlice";
import { useFileStore } from "../../stores/fileStore";
interface TerminalItem {
  processId: string; // 自增 id
  containerRef: React.RefObject<HTMLDivElement>; // 终端的容器
  terminal: weTerminal; // Terminal 类实例
}

// 终端的选项卡
function TerminalTab({
  selectProcessId,
  changeTerminalTab,
  onClose,
  processId,
  terminal,
}: {
  selectProcessId: string;
  changeTerminalTab: () => void;
  onClose: () => void;
  processId: string;
  terminal: weTerminal;
}) {
  const [isReady, setIsReady] = useState(terminal.getIsReady());

  useEffect(() => {
    setIsReady(terminal.getIsReady());
  }, [terminal.getIsReady()]);

  return (
    <div
      className={cn(
        "flex items-center px-4 py-1.5 cursor-pointer transition-colors duration-200",
        "border-b border-[#e5e5e5] dark:border-[#252525]",
        processId == selectProcessId
          ? "bg-[#ffffff] dark:bg-[#37373d] text-[#424242] dark:text-white"
          : "bg-[#f5f5f5] dark:bg-[#252526] text-[#616161] dark:text-[#858585] hover:bg-[#e8e8e8] dark:hover:bg-[#2d2d2d]",
        processId,
        selectProcessId
      )}
      onClick={changeTerminalTab}
    >
      {/* 切换至当前终端的按钮 */}
      <div className="flex items-center">
        <TerminalIcon
          className={cn(
            "w-3 h-3 mr-2 transition-colors",
            isReady
              ? "text-green-500 dark:text-green-400"
              : "text-yellow-500 dark:text-yellow-400"
          )}
        />
        <span
          className={cn(
            "text-xs font-medium",
            processId == selectProcessId
              ? "text-[#424242] dark:text-white"
              : "text-[#616161] dark:text-[#858585]"
          )}
        >
          {/* Terminal {!isReady && '(Initializing...)'} */}
          Terminal
        </span>
      </div>

      <button
        onClick={(e) => {
          e.stopPropagation()
          onClose()

        }}
        className={cn(
          "p-1 rounded transition-colors ml-auto",
          "hover:bg-[#e5e5e5] dark:hover:bg-[#404040]",
          "group"
        )}
      >
        <X
          className={cn(
            "w-3 h-3",
            "text-[#616161] dark:text-[#858585]",
            "group-hover:text-[#424242] dark:group-hover:text-white"
          )}
        />
      </button>
    </div>
  );
}

let isInit = false;

// 终端本体
function TerminalItem({
  containerRef,
  processId,
  selectProcessId,
  terminal,
}: {
  containerRef: React.RefObject<HTMLDivElement>;
  processId: string;
  selectProcessId: string;
  terminal: weTerminal;
}) {
  const {isDarkMode} = useThemeStore()

  const { addError } = useFileStore();

  useEffect(() => {
    // 获取当前主题
    terminal.initialize(containerRef.current, processId, addError);
  }, [containerRef.current]);
  
  useEffect(()=>{
    terminal.setTheme(isDarkMode)
  },[isDarkMode])
  return (
    <div
      ref={containerRef}
      className="flex-1 overflow-hidden terminal-container bg-white dark:bg-[#18181a] px-2 py-1"
      style={{
        display: processId == selectProcessId ? "block" : "none",
      }}
    />
  );
}

export function Terminal() {
  const { newTerminal, terminals, addTerminal, removeTerminal } =
    useTerminalStore();

  const [selectProcessId, setSelectProcessId] = useState<string | null>(null);
  const [items, setItems] = useState<TerminalItem[]>([]);
  const [updateCount, setUpdateCount] = useState(0);

  // 初始化终端列表（其实不会初始化终端，只是用作渲染 显示终端）

  useEffect(() => {
    if (!isInit) {
      newTerminal();
      isInit = true;
    }
    const update = (processId: string) => {
      setSelectProcessId(processId);
      setUpdateCount((num) => num + 1);
    };
    eventEmitter.on("terminal:update", update);
    return () => {
      eventEmitter.removeListener("terminal:update", update);
    };
  }, []);

  useEffect(() => {
    const newItems = Array.from(terminals).map(([key, terminal]) => ({
      processId: key,
      containerRef: terminal.getContainerRef(),
      terminal: terminal,
    }));
    setItems(newItems);
  }, [terminals.size, updateCount]);

  // 处理关闭事件
  const closeTerminal = (item: TerminalItem) => {
    // 销毁终端
    removeTerminal(item.processId);

    // 更新终端列表
    const newItems = items.filter((i) => item.processId !== i.processId);
    // setItems(newItems);

    // 如果关闭的是当前选中的终端，则选中前一项终端
    if (item.processId == selectProcessId) {
      const prevItem = newItems[newItems.length - 1]; // 选中最后一项
  
      if (prevItem) {
        setSelectProcessId(prevItem.processId);
      } else {

        setSelectProcessId(null); // 如果没有终端了，设置为 -1
      }
    }
  };

  // 添加一个终端
  const addTerminalHandle = async () => {
    newTerminal((t: weTerminal) => {
      setSelectProcessId(t.getProcessId());
    });
  };

  // 切换终端
  const changeTerminalTab = (item: TerminalItem) => {
    setSelectProcessId(item.processId);
  };

  return (
    <div className={`w-full h-full flex flex-col`}>
      <div
        style={{
          display: "flex",
          flexDirection: "row",
          alignItems: "center",
          gap: "4px",
          padding: "4px 8px",
        }}
      >
        {items.map((item) => (
          <TerminalTab
            key={item.processId}
            selectProcessId={selectProcessId}
            changeTerminalTab={() => changeTerminalTab(item)}
            onClose={() => closeTerminal(item)}
            processId={item.processId}
            terminal={item.terminal}
          />
        ))}

        <SquarePlus
          className="w-4 h-4 cursor-pointer"
          onClick={addTerminalHandle}
        />
      </div>

      {/* 终端的本体 */}
      {items.map((item) => (
        <TerminalItem
          key={item.processId}
          containerRef={item.containerRef}
          processId={item.processId}
          selectProcessId={selectProcessId}
          terminal={item.terminal}
        />
      ))}
    </div>
  );
}
