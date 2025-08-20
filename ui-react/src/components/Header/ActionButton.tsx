import React from "react";
import { IconDownload } from "./icons/IconDownload";
import { IconDeploy } from "./icons/IconDeploy";
import { FolderOpen } from 'lucide-react';
import { cn } from "@/utils/cn";


const icons = {
  download: IconDownload,
  deploy: IconDeploy,
  open: FolderOpen,
};

interface ActionButtonProps {
  icon: keyof typeof icons;
  label: string;
  variant?: "default" | "primary";
  onClick?: () => void;
  className?: string;
}

export function ActionButton({
  icon,
  label,
  variant = "default",
  onClick,
  className,
}: ActionButtonProps) {
  const Icon = icons[icon];

  const variantClasses = {
    default: cn(
      "bg-white dark:bg-[#333333]",
      "text-[#424242] dark:text-gray-300",
      "hover:bg-[#f5f5f5] dark:hover:bg-[#404040]",
      "hover:text-[#000000] dark:hover:text-white",
      "border border-[#e5e5e5] dark:border-[#252525]"
    ),
    primary: cn(
      "bg-[#0078d4] dark:bg-[#0078d4]",
      "text-white dark:text-white",
      "hover:bg-[#106ebe] dark:hover:bg-[#106ebe]",
      "border-transparent"
    ),
  };

  return (
    <button
      className={cn(
        "flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium",
        "transition-all duration-200 ease-in-out",
        "shadow-sm hover:shadow",
        variantClasses[variant],
        className
      )}
      onClick={onClick}
    >
      <Icon className="w-4 h-4" />
      <span>{label}</span>
    </button>
  );
}
