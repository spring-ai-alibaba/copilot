import React, {useRef, useState} from 'react';
import {FolderUp, Upload, X} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {useFileStore} from '@/components/WeIde/stores/fileStore';

interface FileUploadAreaProps {
  isOpen: boolean;
  onClose: () => void;
}

export function FileUploadArea({ isOpen, onClose }: FileUploadAreaProps) {
  const { t } = useTranslation();
  const { addFile, setFiles, setProjectRoot } = useFileStore();
  const [isDragOver, setIsDragOver] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    setIsUploading(true);

    const items = Array.from(e.dataTransfer.items);
    const fileMap: Record<string, string> = {};
    let hasDirectories = false;
    let rootDir = '';

    try {
      for (const item of items) {
        if (item.kind === 'file') {
          const entry = item.webkitGetAsEntry();
          if (entry) {
            if (entry.isDirectory) {
              hasDirectories = true;
              if (!rootDir) rootDir = entry.name;
              await processDirectory(entry as FileSystemDirectoryEntry, '', fileMap);
            } else if (entry.isFile) {
              const file = item.getAsFile();
              if (file) {
                const content = await file.text();
                fileMap[file.name] = content;
              }
            }
          }
        }
      }

      if (hasDirectories && rootDir) {
        setProjectRoot(rootDir);
        setFiles(fileMap);
      } else {
        // Upload individual files
        for (const [path, content] of Object.entries(fileMap)) {
          await addFile(path, content);
        }
      }
    } catch (error) {
      console.error('Error uploading files:', error);
    } finally {
      setIsUploading(false);
      onClose();
    }
  };

  const processDirectory = async (
    directoryEntry: FileSystemDirectoryEntry,
    basePath: string,
    fileMap: Record<string, string>
  ): Promise<void> => {
    const reader = directoryEntry.createReader();
    
    return new Promise((resolve, reject) => {
      reader.readEntries(async (entries) => {
        try {
          for (const entry of entries) {
            const fullPath = basePath ? `${basePath}/${entry.name}` : entry.name;
            
            if (entry.isFile) {
              const fileEntry = entry as FileSystemFileEntry;
              const file = await new Promise<File>((resolve, reject) => {
                fileEntry.file(resolve, reject);
              });
              const content = await file.text();
              fileMap[fullPath] = content;
            } else if (entry.isDirectory) {
              await processDirectory(entry as FileSystemDirectoryEntry, fullPath, fileMap);
            }
          }
          resolve();
        } catch (error) {
          reject(error);
        }
      });
    });
  };

  const handleFileInputChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files) return;

    setIsUploading(true);
    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const content = await file.text();
        await addFile(file.name, content);
      }
    } catch (error) {
      console.error('Error uploading files:', error);
    } finally {
      setIsUploading(false);
      event.target.value = '';
      onClose();
    }
  };

  const handleFolderInputChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files) return;

    setIsUploading(true);
    const fileMap: Record<string, string> = {};
    let rootDir = '';

    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const path = (file as any).webkitRelativePath;
        if (i === 0) {
          rootDir = path.split('/')[0];
        }
        const content = await file.text();
        fileMap[path] = content;
      }
      
      setProjectRoot(rootDir);
      setFiles(fileMap);
    } catch (error) {
      console.error('Error uploading folder:', error);
    } finally {
      setIsUploading(false);
      event.target.value = '';
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-96 max-w-full mx-4">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            {t('explorer.upload_file')}
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div
          className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
            isDragOver
              ? 'border-blue-400 bg-blue-50 dark:bg-blue-900/20'
              : 'border-gray-300 dark:border-gray-600'
          } ${isUploading ? 'opacity-50 pointer-events-none' : ''}`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          {isUploading ? (
            <div className="text-gray-600 dark:text-gray-400">
              <div className="animate-spin w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full mx-auto mb-2"></div>
              {t("explorer.uploading")}
            </div>
          ) : (
            <>
              <Upload className="w-12 h-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600 dark:text-gray-400 mb-4">
                {t("explorer.drag_drop_text")}
              </p>
              <div className="flex gap-2 justify-center">
                <button
                  onClick={() => fileInputRef.current?.click()}
                  className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
                >
                  <Upload className="w-4 h-4 inline mr-2" />
                  {t("explorer.select_files")}
                </button>
                <button
                  onClick={() => folderInputRef.current?.click()}
                  className="px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 transition-colors"
                >
                  <FolderUp className="w-4 h-4 inline mr-2" />
                  {t("explorer.select_folder")}
                </button>
              </div>
            </>
          )}
        </div>

        <input
          type="file"
          multiple
          ref={fileInputRef}
          onChange={handleFileInputChange}
          className="hidden"
        />
        <input
          type="file"
          webkitdirectory="true"
          ref={folderInputRef}
          onChange={handleFolderInputChange}
          className="hidden"
        />
      </div>
    </div>
  );
}
