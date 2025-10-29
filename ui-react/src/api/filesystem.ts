/**
 * 文件系统API接口
 * 用于与后端的文件系统功能集成
 */

export interface FileSystemResponse {
  success: boolean;
  files?: Record<string, string>;
  content?: string;
  error?: string;
  fileCount?: number;
  message?: string;
}

export interface WorkspaceInfo {
  path: string;
  totalFiles: number;
  totalSize: number;
  createdAt: string;
}

/**
 * 获取工作空间中的文件列表
 */
export const getWorkspaceFiles = async (workspacePath: string): Promise<FileSystemResponse> => {
  try {
    // 将路径中的/替换为|以避免URL编码问题
    const encodedPath = workspacePath.replace(/\//g, '|');
    const response = await fetch(`/api/files/workspace/${encodedPath}`);
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Failed to get workspace files:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
};

/**
 * 读取工作空间中的特定文件
 */
export const readWorkspaceFile = async (workspacePath: string, filePath: string): Promise<FileSystemResponse> => {
  try {
    // 将路径中的/替换为|以避免URL编码问题
    const encodedWorkspacePath = workspacePath.replace(/\//g, '|');
    const encodedFilePath = filePath.replace(/\//g, '|');
    const response = await fetch(`/api/files/workspace/${encodedWorkspacePath}/file/${encodedFilePath}`);
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Failed to read workspace file:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
};

/**
 * 保存文件到工作空间
 */
export const saveWorkspaceFile = async (workspacePath: string, filePath: string, content: string): Promise<FileSystemResponse> => {
  try {
    // 将路径中的/替换为|以避免URL编码问题
    const encodedWorkspacePath = workspacePath.replace(/\//g, '|');
    const encodedFilePath = filePath.replace(/\//g, '|');
    const response = await fetch(`/api/files/workspace/${encodedWorkspacePath}/file/${encodedFilePath}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content }),
    });
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Failed to save workspace file:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
};

/**
 * 批量保存文件到工作空间
 */
export const saveWorkspaceFiles = async (workspacePath: string, files: Record<string, string>): Promise<FileSystemResponse> => {
  try {
    // 将路径中的/替换为|以避免URL编码问题
    const encodedWorkspacePath = workspacePath.replace(/\//g, '|');
    const response = await fetch(`/api/files/workspace/${encodedWorkspacePath}/files`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(files),
    });
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Failed to save workspace files:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
};

/**
 * 获取工作空间信息
 */
export const getWorkspaceInfo = async (workspacePath: string): Promise<FileSystemResponse> => {
  try {
    // 将路径中的/替换为|以避免URL编码问题
    const encodedPath = workspacePath.replace(/\//g, '|');
    const response = await fetch(`/api/files/workspace/${encodedPath}/info`);
    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Failed to get workspace info:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
};

/**
 * 检查文件系统功能是否启用
 */
export const isFileSystemEnabled = async (): Promise<boolean> => {
  try {
    // 尝试调用一个简单的文件系统API来检查功能是否可用
    const response = await fetch('/api/files/config');
    if (response.ok) {
      const data = await response.json();
      return data.enabled === true;
    }
    return false;
  } catch (error) {
    console.log('File system not enabled or not available');
    return false;
  }
};