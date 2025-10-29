/**
 * SSE事件处理器 - 文件系统相关事件
 * 处理从服务器发送的文件系统事件
 */

export interface FileSystemEvent {
  type: 'fileSystem';
  data: {
    workspacePath: string;
    files: Record<string, string>;
    fileCount: number;
    message?: string;
  };
}

/**
 * 处理文件系统事件
 * @param event 文件系统事件
 * @param updateContent 更新文件内容的回调函数
 */
export function handleFileSystemEvent(
  event: FileSystemEvent,
  updateContent: (filePath: string, content: string) => void
): void {
  console.log('Received file system event:', event);

  const { workspacePath, files, fileCount, message } = event.data;

  if (files && Object.keys(files).length > 0) {
    // 将服务器文件同步到前端文件存储
    for (const [filePath, fileContent] of Object.entries(files)) {
      updateContent(filePath, fileContent);
    }

    console.log(`Synchronized ${fileCount} files from server file system`);

    if (message) {
      console.log('File system message:', message);
    }
  }
}

/**
 * 检查事件是否为文件系统事件
 */
export function isFileSystemEvent(event: any): event is FileSystemEvent {
  return event && event.type === 'fileSystem' && event.data;
}