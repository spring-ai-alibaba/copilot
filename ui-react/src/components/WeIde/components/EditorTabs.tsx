import { FileCode, FileText, X } from "lucide-react";
import { useEditorStore } from "../stores/editorStore";
import { useUnsavedChanges } from "../hooks/useUnsavedChanges";
import { cn } from "@/utils/cn";
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
      "absolute bg-white dark:bg-[#252526] border border-[#e5e5e5] dark:border-[#454545] rounded-lg shadow-lg py-1 z-50 transition-opacity duration-150";
    menu.style.left = `${e.clientX}px`;
    menu.style.top = `${e.clientY}px`;

    const closeAllButton = document.createElement("button");
    closeAllButton.className =
      "w-full px-4 py-2 text-[13px] text-left hover:bg-[#f5f5f5] dark:hover:bg-[#2d2d2d] text-[#333] dark:text-gray-300 transition-colors duration-150";
    closeAllButton.textContent = "Close All";
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
      className="bg-[#f6f6f6] dark:bg-[#28292b] flex items-center border-[#e5e5e5] dark:border-[#252525] overflow-x-auto scrollbar-thin scrollbar-thumb-[#c8c8c8] dark:scrollbar-thumb-[#404040] scrollbar-track-transparent"
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
            "group relative px-3 py-1.5 flex items-center space-x-2 cursor-pointer border-r border-[#e5e5e5] dark:border-[#252525] min-w-[120px] max-w-[200px] transition-all duration-200 ease-in-out",
            activeTab === tab
              ? "bg-white dark:bg-[#18181a] text-[#333] dark:text-white before:absolute before:bottom-0 before:left-0 before:w-full before:h-[2px] before:bg-[#007acc]"
              : "hover:bg-[#f9f9f9] dark:bg-[#18181a] dark:hover:bg-[#2d2d2d] text-[#616161] dark:text-gray-400 hover:text-[#333] dark:hover:text-gray-200"
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
              <span className="ml-1 inline-block w-1.5 h-1.5 rounded-full bg-[#007acc] dark:bg-blue-500 transition-all duration-200" />
            )}
          </span>
          <button
            className={cn(
              "flex items-center justify-center w-5 h-5 rounded-sm transition-all duration-200",
              "opacity-0 group-hover:opacity-100",
              "hover:bg-[#e5e5e5] dark:hover:bg-[#404040] active:bg-[#d5d5d5] dark:active:bg-[#505050]",
              "focus:outline-none focus:ring-1 focus:ring-[#007acc] focus:opacity-100"
            )}
            onClick={(e) => handleTabClose(tab, e)}
            aria-label={`Close ${tab}`}
            title={`Close ${tab}`}
          >
            <X className="w-3.5 h-3.5 text-[#616161] dark:text-gray-400" />
          </button>
        </div>
      ))}
    </div>
  );
}
