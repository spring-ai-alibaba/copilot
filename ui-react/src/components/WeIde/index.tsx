import {lazy, Suspense, useEffect, useState} from "react";
import {ActivityBar} from "./components/ActivityBar";
import {Terminal} from "./components/Terminal"
import {EditorTabs} from "./components/EditorTabs"
import {Panel, PanelGroup, PanelResizeHandle} from "react-resizable-panels"
import {useEditorStore} from "./stores/editorStore"
import {FileExplorer} from "./components/IDEContent/FileExplorer"
import {Search} from "./components/IDEContent/Search"

const Editor = lazy(() =>
  import("./components/Editor").then((module) => ({default: module.Editor})),
);

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
    <div className="flex h-full w-full overflow-hidden bg-workbench-panel text-foreground">
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
          className="flex-shrink-0 border-r border-border/70"
        >
          {activeView === "files" ? (
            <FileExplorer onFileSelect={handleFileSelect} />
          ) : (
            <Search onFileSelect={handleFileSelect} />
          )}
        </Panel>

        {/* File List Drag Handle */}
        <PanelResizeHandle className="w-px cursor-col-resize bg-border/70 transition-colors hover:bg-foreground/15" />
      
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
              <div className="flex-1 overflow-hidden bg-workbench-panel">
                {activeTab && (
                  <Suspense fallback={null}>
                    <Editor fileName={activeTab} initialLine={currentLine} />
                  </Suspense>
                )}
              </div>
            </Panel>

            {/* 终端区域 */}
       
              <>
                {/* 上下拖动区域 */}
                <PanelResizeHandle
                  style={{ display: showTerminal ? "flex" : "none" }}
                  className="h-1 cursor-row-resize transition-colors hover:bg-foreground/15"
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
                  className="border-t border-border/70 bg-workbench"
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
