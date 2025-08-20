import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { FolderTree } from "lucide-react";
import { useTranslation } from "react-i18next";

export function Header() {
  const { setFiles, setIsFirstSend, setIsUpdateSend } = useFileStore();
  const { t } = useTranslation();
  const handleClearAll = () => {
    setFiles({});
    setIsFirstSend();
    setIsUpdateSend();
  };

  return (
    <div className="flex items-center justify-between">
      <h2 className="text-[13px] uppercase font-semibold mb-2 flex items-center text-[#424242] dark:text-gray-400 select-none">
        <FolderTree className="w-4 h-4 mr-1.5" /> {t("explorer.explorer")}
      </h2>
      <div onClick={handleClearAll} className="flex mb-2">
        <span className="text-[10px] text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300">
          {t("explorer.clear_all")}
        </span>
      </div>
    </div>
  );
}
