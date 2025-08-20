import { create } from "zustand";
import { syncFileSystem } from "../services";

export interface ErrorMessage {
  message: string;
  code: string;
  number: number;
  severity: "error" | "warning" | "info";
}

interface FileStore {
  files: Record<string, string>;
  setOldFiles: (files: Record<string, string>) => void;
  oldFiles: Record<string, string>;
  errors: ErrorMessage[];
  addError: (error: ErrorMessage) => void;
  removeError: (index: number) => void;
  clearErrors: () => void;
  isFirstSend: Record<string, boolean>;
  isUpdateSend: Record<string, boolean>;
  setIsFirstSend: () => void;
  setEmptyFiles: () => void;
  setIsUpdateSend: () => void;
  addFile: (
    path: string,
    content: string,
    syncFileClose?: boolean,
    number?: number
  ) => Promise<void>;
  getContent: (path: string) => string;
  updateContent: (path: string, content: string, syncFileClose?: boolean, closeUpdate?: boolean) => Promise<void>;
  renameFile: (oldPath: string, newPath: string) => Promise<void>;
  deleteFile: (path: string) => Promise<void>;
  createFolder: (path: string) => Promise<void>;
  getFiles: () => string[];
  setFiles: (files: Record<string, string>) => Promise<void>;
  selectedPath: string;
  projectRoot: string;
  setSelectedPath: (path: string) => void;
  setProjectRoot: (path: string) => void;

}

const initialFiles = {};

export const useFileStore = create<FileStore>((set, get) => ({
  files: initialFiles,
  errors: [],
  oldFiles: initialFiles,
  setOldFiles: async (oldFiles: Record<string, string>) => {
    // 从错误信息来看，需要在 FileStore 接口中添加 oldFiles 属性和 setOldFiles 方法
    set({ oldFiles })
  },
  addError: (error) => {
    if (window.isLoading) {
      return;
    }
    if (get().errors.some((e) => e.code === error.code)) {
      const index = get().errors.findIndex((e) => e.code === error.code);
      get().errors[index].number++;
      return;
    }

    set((state) => ({
      errors: [error, ...state.errors.slice(0, 3)],
    }));
  },
  removeError: (index) =>
    set((state) => ({
      errors: state.errors.filter((_, i) => i !== index),
    })),
  clearErrors: () => set({ errors: [] }),
  isFirstSend: {},
  isUpdateSend: {},

  setIsFirstSend: () => {
    set({ isFirstSend: {} });
  },

  setIsUpdateSend: () => {
    set({ isUpdateSend: {} });
  },

  addFile: async (path, content, syncFileClose?: boolean) => {
    set({
      files: { ...get().files, [path]: content },
      isFirstSend: { ...get().isFirstSend, [path]: true },
    });
    await syncFileSystem(syncFileClose);
  },

  setEmptyFiles: () => {
    window.fileHashMap = new Map<string, string>();
    set({ files: {} });
  },

  getContent: (path) => get().files[path] || "",


  updateContent: async (path, content, syncFileClose?: boolean, closeUpdateChatLog?: boolean) => {
    set({
      files: { ...get().files, [path]: content },
      isUpdateSend: closeUpdateChatLog ? {} : { ...get().isUpdateSend, [path]: !get().isFirstSend[path] },
    });
    await syncFileSystem(syncFileClose);
  },

  renameFile: async (oldPath, newPath) => {
    const files = { ...get().files };
    const content = files[oldPath];
    if (content !== undefined) {
      delete files[oldPath];
      files[newPath] = content;
      set({ files });
      await syncFileSystem();
    }
  },

  deleteFile: async (path) => {
    const files = { ...get().files };
    delete files[path];
    const prefix = path.endsWith("/") ? path : `${path}/`;
    Object.keys(files).forEach((filePath) => {
      if (filePath.startsWith(prefix)) {
        delete files[filePath];
      }
    });
    set({ files });
    await syncFileSystem();
  },

  createFolder: async (path) => {
    const folderPath = path.endsWith("/") ? path : `${path}/`;
    if (Object.keys(get().files).some((file) => file.startsWith(folderPath))) {
      return;
    }
    set({ files: { ...get().files, [`${folderPath}index.tsx`]: "" } });
    await syncFileSystem();
  },

  setFiles: async (files: Record<string, string>) => {
    set({ files });
    await syncFileSystem();
  },

  getFiles: () => Object.keys(get().files),
  selectedPath: '',
  projectRoot: '',
  setSelectedPath: (path: string) => set({ selectedPath: path }),
  setProjectRoot: (path: string) => set({ projectRoot: path }),

}));
