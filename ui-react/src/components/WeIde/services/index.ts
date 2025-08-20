import { WebContainer } from '@webcontainer/api';
import type { NodeContainer } from './nodecontainer/types';
import * as webContainer from './webcontainer';
import * as nodeContainer from './nodecontainer';

const isElectron = !!window.electron;

export type Container = WebContainer | NodeContainer;

// Basic exports
export const {
  useTerminalState,
  syncFileSystem,
  updateFileSystemNow,
  startDevServer,
} = isElectron ? nodeContainer : webContainer;

// Container instance exports
export const getContainerInstance = isElectron 
  ? nodeContainer.getNodeContainerInstance 
  : webContainer.getWebContainerInstance;

// Export types and constants
export type { CommandResult } from './webcontainer/types';
export type { NodeContainer } from './nodecontainer/types';
export { WebContainer } from '@webcontainer/api';
