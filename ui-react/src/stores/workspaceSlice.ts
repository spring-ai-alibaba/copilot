import { create } from 'zustand';
import { getWorkspaceFiles } from '@/api/filesystem';

export interface WorkspaceFile {
  [filePath: string]: string;
}

interface WorkspaceState {
  files: WorkspaceFile | null;
  isLoading: boolean;
  error: string | null;
  setFiles: (files: WorkspaceFile) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  fetchWorkspaceFiles: (workspacePath: string) => Promise<void>;
  clearWorkspace: () => void;
}

const useWorkspaceStore = create<WorkspaceState>((set, get) => ({
  files: null,
  isLoading: false,
  error: null,

  setFiles: (files) => set({ files }),

  setLoading: (loading) => set({ isLoading: loading }),

  setError: (error) => set({ error }),

  fetchWorkspaceFiles: async (workspacePath: string) => {
    console.log('Calling getWorkspaceFiles API with path:', workspacePath);
    set({ isLoading: true, error: null });

    try {
      const response = await getWorkspaceFiles(workspacePath);
      console.log('getWorkspaceFiles response:', response);

      if (response.success && response.files) {
        console.log('Successfully fetched workspace files:', Object.keys(response.files));
        set({ files: response.files, isLoading: false });
      } else {
        console.log('Failed to fetch workspace files:', response.error);
        set({ error: response.error || 'Failed to fetch workspace files', isLoading: false });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred';
      console.log('Error calling getWorkspaceFiles:', errorMessage);
      set({ error: errorMessage, isLoading: false });
    }
  },

  clearWorkspace: () => set({ files: null, error: null, isLoading: false }),
}));

export default useWorkspaceStore;
