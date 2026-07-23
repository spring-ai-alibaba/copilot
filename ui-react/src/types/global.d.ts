import {PostHog} from "posthog-js";
import {HookAPI} from "antd/es/modal/useModal";

interface ElectronIpcRenderer {
  on: (channel: string, listener: (event: unknown, ...args: any[]) => void) => unknown;
  removeListener: (channel: string, listener: (...args: any[]) => void) => unknown;
  send: (channel: string, ...args: any[]) => void;
  invoke: (channel: string, ...args: any[]) => Promise<any>;
}

declare global {
  interface Window {
    isLoading: boolean;
    getCurrentDir: () => string;
    Posthog: PostHog
    fileHashMap: Map<string, string>;
    modal: HookAPI
    electron: {
      ipcRenderer: ElectronIpcRenderer;
    };
  }
}

// 扩展 HTMLInputElement 以支持 webkitdirectory 属性
declare module 'react' {
  interface HTMLAttributes<T> {
    webkitdirectory?: string;
  }
}

export {};

