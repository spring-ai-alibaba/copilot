
import { useState, useRef, useEffect } from "react";
import { useTranslation } from 'react-i18next';

interface PromptEnhancedProps {
  setInput: (text: string) => void;
  input: string
}
const PromptEnhanced = (props: PromptEnhancedProps) => {
  const { setInput, input } = props || {};
  const [isOpen, setIsOpen] = useState(false);
  const [promptText, setPromptText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);
  const baseUrl = process.env.APP_BASE_URL;
  const { t } = useTranslation();
  useEffect(() => {
    if (isOpen) {
      setPromptText(input);
    }
  }, [isOpen]);
  const handleClick = async () => {
    setIsLoading(true);
    try {
      const res = await fetch(`${baseUrl}/api/enhancedPrompt`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          prompt: promptText,
        }),
      });
      const r = await res.json();
      // 后端返回的字段是 enhancedPrompt
      setInput(r.enhancedPrompt || r.text || promptText);
      setIsOpen(false);
    } catch (error) {
      console.error(t('chat.optimizePrompt.error'), error);
    } finally {
      setIsLoading(false);
    }
  };
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        popoverRef.current &&
        !popoverRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="relative">
      {isOpen ? (
        <div
          className={`absolute left-0 bottom-full mb-2 w-96 bg-white/80 dark:bg-[#1a1a1c] backdrop-blur-md rounded-lg shadow-xl p-4 border border-gray-200/50 dark:border-gray-700/50 transition-all duration-200 ease-in-out transform origin-bottom
          ${
            isOpen
              ? "opacity-100 translate-y-0 scale-100"
              : "opacity-0 translate-y-2 scale-95 pointer-events-none"
          }`}
          ref={popoverRef}
        >
          <h3 className="text-sm font-medium mb-2 text-gray-700 dark:text-gray-200">
            {t('chat.optimizePrompt.title')}
          </h3>
          <textarea
            className="w-full h-32 p-2.5 text-xs border rounded-lg bg-white/50 dark:bg-gray-700/50 dark:border-gray-600/50 dark:text-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none transition-all duration-200"
            value={promptText}
            onChange={(e) => setPromptText(e.target.value)}
            placeholder={t('chat.optimizePrompt.placeholder')}
            onKeyDown={async (e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                await handleClick();
              }
            }}
          />
          <div className="flex justify-end gap-2 mt-3">
            <button
              className="px-2.5 py-1.5 text-xs text-gray-600 hover:text-gray-700 dark:text-gray-300 dark:hover:text-gray-100 transition-colors duration-200"
              onClick={() => setIsOpen(false)}
              disabled={isLoading}
            >
              {t('chat.optimizePrompt.cancel')}
            </button>
            <button
              className={`px-3 py-1.5 text-xs text-white bg-blue-500/90 rounded hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800 transition-all duration-200 ${
                isLoading ? "opacity-70 cursor-not-allowed" : ""
              }`}
              onClick={handleClick}
              disabled={isLoading}
            >
              {isLoading ? (
                <span className="flex items-center gap-1">
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                      fill="none"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  {t('chat.optimizePrompt.processing')}
                </span>
              ) : (
                t('chat.optimizePrompt.confirm')
              )}
            </button>
          </div>
        </div>
      ) : null}
      <div
        className="mb-1 px-2 py-1 text-blue-500 text-xs rounded bg-blue-50 dark:bg-blue-500/20 dark:text-blue-400 w-fit cursor-pointer hover:bg-blue-100 dark:hover:bg-blue-500/30 transition-all duration-200 ease-in-out flex items-center gap-1"
        onClick={() => {
          setPromptText(input)
          setIsOpen(!isOpen);
        }}
      >
        <svg
          className="w-3.5 h-3.5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"
          />
        </svg>
        {t('chat.optimizePrompt.button')}
      </div>
    </div>
  );
};

export default PromptEnhanced;
