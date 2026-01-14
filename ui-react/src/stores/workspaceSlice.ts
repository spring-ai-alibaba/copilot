import { create } from 'zustand';
import { getWorkspaceFiles } from '@/api/filesystem';
import { useFileStore } from '@/components/WeIde/stores/fileStore';

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
    set({ isLoading: true, error: null });

    try {
      const response = await getWorkspaceFiles(workspacePath);

      if (response.success && response.files) {
        // store in workspace store
        set({ files: response.files, isLoading: false });
        // also populate the IDE file store so FileExplorer / Editor can display them
        try {
          const fileStoreSetFiles = useFileStore.getState().setFiles;
          if (typeof fileStoreSetFiles === 'function') {
            await fileStoreSetFiles(response.files);
            // debug
            try {
              // eslint-disable-next-line no-console
              console.log('Workspace files synced to fileStore:', Object.keys(response.files).length);
            } catch (e) {}
          }
        } catch (e) {
          // swallow errors from file store sync to avoid breaking workspace fetch
          console.warn('Failed to sync files to fileStore', e);
        }
      } else {
        set({ error: response.error || 'Failed to fetch workspace files', isLoading: false });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred';
      set({ error: errorMessage, isLoading: false });
    }
  },

  clearWorkspace: () => set({ files: null, error: null, isLoading: false }),
}));

export default useWorkspaceStore;
