import { create } from 'zustand';

interface TerminalState {
  currentPath: string;
  setCurrentPath: (path: string) => void;
}

export const useTerminalState = create<TerminalState>((set) => ({
  currentPath: '/',
  setCurrentPath: (path) => set({ currentPath: path }),
}));