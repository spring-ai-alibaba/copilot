import {X} from "lucide-react";
import {useEditorStore} from "../stores/editorStore";
import {useUnsavedChanges} from "../hooks/useUnsavedChanges";
import {cn} from "@/utils/cn";
import FileIcon from "./IDEContent/FileExplorer/components/fileIcon";

interface EditorTabsProps {
  openTabs: string[];
  activeTab: string;
  onTabSelect: (tab: string) => void;
  onTabClose: (tab: string) => void;
  onCloseAll: () => void;
}

export function EditorTabs({
  openTabs,
  activeTab,
  onTabSelect,
  onTabClose,
  onCloseAll,
}: EditorTabsProps) {
  const { isDirty } = useEditorStore();
  const { checkUnsavedChanges } = useUnsavedChanges();

  const handleTabClose = (tab: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!isDirty[tab] || checkUnsavedChanges([tab])) {
      onTabClose(tab);
    }
  };

  const handleCloseAll = () => {
    const dirtyTabs = openTabs.filter((tab) => isDirty[tab]);
    if (dirtyTabs.length === 0 || checkUnsavedChanges(dirtyTabs)) {
      onCloseAll();
    }
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    const menu = document.createElement("div");
    menu.className =
      "arc-popover fixed z-[10030] w-36 p-1.5";
    menu.style.left = `${e.clientX}px`;
    menu.style.top = `${e.clientY}px`;

    const closeAllButton = document.createElement("button");
    closeAllButton.className =
      "arc-popover-item min-h-8 py-1.5 text-xs";
    closeAllButton.textContent = "关闭全部";
    closeAllButton.onclick = () => {
      handleCloseAll();
      document.body.removeChild(menu);
    };

    menu.appendChild(closeAllButton);
    document.body.appendChild(menu);

    const handleClickOutside = (e: MouseEvent) => {
      if (!menu.contains(e.target as Node)) {
        document.body.removeChild(menu);
      }
    };

    document.addEventListener("click", handleClickOutside, { once: true });
  };

  return (
    <div
      className="flex min-h-9 items-stretch overflow-x-auto border-b border-border/65 bg-workbench [scrollbar-width:thin]"
      onContextMenu={handleContextMenu}
      role="tablist"
      aria-label="Open editor tabs"
    >
      {openTabs.map((tab) => (
        <div
          key={tab}
          role="tab"
          aria-selected={activeTab === tab}
          tabIndex={activeTab === tab ? 0 : -1}
          className={cn(
            "group relative flex min-w-[120px] max-w-[200px] cursor-pointer items-center space-x-2 border-r border-border/65 px-3 py-1.5 text-muted-foreground transition-colors",
            activeTab === tab
              ? "bg-workbench-panel text-foreground before:absolute before:bottom-0 before:left-0 before:h-px before:w-full before:bg-foreground/55"
              : "hover:bg-foreground/[0.04] hover:text-foreground"
          )}
          onClick={() => onTabSelect(tab)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              onTabSelect(tab);
            }
          }}
        >
          <div className="flex-shrink-0">
            <FileIcon fileName={tab} />
          </div>
          <span className="flex-1 text-xs truncate">
            {tab}
            {isDirty[tab] && (
              <span className="ml-1 inline-block h-1.5 w-1.5 rounded-full bg-amber-500" />
            )}
          </span>
          <button
            className={cn(
              "flex h-5 w-5 items-center justify-center rounded-md transition-all duration-150",
              "opacity-0 group-hover:opacity-100",
              "hover:bg-foreground/[0.07] focus:opacity-100 focus:outline-none focus:ring-1 focus:ring-ring/35"
            )}
            onClick={(e) => handleTabClose(tab, e)}
            aria-label={`Close ${tab}`}
            title={`Close ${tab}`}
          >
            <X className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        </div>
      ))}
    </div>
  );
}
