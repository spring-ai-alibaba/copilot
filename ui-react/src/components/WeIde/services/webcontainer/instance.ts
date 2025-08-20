import { WebContainer } from '@webcontainer/api';
import { useFileStore } from '../../stores/fileStore';

let webcontainerInstance: WebContainer | null = null;
let bootPromise: Promise<WebContainer> | null = null;

export async function getWebContainerInstance(): Promise<WebContainer | null> {
  if (webcontainerInstance) return webcontainerInstance;
  if (bootPromise) return bootPromise;

  try {
    bootPromise = WebContainer.boot();
    webcontainerInstance = await bootPromise;
    
    if (webcontainerInstance) {
      // Initialize the root directory
      await webcontainerInstance.fs.mkdir('/', { recursive: true });
      
      // Mount initial files
      const { files } = useFileStore.getState();
      for (const [path, contents] of Object.entries(files)) {
        const fullPath = `//${path}`;
        // Create parent directories
        const parentDir = fullPath.substring(0, fullPath.lastIndexOf('/'));
        await webcontainerInstance.fs.mkdir(parentDir, { recursive: true });
        // Write file
        await webcontainerInstance.fs.writeFile(fullPath, contents);
      }
    }

    return webcontainerInstance;
  } catch (error) {
    console.error('Failed to boot WebContainer:', error);
    webcontainerInstance = null;
    return null;
  } finally {
    bootPromise = null;
  }
}