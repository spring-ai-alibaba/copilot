import {WebContainer} from '@webcontainer/api';
import * as webContainer from './webcontainer';

export type Container = WebContainer;

// Basic exports - now always use webContainer
export const {
  useTerminalState,
  syncFileSystem,
  updateFileSystemNow,
  startDevServer,
} = webContainer;

// Container instance exports
export const getContainerInstance = webContainer.getWebContainerInstance;

// Export types and constants
export type { CommandResult } from './webcontainer/types';
export { WebContainer } from '@webcontainer/api';
