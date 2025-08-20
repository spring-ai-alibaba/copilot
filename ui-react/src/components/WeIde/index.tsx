import { useState, useEffect, useRef } from "react";
import { ActivityBar } from "./components/ActivityBar";
import { Terminal } from "./components/Terminal"
import { Editor } from "./components/Editor"
import { EditorTabs } from "./components/EditorTabs"
import { Panel, PanelGroup, PanelResizeHandle } from "react-resizable-panels"
import { useEditorStore } from "./stores/editorStore"
import { FileExplorer } from "./components/IDEContent/FileExplorer"
import { Search } from "./components/IDEContent/Search"
import { TeamExample } from "../Role"

export default function WeIde() {
  const [activeTab, setActiveTab] = useState("");
  const [showTerminal, setShowTerminal] = useState(true);
  const [openTabs, setOpenTabs] = useState<string[]>([]);
  const { setDirty } = useEditorStore();
  const [activeView, setActiveView] = useState<"files" | "search">("files");
  const [currentLine, setCurrentLine] = useState<number | undefined>();

  useEffect(() => {
    const handleEmit = (
      event: CustomEvent<{ path: string; line?: number }>
    ) => {
      handleFileSelectAiFile(event.detail.path, event.detail.line);
    };

    window.addEventListener("openFile", handleEmit as EventListener);
    return () => {
      window.removeEventListener("openFile", handleEmit as EventListener);
    };
  }, [openTabs]);


  const handleFileSelectAiFile = (path: string, line?: number) => {
    setActiveTab(path);
    setCurrentLine(line);
    if (!openTabs.includes(path)) {
      const newTabs = [...openTabs];
      newTabs[0] = path;
      setOpenTabs(newTabs);
    }
    setDirty(path, false);
  };

  const handleFileSelect = (path: string, line?: number) => {
    setActiveTab(path);
    setCurrentLine(line);
    if (!openTabs.includes(path)) {
      setOpenTabs([...openTabs, path]);
    }
  };

  const handleTabClose = (tab: string) => {
    const newTabs = openTabs.filter((t) => t !== tab);
    setOpenTabs(newTabs);
    if (activeTab === tab && newTabs.length > 0) {
      setActiveTab(newTabs[0]);
    }
  };

  const handleCloseAll = () => {
    setOpenTabs([]);
    setActiveTab("");
  };

  return (
    <div
      style={{
        borderRadius: "8px",
        borderTopRightRadius: "0px",
        borderTopLeftRadius: "0px",
      }}
      className="h-full w-full bg-white dark:bg-[#18181a] text-[#333] dark:text-gray-300 flex overflow-hidden border border-[#e4e4e4] dark:border-[#333]"
    >
      {/* Activity Bar (Icon Bar) */}
      <ActivityBar
        activeView={activeView}
        onViewChange={setActiveView}
        onToggleTerminal={() => setShowTerminal(!showTerminal)}
        showTerminal={showTerminal}
      />


      <PanelGroup direction="horizontal">
        {/* File List */}
        <Panel
          defaultSize={25}
          minSize={16}
          maxSize={30}
          className="flex-shrink-0 border-r border-[#e4e4e4] dark:border-[#333]"
        >
          {activeView === "files" ? (
            <FileExplorer onFileSelect={handleFileSelect} />
          ) : (
            <Search onFileSelect={handleFileSelect} />
          )}
        </Panel>

        {/* File List Drag Handle */}
        <PanelResizeHandle className="w-[1px] bg-[#e6e6e6] hover:bg-[#e8e8e8] dark:hover:bg-[#404040] transition-colors cursor-col-resize" />
      
        {/* Coding Area and Terminal */}
        <Panel className="min-w-0 ml-[-1px]">
          <PanelGroup direction="vertical">
            {/* Coding Area */}
            <Panel className="flex flex-col min-h-0">
              <EditorTabs
                openTabs={openTabs}
                activeTab={activeTab}
                onTabSelect={setActiveTab}
                onTabClose={handleTabClose}
                onCloseAll={handleCloseAll}
              />
              <div className="flex-1 overflow-hidden bg-[#ffffff] dark:bg-[#18181a]">
                {activeTab && (
                  <Editor fileName={activeTab} initialLine={currentLine} />
                )}
              </div>
            </Panel>

            {/* 终端区域 */}
       
              <>
                {/* 上下拖动区域 */}
                <PanelResizeHandle
                  style={{ display: showTerminal ? "flex" : "none" }}
                  className="h-1 hover:bg-[#e8e8e8] dark:hover:bg-[#404040] transition-colors cursor-row-resize"
                />

                {/* 创建 承载终端 的容器 */}
                <Panel
                  defaultSize={30}
                  minSize={10}
                  maxSize={80}
                  style={{
                    display: showTerminal ? "flex" : "none",
                    flexDirection: "column",
                  }}
                  className="bg-[#f6f6f6] dark:bg-[#1e1e1e] border-t border-[#e4e4e4] dark:border-[#333]"
                >
                  {/* 终端icon + 终端本体 */}
                  <Terminal />
                </Panel>
              </>
          
          </PanelGroup>
        </Panel>
      </PanelGroup>
    </div>
  );
}
