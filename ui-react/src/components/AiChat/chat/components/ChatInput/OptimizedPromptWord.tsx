import { useEffect, useRef, useState } from "react";
import { LoaderCircle, Sparkles, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { apiUrl } from "@/api/base";
import { Button } from "@/components/ui/button";

interface PromptEnhancedProps {
  setInput: (text: string) => void;
  input: string;
}

const PromptEnhanced = ({ setInput, input }: PromptEnhancedProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [promptText, setPromptText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);
  const { t } = useTranslation();

  useEffect(() => {
    if (isOpen) setPromptText(input);
  }, [input, isOpen]);

  useEffect(() => {
    const handleClickOutside = (event: PointerEvent) => {
      if (!popoverRef.current?.contains(event.target as Node)) setIsOpen(false);
    };
    window.addEventListener("pointerdown", handleClickOutside);
    return () => window.removeEventListener("pointerdown", handleClickOutside);
  }, []);

  const handleEnhance = async () => {
    if (!promptText.trim() || isLoading) return;
    setIsLoading(true);
    try {
      const response = await fetch(apiUrl("/api/enhancedPrompt"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt: promptText }),
      });
      if (!response.ok) throw new Error(`Prompt enhancement failed: ${response.status}`);
      const result = await response.json();
      setInput(result.enhancedPrompt || result.text || promptText);
      setIsOpen(false);
    } catch (error) {
      console.error(t("chat.optimizePrompt.error", { defaultValue: "提示词优化失败" }), error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div ref={popoverRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((current) => !current)}
        className="inline-flex h-7 items-center gap-1.5 rounded-full border border-border/65 bg-background/75 px-2.5 text-[11px] text-muted-foreground shadow-sm backdrop-blur transition-colors hover:bg-muted/70 hover:text-foreground"
      >
        <Sparkles className="h-3 w-3" />
        {t("chat.optimizePrompt.button", { defaultValue: "优化提示词" })}
      </button>

      {isOpen ? (
        <div className="arc-popover absolute bottom-9 left-0 z-50 w-[420px] max-w-[calc(100vw-32px)] p-3">
          <div className="flex items-center justify-between gap-3 px-1 pb-2">
            <div>
              <div className="text-xs font-semibold text-foreground">
                {t("chat.optimizePrompt.title", { defaultValue: "优化提示词" })}
              </div>
              <div className="mt-0.5 text-[10px] text-muted-foreground">
                补充任务目标、约束和期望输出格式
              </div>
            </div>
            <button
              type="button"
              className="arc-icon-button h-7 w-7"
              onClick={() => setIsOpen(false)}
              aria-label="关闭"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
          <textarea
            autoFocus
            className="h-32 w-full resize-none rounded-xl border border-border bg-background/70 p-3 text-xs leading-5 text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-foreground/20"
            value={promptText}
            onChange={(event) => setPromptText(event.target.value)}
            placeholder={t("chat.optimizePrompt.placeholder", {
              defaultValue: "描述你希望 Agent 完成的任务…",
            })}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                void handleEnhance();
              }
            }}
          />
          <div className="mt-3 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setIsOpen(false)} disabled={isLoading}>
              {t("chat.optimizePrompt.cancel", { defaultValue: "取消" })}
            </Button>
            <Button size="sm" onClick={() => void handleEnhance()} disabled={isLoading || !promptText.trim()}>
              {isLoading ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
              {isLoading
                ? t("chat.optimizePrompt.processing", { defaultValue: "正在优化" })
                : t("chat.optimizePrompt.confirm", { defaultValue: "开始优化" })}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default PromptEnhanced;
