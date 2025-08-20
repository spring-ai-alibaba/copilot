import { useState, useCallback } from "react";
import { FileTreeItem } from "./FileTreeItem";
import { CreateFileDialog } from "./CreateFileDialog";
import { CreateFolderDialog } from "./CreateFolderDialog";
import { createFile, createFolder } from "../utils/fileSystem";
import { FileTreeProps } from "../types";
import { cn } from "@/utils/cn";

export function FileTree({ items, onFileSelect }: FileTreeProps) {
  // 默认展开的文件夹
  const defaultExpanded = {
    src: true,
    features: true,
    components: true,
  };

  const [expandedFolders, setExpandedFolders] =
    useState<Record<string, boolean>>(defaultExpanded);
  const [showCreateFile, setShowCreateFile] = useState(false);
  const [showCreateFolder, setShowCreateFolder] = useState(false);
  const [selectedPath, setSelectedPath] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  const toggleFolder = useCallback((path: string, isExpend?: boolean) => {
    setExpandedFolders((prev) => ({
      ...prev,
      [path]: typeof isExpend === "boolean" ? isExpend : !prev[path],
    }));
  }, []);

  const handleCreateFile = async (fileName: string) => {
    const newPath = await createFile(selectedPath, fileName);
    setShowCreateFile(false);
    onFileSelect(newPath);
  };

  const handleCreateFolder = (folderName: string) => {
    createFolder(selectedPath, folderName);
    setShowCreateFolder(false);
    setExpandedFolders((prev) => ({
      ...prev,
      [selectedPath]: true,
    }));
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    // 处理文件拖拽逻辑
  };

  return (
    <>
      <div
        className={cn(
          "flex flex-col h-full overflow-auto",
          "scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-transparent",
          "px-1 py-2",
          isDragging && "bg-gray-800/20"
        )}
        role="tree"
        aria-label="文件浏览器"
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        {items.map((item) => (
          <FileTreeItem
            key={item.path}
            item={item}
            expandedFolders={expandedFolders}
            expanded={!!expandedFolders[item.path]}
            onToggle={toggleFolder}
            onFileSelect={(path) => {
              setSelectedFile(path);
              onFileSelect(path);
            }}
            selectedFile={selectedFile}
          />
        ))}
      </div>

      {showCreateFile && (
        <CreateFileDialog
          path={selectedPath}
          onSubmit={handleCreateFile}
          onCancel={() => setShowCreateFile(false)}
        />
      )}
      {showCreateFolder && (
        <CreateFolderDialog
          path={selectedPath}
          onSubmit={handleCreateFolder}
          onCancel={() => setShowCreateFolder(false)}
        />
      )}
    </>
  );
}
