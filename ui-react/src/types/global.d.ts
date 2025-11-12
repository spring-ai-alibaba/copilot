import {PostHog} from "posthog-js";
import {HookAPI} from "antd/es/modal/useModal";

declare global {
  interface Window {
    isLoading: boolean;
    getCurrentDir: () => string;
    Posthog: PostHog
    fileHashMap: Map<string, string>;
    modal: HookAPI
  }
}

// 扩展 HTMLInputElement 以支持 webkitdirectory 属性
declare module 'react' {
  interface HTMLAttributes<T> {
    webkitdirectory?: string;
  }
}

export {};

