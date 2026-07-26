import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { FolderTree, Settings, Upload, RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useState } from 'react';
import { message } from 'antd';
import { Tooltip } from '@/components/WeIde/components/Tooltip';
import { FileUploadArea } from './FileUploadArea';
import { ProjectRootSelector } from './ProjectRootSelector';
import { refreshIndex, getWorkspacePath } from '@/api/knowledge';

export function Header() {
  const { setFiles, setIsFirstSend, setIsUpdateSend, projectRoot } = useFileStore();
  const { t } = useTranslation();
  const [showUploadArea, setShowUploadArea] = useState(false);
  const [showRootSelector, setShowRootSelector] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

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

  const handleRefreshIndex = async () => {
    if (isRefreshing) return;

    setIsRefreshing(true);
    const hide = message.loading('正在刷新知识库索引...', 0);

    try {
      // 从后端获取 workspace 的绝对路径
      console.log('获取 workspace 路径...');
      const workspacePath = await getWorkspacePath();
      console.log('刷新知识库索引, path:', workspacePath);

      await refreshIndex(workspacePath);

      hide();
      message.success('索引刷新完成');
    } catch (error) {
      hide();
      console.error('刷新索引失败:', error);
      message.error('刷新索引失败: ' + (error instanceof Error ? error.message : String(error)));
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div>
      <div className="space-y-1.5">
        <h2 className="flex min-w-0 select-none items-center text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
          <FolderTree className="mr-1.5 h-3.5 w-3.5 shrink-0" />
          <span className="truncate whitespace-nowrap">{t("explorer.explorer")}</span>
        </h2>
        <div className="flex min-w-0 items-center justify-end gap-1">
          <Tooltip content={t("explorer.project_settings")} side="bottom">
            <Settings
              className="h-3.5 w-3.5 cursor-pointer text-muted-foreground transition-colors hover:text-foreground"
              onClick={handleSettingsClick}
            />
          </Tooltip>
          <Tooltip content="刷新知识库索引" side="bottom">
            <RefreshCw
              className={`h-3.5 w-3.5 cursor-pointer text-muted-foreground transition-colors hover:text-foreground ${isRefreshing ? 'animate-spin' : ''
                }`}
              onClick={handleRefreshIndex}
            />
          </Tooltip>
          <Tooltip content={t("explorer.upload_file")} side="bottom">
            <Upload
              className="h-3.5 w-3.5 cursor-pointer text-muted-foreground transition-colors hover:text-foreground"
              onClick={handleUploadClick}
            />
          </Tooltip>
          <span
            onClick={handleClearAll}
            className="ml-auto cursor-pointer whitespace-nowrap text-[9px] text-muted-foreground transition-colors hover:text-foreground"
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
