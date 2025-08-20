import { getWebContainerInstance } from './instance';

export async function startDevServer() {
  const webcontainer = await getWebContainerInstance();
  
  const installProcess = await webcontainer.spawn('npm', ['install']);
  const installExitCode = await installProcess.exit;
  
  if (installExitCode !== 0) {
    throw new Error('Installation failed');
  }

  return await webcontainer.spawn('npm', ['run', 'dev']);
}