import React from 'react';
import { useTranslation } from 'react-i18next';

interface OtherConfig {
  isBackEnd: boolean;
  backendLanguage: string;
  extra?: {
    database?: string;
    databaseConfig?: {
      url?: string;
      username?: string;
      password?: string;
    };
  };
}

interface BackendSettingsProps {
  otherConfig: OtherConfig;
  updateConfig: (config: OtherConfig) => void;
}

export function BackendSettings({ otherConfig, updateConfig }: BackendSettingsProps) {
  const { t } = useTranslation();

  return (
    <>
      <div className="mb-4">
        <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm">
          {t('settings.backend.enable')}
        </label>
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={otherConfig.isBackEnd}
            onChange={(e) => {
              const newConfig = { ...otherConfig, isBackEnd: e.target.checked };
              updateConfig(newConfig);
            }}
            className="sr-only peer"
          />
          <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
        </label>
      </div>

      {otherConfig.isBackEnd && (
        <div className="mb-4">
          <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm">
            {t('settings.backend.language')}
          </label>
          <select
            value={otherConfig.backendLanguage}
            onChange={(e) => {
              const newConfig = { ...otherConfig, backendLanguage: e.target.value };
              updateConfig(newConfig);
            }}
            className="w-full px-2.5 py-1.5 bg-white dark:bg-[#1e1e1e] border border-border rounded-md text-foreground focus:outline-none focus:border-blue-500 text-sm"
          >
            <option value="Java">Java</option>
            <option value="Node">Node</option>
            <option value="Go">Go</option>
            <option value="Python">Python</option>
          </select>
        </div>
      )}

      {otherConfig.isBackEnd && (
        <div className="mb-4">
          <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm">
            {t('settings.backend.database.type')}
          </label>
          <select
            value={otherConfig.extra?.database || 'none'}
            onChange={(e) => {
              const newConfig = {
                ...otherConfig,
                extra: {
                  ...otherConfig.extra,
                  database: e.target.value
                }
              };
              updateConfig(newConfig);
            }}
            className="w-full px-2.5 py-1.5 bg-white dark:bg-[#1e1e1e] border border-border rounded-md text-foreground focus:outline-none focus:border-blue-500 text-sm"
          >
            <option value="none">{t('settings.backend.database.none')}</option>
            <option value="mysql">MySQL</option>
          </select>
        </div>
      )}

      {otherConfig.isBackEnd && otherConfig.extra?.database !== 'none' && (
        <div className="mb-4">
          <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm">
            {t('settings.backend.database.url')}
          </label>
          <input
            type="text"
            value={otherConfig.extra?.databaseConfig?.url || ''}
            onChange={(e) => {
              const newConfig = {
                ...otherConfig,
                extra: {
                  ...otherConfig.extra,
                  databaseConfig: {
                    ...otherConfig.extra?.databaseConfig,
                    url: e.target.value
                  }
                }
              };
              updateConfig(newConfig);
            }}
            placeholder="localhost:3306"
            className="w-full px-2.5 py-1.5 bg-white dark:bg-[#1e1e1e] border border-border rounded-md text-foreground focus:outline-none focus:border-blue-500 text-sm"
          />
          <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm mt-4">
            {t('settings.backend.database.username')}
          </label>
          <input
            type="text"
            value={otherConfig.extra?.databaseConfig?.username || ''}
            onChange={(e) => {
              const newConfig = {
                ...otherConfig,
                extra: {
                  ...otherConfig.extra,
                  databaseConfig: {
                    ...otherConfig.extra?.databaseConfig,
                    username: e.target.value
                  }
                }
              };
              updateConfig(newConfig);
            }}
            placeholder={t('settings.backend.database.username')}
            className="w-full px-2.5 py-1.5 bg-white dark:bg-[#1e1e1e] border border-border rounded-md text-foreground focus:outline-none focus:border-blue-500 text-sm"
          />
          <label className="block text-gray-500 dark:text-gray-300 mb-1.5 text-sm mt-4">
            {t('settings.backend.database.password')}
          </label>
          <input
            type="password"
            value={otherConfig.extra?.databaseConfig?.password || ''}
            onChange={(e) => {
              const newConfig = {
                ...otherConfig,
                extra: {
                  ...otherConfig.extra,
                  databaseConfig: {
                    ...otherConfig.extra?.databaseConfig,
                    password: e.target.value
                  }
                }
              };
              updateConfig(newConfig);
            }}
            placeholder={t('settings.backend.database.password')}
            className="w-full px-2.5 py-1.5 bg-white dark:bg-[#1e1e1e] border border-border rounded-md text-foreground focus:outline-none focus:border-blue-500 text-sm"
          />
        </div>
      )}
    </>
  );
} 