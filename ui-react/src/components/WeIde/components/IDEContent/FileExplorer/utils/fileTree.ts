import { FileItem } from '../types';

export function buildFileTree(paths: string[]): FileItem[] {
  const root: FileItem[] = [];
  const map = new Map<string, FileItem>();

  // Sort paths to ensure consistent order
  const sortedPaths = [...paths].sort();

  sortedPaths.forEach(path => {
    const parts = path.split('/');
    let currentPath = '';

    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      if (!map.has(currentPath)) {
        const node: FileItem = {
          name: part,
          type: isFile ? 'file' : 'folder',
          path: currentPath,
          children: isFile ? undefined : []
        };
        map.set(currentPath, node);

        if (index === 0) {
          root.push(node);
        } else {
          const parentPath = parts.slice(0, index).join('/');
          const parent = map.get(parentPath);
          if (parent?.children) {
            // Only add if not already present
            if (!parent.children.some(child => child.path === node.path)) {
              parent.children.push(node);
            }
          }
        }
      }
    });
  });

  // Sort nodes (folders first, then alphabetically)
  const sortNodes = (nodes: FileItem[]) => {
    nodes.sort((a, b) => {
      if (a.type === b.type) {
        return a.name.localeCompare(b.name);
      }
      return a.type === 'folder' ? -1 : 1;
    });

    nodes.forEach(node => {
      if (node.children) {
        sortNodes(node.children);
      }
    });
  };

  sortNodes(root);
  return root;
}