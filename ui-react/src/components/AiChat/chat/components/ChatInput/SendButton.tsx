import React from "react";
import { ArrowUp, Square } from "lucide-react";
import { cn } from "@/utils/cn";
import type { SendButtonProps } from "./types";
import { useTranslation } from "react-i18next";

export const SendButton: React.FC<SendButtonProps> = ({
  isLoading,
  isUploading,
  hasInput,
  hasUploadingImages,
  onClick,
  stop,
}) => {
  const { t } = useTranslation();
  const disabled = !isLoading && (!hasInput || isUploading || hasUploadingImages);
  const label = isLoading
    ? t("chat.buttons.stopReceiving", { defaultValue: "停止接收" })
    : t("chat.buttons.sendMessage", { defaultValue: "发送消息" });

  return (
    <button
      type="button"
      onClick={(event) => (isLoading ? stop() : onClick(event))}
      disabled={disabled}
      className={cn(
        "flex h-8 w-8 shrink-0 items-center justify-center rounded-full transition-[background-color,color,transform,opacity] duration-150 active:scale-95",
        isLoading
          ? "bg-destructive text-destructive-foreground hover:bg-destructive/90"
          : disabled
            ? "cursor-not-allowed bg-foreground/[0.075] text-muted-foreground/55"
            : "bg-foreground text-background hover:bg-foreground/85",
      )}
      title={label}
      aria-label={label}
    >
      {isLoading ? <Square className="h-3 w-3 fill-current" /> : <ArrowUp className="h-4 w-4" />}
    </button>
  );
};
