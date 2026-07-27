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
  return (
    <div className="flex w-11 flex-col items-center border-r border-border/65 bg-workbench py-2">
      <Tooltip content="File Explorer" side="right">
        <button
          aria-label="File Explorer"
          className={cn(
            "group relative mb-1.5 rounded-lg p-1.5 transition-colors",
            activeView === "files"
              ? "bg-workbench-active text-foreground"
              : "text-muted-foreground hover:bg-foreground/[0.055] hover:text-foreground",
            activeView === "files" &&
              "before:absolute before:-left-2 before:top-[25%] before:h-1/2 before:w-0.5 before:rounded-full before:bg-foreground"
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
            "group relative mb-1.5 rounded-lg p-1.5 transition-colors",
            activeView === "search"
              ? "bg-workbench-active text-foreground"
              : "text-muted-foreground hover:bg-foreground/[0.055] hover:text-foreground",
            activeView === "search" &&
              "before:absolute before:-left-2 before:top-[25%] before:h-1/2 before:w-0.5 before:rounded-full before:bg-foreground"
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
            "group relative mb-1.5 rounded-lg p-1.5 transition-colors",
            showTerminal
              ? "bg-workbench-active text-foreground"
              : "text-muted-foreground hover:bg-foreground/[0.055] hover:text-foreground",
              showTerminal &&
              "before:absolute before:-left-2 before:top-[25%] before:h-1/2 before:w-0.5 before:rounded-full before:bg-foreground"
          )}
          onClick={onToggleTerminal}
        >
          <Terminal className="w-5 h-5" />
        </button>
      </Tooltip>
    </div>
  );
}
