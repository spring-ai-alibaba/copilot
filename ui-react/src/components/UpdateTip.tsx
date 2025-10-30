import {useEffect, useState} from "react";
import {authService} from "../api/appInfo";
import {useTranslation} from "react-i18next";

interface VersionInfo {
  version: string;
  content: string;
  date: string;
}

export const UpdateTip: React.FC = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [versionInfo, setVersionInfo] = useState<VersionInfo | null>(null);
  const { t } = useTranslation();

  useEffect(() => {
    const fetchAppInfo = async () => {
      try {
        const data = await authService.appInfo();
        // Check if data and versionInfo exist before accessing properties
        if (data && data.versionInfo && data.versionInfo.version) {
          const verison = data.versionInfo.version;
          const localVersion = localStorage.getItem("version");
          if (!localVersion) {
            localStorage.setItem("version", verison);
          } else if (verison !== localVersion) {
            localStorage.setItem("version", verison);
            setVersionInfo(data.versionInfo);
            setIsVisible(true);
          }
        } else {
          console.warn("Version info not available in API response");
        }
      } catch (error) {
        console.error("get error:", error);
      }
    };

    fetchAppInfo();
  }, []);

  if (!isVisible || !versionInfo) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="relative w-[90%] max-w-md p-6 rounded-lg shadow-xl bg-white dark:bg-[#222]">
        <button
          onClick={() => setIsVisible(false)}
          className="absolute top-3 right-3 text-2xl text-gray-600 dark:text-gray-300 hover:opacity-70 transition-opacity"
        >
          ×
        </button>

        <h2 className="text-xl font-semibold mb-4 text-gray-800 dark:text-white">
          {t('update.version_update')} {versionInfo.version}
        </h2>

        <div className="mb-10 leading-relaxed text-gray-600 dark:text-gray-300 whitespace-pre-line">
          {versionInfo.content}
        </div>

        <div className="text-sm text-gray-500 dark:text-gray-400">
          {t('update.release_date')}：{versionInfo.date}
        </div>
      </div>
    </div>
  );
};

export default UpdateTip;
