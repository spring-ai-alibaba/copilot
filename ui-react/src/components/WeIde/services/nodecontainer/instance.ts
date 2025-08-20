import { EventEmitter } from '@/components/AiChat/utils/EventEmitter';
import { useFileStore } from '../../stores/fileStore';
import type { NodeContainer } from './types';


const ipcRenderer = window.electron?.ipcRenderer as any;

let nodeContainerInstance: NodeContainer | null = null;
let bootPromise: Promise<NodeContainer> | null = null;

// 获取项目根目录
export async function getProjectRoot() {
  if (!ipcRenderer) {
    throw new Error('Electron IPC is not available');
  }
  // 从主进程获取应用路径
  // @ts-ignore
  return await ipcRenderer.invoke('node-container:get-project-root');
}

async function initNodeContainer(): Promise<NodeContainer> {
  if (!ipcRenderer) {
    throw new Error('Electron IPC is not available. Are you running in development mode?');
  }

  try {
    // @ts-ignore
    await ipcRenderer.invoke('node-container:init');
    const projectRoot = await getProjectRoot();

    const instance = new EventEmitter() as any;

    instance.fs = {
      mkdir: async (path: string, options?: { recursive?: boolean }) => {
        return ipcRenderer.invoke('node-container:mkdir', path, options);
      },
      writeFile: async (path: string, contents: string) => {
        return ipcRenderer.invoke('node-container:writeFile', path, contents);
      },
      readFile: async (path: string, encoding: string) => {
        return ipcRenderer.invoke('node-container:readFile', path, encoding);
      },
      readdir: async (path: string, options?: { withFileTypes?: boolean }) => {
        return ipcRenderer.invoke('node-container:readdir', path, options);
      }
    };

    instance.spawn = async (command: string, args: string[], options?: { cwd?: string }) => {
      try {
        console.log('Renderer Process: Spawning command:', command, args, options);
        const { processId } = await ipcRenderer.invoke('node-container:spawn', command, args, {
          cwd: options?.cwd || projectRoot
        });
        console.log('Renderer Process: Got process ID:', processId);

        const stream = new ReadableStream({
          start(controller) {
            console.log('Renderer Process: Setting up stream listeners');
            const outputListener = (sdata: any, data: string) => {
              console.log('Renderer Process: Received output:', sdata);
              //   controller.enqueue(new TextEncoder().encode(data));
              controller.enqueue(new TextEncoder().encode(sdata));
            };

            const exitListener = (_: any, code: number) => {
              console.log('Renderer Process: Process exited with code:', code);
              ipcRenderer.removeListener(`process-output-${processId}`, outputListener);
              ipcRenderer.removeListener(`process-exit-${processId}`, exitListener);
              controller.close();
            };

            console.log('Renderer Process: Adding event listeners for:', processId);
            ipcRenderer.on(`process-output-${processId}`, outputListener);
            ipcRenderer.on(`process-exit-${processId}`, exitListener);
          }
        });

        const exit = new Promise<number>((resolve) => {
          const exitListener = (_: any, code: number) => {
            console.log('Renderer Process: Exit promise resolved with code:', code);
            ipcRenderer.removeListener(`process-exit-${processId}`, exitListener);
            resolve(code);
          };
          ipcRenderer.on(`process-exit-${processId}`, exitListener);
        });

        return {
          output: stream,
          exit
        };
      } catch (error) {
        console.error('Renderer Process: Spawn error:', error);
        throw error;
      }
    };

    // 保存项目根路径到实例中
    instance.projectRoot = projectRoot;

    return instance;
  } catch (error) {
    console.error('Failed to initialize NodeContainer:', error);
    throw error;
  }
}

export async function getNodeContainerInstance(): Promise<NodeContainer | null> {
  if (nodeContainerInstance) return nodeContainerInstance;
  if (bootPromise) return bootPromise;

  try {
    if (!window.electron) {
      console.warn('Electron environment not detected. Some features may not work.');
      return null;
    }

    bootPromise = initNodeContainer();
    nodeContainerInstance = await bootPromise;

    if (nodeContainerInstance) {
      const projectRoot = nodeContainerInstance.projectRoot;

      // 初始化项目根目录
      await nodeContainerInstance.fs.mkdir(projectRoot, { recursive: true });

      // 挂载初始文件
      const { files } = useFileStore.getState();
      for (const [path, contents] of Object.entries(files)) {
        const fullPath = `${projectRoot}/${path}`;
        // 创建父目录
        const parentDir = fullPath.substring(0, fullPath.lastIndexOf('/'));
        await nodeContainerInstance.fs.mkdir(parentDir, { recursive: true });
        // 写入文件
        await nodeContainerInstance.fs.writeFile(fullPath, contents);
      }
    }

    return nodeContainerInstance;
  } catch (error) {
    console.error('Failed to init NodeContainer:', error);
    nodeContainerInstance = null;
    return null;
  } finally {
    bootPromise = null;
  }
} 