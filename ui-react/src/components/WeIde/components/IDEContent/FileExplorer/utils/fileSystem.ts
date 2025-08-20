import { useFileStore } from "@/components/WeIde/stores/fileStore";
import { getDefaultContent } from "@/utils/defaultContent";

// Path utilities with improved handling
const path = {
  join: (...parts: string[]): string => {
    return parts.filter(Boolean).join('/').replace(/\/+/g, '/');
  },
  dirname: (path: string): string => {
    if (!path.includes('/')) return '';
    return path.split('/').slice(0, -1).join('/');
  },
  basename: (path: string): string => {
    return path.split('/').pop() || '';
  },
  isRoot: (path: string): boolean => {
    return !path.includes('/');
  }
};

export function isValidFileName(name: string): boolean {
  return (
    name.length > 0 && 
    !/[<>:"\/\\|?*\x00-\x1F]/.test(name) && 
    !name.startsWith('.') &&
    !name.endsWith('.')
  );
}


export async function createFile(basePath: string, fileName: string): Promise<string> {
  const { addFile } = useFileStore.getState();
  const fullPath = path.join(basePath, fileName);
  const content = getDefaultContent(fileName);
  await addFile(fullPath, content);
  return fullPath;
}
// 新增文件
export async function createFileWithContent(basePath: string, content: string, syncFileClose?: boolean): Promise<string> {
  const { addFile } = useFileStore.getState();
  await addFile(basePath, content, syncFileClose); // src/ 
  return basePath;
}

export function createFolder(basePath: string, folderName: string): string {
  const { createFolder } = useFileStore.getState();
  const fullPath = path.join(basePath, folderName);
  createFolder(fullPath);
  return fullPath;
}

export function renameFile(oldPath: string, newName: string): string {
  const { renameFile } = useFileStore.getState();
  // For root-level files, just use the new name directly
  const newPath = path.isRoot(oldPath) ? newName : path.join(path.dirname(oldPath), newName);
  renameFile(oldPath, newPath);
  return newPath;
}

export function deleteFile(path: string): void {
  const { deleteFile } = useFileStore.getState();
  deleteFile(path);
}