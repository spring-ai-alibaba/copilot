import React, { useState } from 'react';
import { FolderOpen, Settings, Check, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useFileStore } from '@/components/WeIde/stores/fileStore';

interface ProjectRootSelectorProps {
  isOpen: boolean;
  onClose: () => void;
}

export function ProjectRootSelector({ isOpen, onClose }: ProjectRootSelectorProps) {
  const { t } = useTranslation();
  const { projectRoot, setProjectRoot, getFiles } = useFileStore();
  const [selectedRoot, setSelectedRoot] = useState(projectRoot);

  // Get all possible root directories from current files
  const getPossibleRoots = () => {
    const files = getFiles();
    const roots = new Set<string>();
    
    files.forEach(file => {
      const parts = file.split('/');
      if (parts.length > 1) {
        roots.add(parts[0]);
      } else {
        roots.add(''); // Root level
      }
    });
    
    return Array.from(roots).sort();
  };

  const handleSave = () => {
    setProjectRoot(selectedRoot);
    onClose();
  };

  const handleCancel = () => {
    setSelectedRoot(projectRoot);
    onClose();
  };

  if (!isOpen) return null;

  const possibleRoots = getPossibleRoots();

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-96 max-w-full mx-4">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white flex items-center">
            <Settings className="w-5 h-5 mr-2" />
            {t("explorer.project_root_settings")}
          </h3>
          <button
            onClick={handleCancel}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="mb-4">
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
            {t("explorer.select_project_root_desc")}
          </p>
          
          <div className="space-y-2">
            {possibleRoots.map((root) => (
              <label
                key={root}
                className="flex items-center p-3 border rounded-lg cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                <input
                  type="radio"
                  name="projectRoot"
                  value={root}
                  checked={selectedRoot === root}
                  onChange={(e) => setSelectedRoot(e.target.value)}
                  className="mr-3"
                />
                <FolderOpen className="w-4 h-4 mr-2 text-blue-500" />
                <span className="text-gray-900 dark:text-white">
                  {root || '/ (Root)'}
                </span>
                {root === projectRoot && (
                  <span className="ml-auto text-xs text-green-600 dark:text-green-400">
                    {t("explorer.current")}
                  </span>
                )}
              </label>
            ))}
          </div>

          {possibleRoots.length === 0 && (
            <div className="text-center py-8 text-gray-500 dark:text-gray-400">
              <FolderOpen className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>{t("explorer.no_files_found")}</p>
            </div>
          )}
        </div>

        <div className="flex gap-2 justify-end">
          <button
            onClick={handleCancel}
            className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200 transition-colors"
          >
            {t("explorer.cancel")}
          </button>
          <button
            onClick={handleSave}
            disabled={possibleRoots.length === 0}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center"
          >
            <Check className="w-4 h-4 mr-2" />
            {t("explorer.save")}
          </button>
        </div>
      </div>
    </div>
  );
}
