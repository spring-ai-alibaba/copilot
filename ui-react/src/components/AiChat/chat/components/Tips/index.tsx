import { useRef, useState } from "react";
import { Code2, Globe2, ImagePlus, MessageSquareText, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { AppLogo } from "@/components/AppLogo";
import useChatModeStore from "@/stores/chatModeSlice";
import { ChatMode } from "../ChatInput";
import { UrlInputDialog } from "../UrlInputDialog";

interface TipsProps {
  setInput: (value: string) => void;
  append: (message: { role: "user" | "assistant"; content: string }) => void;
  handleFileSelect: (event: React.ChangeEvent<HTMLInputElement>) => void;
  handleSketchUpload?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

const Tips = ({ handleFileSelect, setInput, append }: TipsProps) => {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { mode } = useChatModeStore();
  const [isUrlDialogOpen, setIsUrlDialogOpen] = useState(false);

  const suggestions =
    mode === ChatMode.Builder
      ? [
          t("chat.tips.game", { defaultValue: "创建一个可玩的网页小游戏" }),
          t("chat.tips.hello", { defaultValue: "生成一个现代化的产品首页" }),
          t("chat.tips.projectAnalysis", {
            defaultValue: "分析当前项目并提出可以实施的改进",
          }),
        ]
      : [
          t("chat.tips.summarize", { defaultValue: "总结这份材料的重点" }),
          t("chat.tips.breakDownTask", { defaultValue: "帮我拆解一个复杂任务" }),
          t("chat.tips.ideas", { defaultValue: "给我几个可落地的创意方向" }),
        ];

  return (
    <div className="flex min-h-[46vh] flex-col items-center justify-center pb-8 text-center">
      <AppLogo className="h-11 w-11 rounded-2xl" />
      <h1 className="mt-4 text-xl font-semibold tracking-tight text-foreground">
        {mode === ChatMode.Builder
          ? t("chat.tips.builderTitle", { defaultValue: "今天想构建什么？" })
          : t("chat.tips.chatTitle", { defaultValue: "我能帮你做什么？" })}
      </h1>
      <p className="mt-2 max-w-md text-xs leading-5 text-muted-foreground">
        {mode === ChatMode.Builder
          ? t("chat.tips.builderDescription", {
              defaultValue:
                "描述目标、引用工作区文件，Agent 会在右侧工作区中创建并运行项目。",
            })
          : t("chat.tips.chatDescription", {
              defaultValue: "提出问题，或者把资料和图片加入上下文。",
            })}
      </p>

      <div className="mt-6 grid w-full max-w-xl grid-cols-1 gap-2 sm:grid-cols-2">
        {mode === ChatMode.Builder ? (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="arc-starter-card"
          >
            <span className="arc-starter-icon"><ImagePlus className="h-4 w-4" /></span>
            <span className="min-w-0 text-left">
              <span className="block text-xs font-medium text-foreground">
                {t("chat.tips.imageStartTitle", { defaultValue: "从图片开始" })}
              </span>
              <span className="mt-0.5 block text-[10px] text-muted-foreground">
                {t("chat.tips.imageStartDescription", {
                  defaultValue: "上传设计稿或参考图",
                })}
              </span>
            </span>
          </button>
        ) : (
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="arc-starter-card"
          >
            <span className="arc-starter-icon"><MessageSquareText className="h-4 w-4" /></span>
            <span className="min-w-0 text-left">
              <span className="block text-xs font-medium text-foreground">
                {t("chat.tips.addImageTitle", { defaultValue: "添加图片" })}
              </span>
              <span className="mt-0.5 block text-[10px] text-muted-foreground">
                {t("chat.tips.addImageDescription", {
                  defaultValue: "让模型查看视觉内容",
                })}
              </span>
            </span>
          </button>
        )}

        <button
          type="button"
          onClick={() => setIsUrlDialogOpen(true)}
          className="arc-starter-card"
        >
          <span className="arc-starter-icon"><Globe2 className="h-4 w-4" /></span>
          <span className="min-w-0 text-left">
            <span className="block text-xs font-medium text-foreground">
              {t("chat.tips.webTitle", { defaultValue: "引用网页" })}
            </span>
            <span className="mt-0.5 block text-[10px] text-muted-foreground">
              {t("chat.tips.webDescription", {
                defaultValue: "从公开 URL 获取上下文",
              })}
            </span>
          </span>
        </button>
      </div>

      <div className="mt-4 flex max-w-xl flex-wrap justify-center gap-2">
        {suggestions.map((suggestion, index) => (
          <button
            key={`${suggestion}-${index}`}
            type="button"
            onClick={() => setInput(suggestion)}
            className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-background/65 px-3 py-1.5 text-[11px] text-muted-foreground transition-colors hover:bg-muted/65 hover:text-foreground"
          >
            {mode === ChatMode.Builder ? <Code2 className="h-3 w-3" /> : <Sparkles className="h-3 w-3" />}
            {suggestion}
          </button>
        ))}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        onChange={handleFileSelect}
        className="hidden"
        multiple
        accept="image/*"
      />

      <UrlInputDialog
        isOpen={isUrlDialogOpen}
        onClose={() => setIsUrlDialogOpen(false)}
        onSubmit={(url) => append({ role: "user", content: `#${url}` })}
      />
    </div>
  );
};

export default Tips;
