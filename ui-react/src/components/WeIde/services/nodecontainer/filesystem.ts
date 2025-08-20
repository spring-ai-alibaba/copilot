const ipcRenderer = window?.electron?.ipcRenderer;
import { useFileStore } from '../../stores/fileStore';
import { add, debounce } from 'lodash';

import {isHiddenNodeModules} from "../../../../../config/electronOrSrcCommonConfig"

// Store file hash values
window.fileHashMap = new Map<string, string>();

// Calculate MD5 hash of file content
async function calculateMD5(content: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(content);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

let first = true;
// Move recursive function outside
async function readDirRecursive(
  dirPath: string,
  filesObj: Record<string, string>,
  projectRoot: string
): Promise<{ path: string; content: string }[]> {
  const files: { path: string; content: string }[] = [];
  const entries = await ipcRenderer.invoke('node-container:readdir', dirPath);

  for (const entry of entries) {
    const fullPath = dirPath + (dirPath.endsWith('/') ? '' : '/') + entry;

    if (isHiddenNodeModules.some(item => entry.indexOf(item) > -1)) continue;

    try {
      const stats = await ipcRenderer.invoke('node-container:stat', fullPath);

      if (stats && stats.isDirectory) {
        const subFiles = await readDirRecursive(fullPath, filesObj, projectRoot);
        files.push(...subFiles);
      } else {
        const content = await ipcRenderer.invoke('node-container:readFile', fullPath, 'utf-8');
        // Calculate new MD5 and compare
        const newHash = await calculateMD5(content || '&empty');
        const oldHash = window.fileHashMap.get(fullPath);
        const fileHash = await calculateMD5(filesObj[fullPath.replace((projectRoot.startsWith('/') ? projectRoot.substring(1) : projectRoot)  + '/', '').substring(1)] || '&empty');

        if ((oldHash !== newHash && fileHash !== newHash)) {
          files.push({
            path: fullPath.startsWith('/') ? fullPath.slice(1) : fullPath,
            content
          });
          window.fileHashMap.set(fullPath, newHash);
        }
      }
    } catch (error) {
      console.warn(`Failed to process ${fullPath}:`, error);
    }
  }
  return files;
}

// Create debounced version of updateFileSystemNow
const debouncedUpdateFileSystem = debounce(async () => {
  if(window.isLoading) return;
  const { updateContent, addFile, files: filesObj } = useFileStore.getState();

  try {
    const projectRoot = await ipcRenderer.invoke('node-container:get-project-root');

    const files = await readDirRecursive(projectRoot, filesObj, projectRoot);

    // Update file storage
    if (files.length > 0) {
      for (const file of files) {
        if (first) {
          addFile(file.path.replace((projectRoot.startsWith('/') ? projectRoot.substring(1) : projectRoot) + '/', ''), file.content, true);
        } else {
          updateContent(file.path.replace((projectRoot.startsWith('/') ? projectRoot.substring(1) : projectRoot) + '/', ''), file.content, true);
        }
      }
      first = false;
    }
  } catch (error) {
    console.error('Failed to update files:', error);
  }
}, 500);

// Export debounced version
export const updateFileSystemNow = debouncedUpdateFileSystem;

// Create debounced version of syncFileSystem
const debouncedSyncFileSystem = debounce(async (close: boolean = false): Promise<boolean> => {
  try {
    const { files } = useFileStore.getState();
    await ipcRenderer.invoke('node-container:sync-filesystem', files);
    if (!close && !window.isLoading) {
      updateFileSystemNow();
    }
    return true;
  } catch (error) {
    console.error('Failed to sync file system:', error);
    return false;
  }
}, 500);

// Export debounced version
export const syncFileSystem = debouncedSyncFileSystem;
