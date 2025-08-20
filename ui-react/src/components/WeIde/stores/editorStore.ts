import { create } from 'zustand';

interface EditorState {
  currentFile: string;
  setCurrentFile: (path: string) => void;
  isDirty: Record<string, boolean>;
  setDirty: (path: string, isDirty: boolean) => void;
  clearDirty: (path: string) => void;
}

export const useEditorStore = create<EditorState>((set) => ({
  currentFile: '',
  setCurrentFile: (path: string) => set({ currentFile: path }),
  isDirty: {},
  setDirty: (path, isDirty) => 
    set((state) => ({
      isDirty: { ...state.isDirty, [path]: isDirty }
    })),
  clearDirty: (path) =>
    set((state) => {
      const newDirty = { ...state.isDirty };
      delete newDirty[path];
      return { isDirty: newDirty };
    })
}));