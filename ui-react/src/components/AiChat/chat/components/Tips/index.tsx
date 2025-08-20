import { useTranslation } from "react-i18next";
import { MessageSquare, Upload, Code2, Globe } from "lucide-react";
import { useRef, useState } from "react";
import { ChatMode } from "../ChatInput";
import useChatModeStore from "@/stores/chatModeSlice";
import { UrlInputDialog } from "../UrlInputDialog";
import { Logo } from "@/components/Logo";

interface TipsProps {
  setInput: (s: string) => void;
  append: (message: { role: 'user' | 'assistant'; content: string }) => void;
  handleFileSelect: (e: React.ChangeEvent<HTMLInputElement>) => void;
  handleSketchUpload?: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

const Tips = (props: TipsProps) => {
  const { handleFileSelect, setInput, append } = props;
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const sketchInputRef = useRef<HTMLInputElement>(null);
  const { mode, initOpen } = useChatModeStore();
  const [isUrlDialogOpen, setIsUrlDialogOpen] = useState(false);

  const handleUrlSubmit = (url: string): void => {
    append({
      role: "user",
      content: `#${url}`,
    });
  };

  return (
    <div className="flex flex-col items-center gap-4 text-gray-500 dark:text-gray-400">
      {initOpen ? (
        <div className="flex flex-col w-full max-w-3xl gap-8 p-8 mx-auto">
          <div className="space-y-4 text-center justify-between items-center flex flex-col">
            <Logo />
            <h1 className="mb-6 font-bold text-white text-7xl">
              {t("chat.tips.title")}
            </h1>

            <p className="text-lg text-gray-400">
              you can generate java python js
            </p>
          </div>

          <div className="flex flex-col gap-6">
            {mode === ChatMode.Builder && (
              <div className="grid grid-cols-2 gap-4">
                <button className="w-full p-4 transition-colors border rounded-lg bg-zinc-800/50 border-zinc-700/50 hover:bg-zinc-800">
                  <div className="flex items-center gap-3 text-gray-300">
                    <Upload className="w-3 h-3" />
                    <span className="text-sm">
                      {t("chat.tips.uploadSketch")}
                    </span>
                  </div>
                </button>
                <button
                  className="w-full p-4 transition-colors border rounded-lg bg-zinc-800/50 border-zinc-700/50 hover:bg-zinc-800"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <div className="flex items-center gap-3 text-gray-300">
                    <Upload className="w-3 h-3" />
                    <span className="text-sm">{t("chat.tips.uploadImg")}</span>
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="flex flex-col w-full gap-2 p-4 transition-colors border border-gray-200 rounded-lg dark:border-gray-600/30 bg-white/50 dark:bg-transparent hover:bg-gray-100 dark:hover:bg-gray-700/10">
          <div className="flex items-center gap-2">
            <Code2 className="w-5 h-5 text-blue-500 dark:text-blue-400" />
            <span className="font-medium text-gray-900 dark:text-gray-300">
              {t("chat.tips.title")}
            </span>
          </div>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {t("chat.tips.description")}
          </p>
          <div className="flex flex-col gap-2 mt-2">
            
            <div className="flex  gap-2">
              <div
                className="flex items-center gap-2 mt-2 mr-4 text-xs text-gray-600 transition-colors cursor-pointer hover:text-blue-500 dark:text-gray-400 dark:hover:text-blue-400"
                onClick={() => {
                  fileInputRef.current?.click();
                }}
              >
                <Upload className="w-4 h-4" />
                <span className="text-sm">{t("chat.tips.uploadImg")}</span>
              </div>
              <div
                className="flex items-center gap-2 mt-2 text-xs text-gray-600 transition-colors cursor-pointer hover:text-blue-500 dark:text-gray-400 dark:hover:text-blue-400"
                onClick={() => setIsUrlDialogOpen(true)}
              >
                <Globe className="w-4 h-4" />
                <span className="text-sm">{t("chat.tips.uploadWebsite")}</span>
              </div>
            </div>

            {mode === ChatMode.Builder && (
              <div className="flex flex-wrap gap-2 mt-2 text-xs">
                <span
                  onClick={() => {
                    setInput(t("chat.tips.game"));
                  }}
                  className="px-2 py-1 text-blue-500 rounded bg-blue-50 dark:bg-blue-500/20 dark:text-blue-400"
                >
                  {t("chat.tips.game")}
                </span>
                <span
                  onClick={() => {
                    setInput(t("chat.tips.hello"));
                  }}
                  className="px-2 py-1 text-blue-500 rounded bg-blue-50 dark:bg-blue-500/20 dark:text-blue-400"
                >
                  {t("chat.tips.hello")}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

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
        onSubmit={handleUrlSubmit}
      />
    </div>
  );
  
};

export default Tips;
