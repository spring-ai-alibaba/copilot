import { create } from 'zustand';
import { getNodeContainerInstance } from './instance';

interface TerminalState {
  currentPath: string;
  setCurrentPath: (path: string) => void;
  initialize: () => Promise<void>;
}

export const useTerminalState = create<TerminalState>((set, get) => ({
  currentPath: '',
  setCurrentPath: (path) => set({ currentPath: path }),
  initialize: async () => {
    try {
      const instance = await getNodeContainerInstance();
      if (instance) {
        const projectRoot = instance.projectRoot;
        if (!projectRoot) {
          throw new Error('Project root not available');
        }
        set({ currentPath: projectRoot });
      }
    } catch (error) {
      console.error('Failed to initialize terminal state:', error);
    }
  }
}));

useTerminalState.getState().initialize().catch(error => {
  console.error('Failed to initialize terminal state:', error);
}); 