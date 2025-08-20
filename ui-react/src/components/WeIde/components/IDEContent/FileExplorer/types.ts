export interface FileItem {
  name: string;
  type: 'file' | 'folder';
  children?: FileItem[];
  path: string;
}

export interface FileTreeProps {
  items: FileItem[];
  level?: number;
  onFileSelect: (path: string) => void;
}

export interface FileExplorerProps {
  onFileSelect: (path: string) => void;
}