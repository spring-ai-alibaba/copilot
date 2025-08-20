const ipcRenderer = window.electron?.ipcRenderer;
import { getNodeContainerInstance } from './instance';

export async function startDevServer(): Promise<{ output: ReadableStream<any>; exit: Promise<number>; }> {
  const nodeContainer = await getNodeContainerInstance();
  if (!nodeContainer) {
    throw new Error('NodeContainer not available');
  }

  try {
    console.log('Installing dependencies...');
    const installProcess = await nodeContainer.spawn('npm', ['install'], {
      cwd: nodeContainer.projectRoot
    });

    const installExitCode = await installProcess.exit;
    if (installExitCode !== 0) {
      throw new Error('npm install failed');
    }

    console.log('Starting dev server...');
     return await nodeContainer.spawn('npm', ['run', 'dev'], {
      cwd: nodeContainer.projectRoot
    });

  } catch (error) {
    console.error('Failed to start dev server:', error);
    throw error;
  }
}

// 停止开发服务器
export async function stopDevServer(port: number): Promise<void> {
  try {
    // @ts-ignore
    await ipcRenderer.invoke('node-container:stop-server', port);
  } catch (error) {
    console.error('Failed to stop dev server:', error);
    throw error;
  }
} 