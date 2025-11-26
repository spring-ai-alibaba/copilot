import React from "react";
import {message, Tooltip} from "antd";

import {updateFileSystemNow} from "./WeIde/services";
import {useFileStore} from "./WeIde/stores/fileStore";
import {ActionButton} from "./Header/ActionButton";
import {useTranslation} from "react-i18next";
import useTerminalStore from "@/stores/terminalSlice";

export const OpenDirectoryButton: React.FC = () => {
  const { t } = useTranslation();

  const handleOpenDirectory = async () => {
    message.info(t("header.error.feature_not_available_in_web"));
  };

  return (
    <Tooltip
      title={
        <div className="text-xs">
          <div className="font-medium mb-1 text-[#333] dark:text-white">
            {t("header.open_directory")}
          </div>
          <div className="text-[#666] dark:text-gray-300">
            {t("header.open_directory_web_tips")}
          </div>
        </div>
      }
      overlayClassName="bg-white dark:bg-[#1a1a1c] border border-[#e5e5e5] dark:border-[#454545] shadow-lg"
      overlayInnerStyle={{
        padding: "8px 12px",
        borderRadius: "6px",
      }}
      placement="bottom"
    >
      <div className="relative">
        <ActionButton
          onClick={handleOpenDirectory}
          icon={"open"}
          label={t("header.open_directory")}
          className="text-[#424242] dark:text-gray-300 hover:text-[#000] dark:hover:text-white
            bg-white dark:bg-[#333333] hover:bg-[#f5f5f5] dark:hover:bg-[#404040]
            border border-[#e5e5e5] dark:border-[#252525]
            shadow-sm hover:shadow transition-all opacity-50 cursor-not-allowed"
        />
      </div>
    </Tooltip>
  );
};
