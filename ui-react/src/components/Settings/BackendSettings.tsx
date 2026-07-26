import React from 'react';
import {useTranslation} from 'react-i18next';

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
            className="w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-sm text-foreground outline-none focus:border-foreground/20"
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
            className="w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-sm text-foreground outline-none focus:border-foreground/20"
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
            className="w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-sm text-foreground outline-none focus:border-foreground/20"
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
            className="w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-sm text-foreground outline-none focus:border-foreground/20"
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
            className="w-full rounded-lg border border-border bg-background px-2.5 py-1.5 text-sm text-foreground outline-none focus:border-foreground/20"
          />
        </div>
      )}
    </>
  );
}
