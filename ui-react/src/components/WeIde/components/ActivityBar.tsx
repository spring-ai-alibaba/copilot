import {Files, Search, Terminal} from "lucide-react";

import {Tooltip} from "./Tooltip";
import {cn} from "@/utils/cn";

interface ActivityBarProps {
  activeView: "files" | "search";
  showTerminal: boolean;
  onViewChange: (view: "files" | "search") => void;
  onToggleTerminal: () => void;
}

export function ActivityBar({
  activeView,
  onViewChange,
  onToggleTerminal,
  showTerminal
}: ActivityBarProps) {
  // GitHub 仓库链接
  const handleGithubClick = () => {
    window.open('https://github.com/we0-dev/we0', '_blank');
  };

  return (
    <div className="w-12 bg-[#f6f6f6] dark:bg-[#1a1a1c] flex flex-col items-center py-2 border-r border-[#e4e4e4] dark:border-[#18181a]">
      <Tooltip content="File Explorer" side="right">
        <button
          aria-label="File Explorer"
          className={cn(
            "p-1.5 rounded-md mb-2 transition-all duration-200 relative group",
            activeView === "files"
              ? "bg-white dark:bg-[#37373D] text-[#424242] dark:text-white"
              : "text-[#616161] dark:text-[#858585] hover:text-[#424242] hover:bg-[#e8e8e8] dark:hover:text-white dark:hover:bg-[#37373D]",
            activeView === "files" &&
              "before:absolute before:left-0 before:top-[20%] before:h-[60%] before:w-[2px] before:bg-[#424242] dark:before:bg-white before:-ml-2"
          )}
          onClick={() => onViewChange("files")}
        >
          <Files className="w-5 h-5" />
        </button>
      </Tooltip>

      <Tooltip content="Search" side="right">
        <button
          aria-label="Search"
          className={cn(
            "p-1.5 rounded-md mb-2 transition-all duration-200 relative group",
            activeView === "search"
              ? "bg-white dark:bg-[#37373D] text-[#424242] dark:text-white"
              : "text-[#616161] dark:text-[#858585] hover:text-[#424242] hover:bg-[#e8e8e8] dark:hover:text-white dark:hover:bg-[#37373D]",
            activeView === "search" &&
              "before:absolute before:left-0 before:top-[20%] before:h-[60%] before:w-[2px] before:bg-[#424242] dark:before:bg-white before:-ml-2"
          )}
          onClick={() => onViewChange("search")}
        >
          <Search className="w-5 h-5" />
        </button>
      </Tooltip>



      <div className="flex-grow" />

      <Tooltip content="Terminal" side="right">
        <button
          aria-label="Terminal"
          className={cn(
            "p-1.5 opacity-70 rounded-md mb-2 transition-all duration-200 relative group ",
            showTerminal
              ? "bg-white dark:bg-[#37373D] text-[#424242] dark:text-white"
              : "text-[#616161] dark:text-[#858585] hover:text-[#424242] hover:bg-[#e8e8e8] dark:hover:text-white dark:hover:bg-[#37373D]",
              showTerminal &&
              "before:absolute before:left-0 before:top-[20%] before:h-[60%] before:w-[2px] before:bg-[#424242] dark:before:bg-white before:-ml-2"
          )}
          onClick={onToggleTerminal}
        >
          <Terminal className="w-5 h-5" />
        </button>
      </Tooltip>
    </div>
  );
}
