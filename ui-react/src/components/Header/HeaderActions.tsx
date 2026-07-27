import { useEffect, useRef, useState } from "react";
import {
  CloudUpload,
  Download,
  FolderOpen,
  MoreHorizontal,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import { ChatMode } from "@/types/chat";
import { cn } from "@/utils/cn";
import useChatModeStore from "@/stores/chatModeSlice";
import { useFileStore } from "../WeIde/stores/fileStore";

function downloadBlob(blob: Blob, name: string) {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(url);
}

export function HeaderActions() {
  const files = useFileStore((state) => state.files);
  const { t } = useTranslation();
  const { mode } = useChatModeStore();
  const [menuOpen, setMenuOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    window.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [menuOpen]);

  if (mode !== ChatMode.Builder) return null;

  const handleDownload = async () => {
    setMenuOpen(false);
    try {
      const { default: JSZip } = await import("jszip");
      const zip = new JSZip();
      Object.entries(files).forEach(([path, content]) => zip.file(path, content));
      downloadBlob(await zip.generateAsync({ type: "blob" }), "project.zip");
      toast.success(t("header.downloadSuccess", { defaultValue: "项目已打包下载" }));
    } catch (error) {
      console.error("下载失败:", error);
      toast.error(t("header.error.download_failed", { defaultValue: "项目打包失败" }));
    }
  };

  return (
    <>
      <div ref={rootRef} className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen((current) => !current)}
          className={cn("arc-icon-button", menuOpen && "bg-foreground/[0.06] text-foreground")}
          title={t("header.projectActions", { defaultValue: "项目操作" })}
          aria-label={t("header.projectActions", { defaultValue: "项目操作" })}
          aria-expanded={menuOpen}
        >
          <MoreHorizontal className="h-4 w-4" />
        </button>

        {menuOpen ? (
          <div className="arc-popover absolute right-0 top-9 z-50 w-52 p-1.5">
            <button type="button" onClick={() => void handleDownload()} className="arc-popover-item">
              <Download className="h-4 w-4" />
              <span className="flex-1">{t("header.download", { defaultValue: "下载项目" })}</span>
              <span className="text-[10px] text-muted-foreground">ZIP</span>
            </button>
            <button
              type="button"
              disabled
              className="arc-popover-item"
              title={t("header.deployPendingDescription", {
                defaultValue: "部署后端尚未接入",
              })}
            >
              <CloudUpload className="h-4 w-4" />
              <span className="flex-1">{t("header.deploy", { defaultValue: "部署" })}</span>
              <span className="text-[10px] text-muted-foreground">
                {t("header.pending", { defaultValue: "待接入" })}
              </span>
            </button>
            {window.electron ? (
              <button
                type="button"
                disabled
                className="arc-popover-item"
                title={t("header.error.feature_not_available_in_web", {
                  defaultValue: "此运行环境暂不支持直接打开目录",
                })}
              >
                <FolderOpen className="h-4 w-4" />
                <span>
                  {t("header.openInFolder", { defaultValue: "在文件夹中打开" })}
                </span>
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </>
  );
}
