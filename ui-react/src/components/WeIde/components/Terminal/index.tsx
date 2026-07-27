import React, {useEffect, useState} from "react";
import {SquarePlus, Terminal as TerminalIcon, X} from "lucide-react";
import type WeTerminal from "./utils/weTerminal";
import useTerminalStore from "../../../../stores/terminalSlice";
import "xterm/css/xterm.css";
import "./styles.css";
import {cn} from "@/utils/cn";
import {eventEmitter} from "@/components/AiChat/utils/EventEmitter";
import useThemeStore from "@/stores/themeSlice";
import {useFileStore} from "../../stores/fileStore";
import {useTranslation} from "react-i18next";

interface TerminalItem {
  processId: string; // 自增 id
  containerRef: React.RefObject<HTMLDivElement>; // 终端的容器
  terminal: WeTerminal; // Terminal 类实例
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
  terminal: WeTerminal;
}) {
  const [isReady, setIsReady] = useState(terminal.getIsReady());

  useEffect(() => {
    setIsReady(terminal.getIsReady());
  }, [terminal.getIsReady()]);

  return (
    <div
      className={cn(
        "flex cursor-pointer items-center rounded-md px-2 py-1 transition-colors duration-150",
        processId == selectProcessId
          ? "bg-workbench-active text-foreground"
          : "text-muted-foreground hover:bg-foreground/[0.045] hover:text-foreground"
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
              ? "text-foreground"
              : "text-muted-foreground"
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
          "hover:bg-foreground/[0.07]",
          "group"
        )}
      >
        <X
          className={cn(
            "w-3 h-3",
            "text-muted-foreground",
            "group-hover:text-foreground"
          )}
        />
      </button>
    </div>
  );
}

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
  terminal: WeTerminal;
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
      className="terminal-container flex-1 overflow-hidden bg-workbench-panel px-2 py-1"
      style={{
        display: processId == selectProcessId ? "block" : "none",
      }}
    />
  );
}

export function Terminal() {
  const {t} = useTranslation();
  const { newTerminal, terminals, removeTerminal } =
    useTerminalStore();

  const [selectProcessId, setSelectProcessId] = useState<string | null>(null);
  const [items, setItems] = useState<TerminalItem[]>([]);
  const [updateCount, setUpdateCount] = useState(0);

  // 初始化终端列表（其实不会初始化终端，只是用作渲染 显示终端）

  useEffect(() => {
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
    newTerminal((t: WeTerminal) => {
      setSelectProcessId(t.getProcessId());
    });
  };

  // 切换终端
  const changeTerminalTab = (item: TerminalItem) => {
    setSelectProcessId(item.processId);
  };

  return (
    <div className="flex h-full w-full flex-col">
      <div className="flex min-h-9 flex-row items-center gap-1 border-b border-border/65 px-2 py-1">
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

        <button
          type="button"
          onClick={addTerminalHandle}
          className="arc-icon-button h-7 w-7"
          title={t("appShell.dock.newTerminal", {defaultValue: "新建终端"})}
          aria-label={t("appShell.dock.newTerminal", {defaultValue: "新建终端"})}
        >
          <SquarePlus className="h-3.5 w-3.5" />
        </button>
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
