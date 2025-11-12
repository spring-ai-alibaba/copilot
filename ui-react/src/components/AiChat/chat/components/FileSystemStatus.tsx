import React, {useEffect, useState} from 'react';
import {isFileSystemEnabled} from '@/api/filesystem';

interface FileSystemStatusProps {
  className?: string;
}

export const FileSystemStatus: React.FC<FileSystemStatusProps> = ({ className }) => {
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkStatus = async () => {
      try {
        const isEnabled = await isFileSystemEnabled();
        setEnabled(isEnabled);
      } catch (error) {
        console.error('Failed to check file system status:', error);
      } finally {
        setLoading(false);
      }
    };

    checkStatus();
  }, []);

  if (loading) {
    return (
      <div className={`flex items-center gap-2 text-sm text-gray-500 ${className || ''}`}>
        <div className="w-2 h-2 bg-gray-400 rounded-full animate-pulse" />
        <span>检查文件系统状态...</span>
      </div>
    );
  }

  return (
    <div className={`flex items-center gap-2 text-sm ${className || ''}`}>
      {/*<div*/}
      {/*  className={`w-2 h-2 rounded-full ${*/}
      {/*    enabled*/}
      {/*      ? 'bg-green-500 dark:bg-green-400'*/}
      {/*      : 'bg-gray-400 dark:bg-gray-500'*/}
      {/*  }`}*/}
      {/*/>*/}
      {/*<span className={enabled ? 'text-green-600 dark:text-green-400' : 'text-gray-600 dark:text-gray-400'}>*/}
      {/*  {enabled ? '文件系统已启用' : '文件系统未启用'}*/}
      {/*</span>*/}
    </div>
  );
};
