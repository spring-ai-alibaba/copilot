import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { FolderTree, Upload, Settings } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useState } from 'react';
import { FileUploadArea } from './FileUploadArea';
import { ProjectRootSelector } from './ProjectRootSelector';

export function Header() {
  const { setFiles, setIsFirstSend, setIsUpdateSend } = useFileStore();
  const { t } = useTranslation();
  const [showUploadArea, setShowUploadArea] = useState(false);
  const [showRootSelector, setShowRootSelector] = useState(false);

  const handleClearAll = () => {
    setFiles({});
    setIsFirstSend();
    setIsUpdateSend();
  };

  const handleUploadClick = () => {
    setShowUploadArea(true);
  };

  const handleSettingsClick = () => {
    setShowRootSelector(true);
  };

  return (
    <div>
      <div className="flex items-center justify-between">
        <h2 className="text-[13px] uppercase font-semibold mb-2 flex items-center text-[#424242] dark:text-gray-400 select-none">
          <FolderTree className="w-4 h-4 mr-1.5" /> {t("explorer.explorer")}
        </h2>
        <div className="flex items-center mb-2 space-x-2">
          <Settings
            title={t("explorer.project_settings")}
            className="w-4 h-4 text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
            onClick={handleSettingsClick}
          />
          <Upload
            title={t("explorer.upload_file")}
            className="w-4 h-4 text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
            onClick={handleUploadClick}
          />
          <span
            onClick={handleClearAll}
            className="text-[10px] text-[#616161] dark:text-gray-400 cursor-pointer hover:text-[#333] dark:hover:text-gray-300"
          >
            {t("explorer.clear_all")}
          </span>
        </div>
      </div>

      <FileUploadArea
        isOpen={showUploadArea}
        onClose={() => setShowUploadArea(false)}
      />

      <ProjectRootSelector
        isOpen={showRootSelector}
        onClose={() => setShowRootSelector(false)}
      />
    </div>
  );
}
