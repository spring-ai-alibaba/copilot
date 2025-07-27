/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module '@webcontainer/api' {
  export interface WebContainer {
    boot(): Promise<WebContainer>
    mount(tree: any, options?: any): Promise<void>
    spawn(command: string, args?: string[], options?: any): Promise<any>
    fs: {
      readFile(path: string, encoding?: string): Promise<string>
      writeFile(path: string, content: string): Promise<void>
      mkdir(path: string, options?: { recursive?: boolean }): Promise<void>
      readdir(path: string, options?: { withFileTypes?: boolean }): Promise<any[]>
      rm(path: string, options?: { recursive?: boolean }): Promise<void>
    }
    on(event: string, listener: (...args: any[]) => void): void
    off(event: string, listener: (...args: any[]) => void): void
  }
  
  export const WebContainer: {
    boot(): Promise<WebContainer>
  }
}

declare module '@xterm/xterm' {
  export class Terminal {
    constructor(options?: any)
    open(element: HTMLElement): void
    write(data: string): void
    writeln(data: string): void
    clear(): void
    dispose(): void
    onData(listener: (data: string) => void): void
    onResize(listener: (size: { cols: number; rows: number }) => void): void
    loadAddon(addon: any): void
    cols: number
    rows: number
  }
}

declare module '@xterm/addon-fit' {
  export class FitAddon {
    fit(): void
  }
}

declare module '@xterm/addon-web-links' {
  export class WebLinksAddon {}
}

declare module '@codemirror/basic-setup' {
  export const basicSetup: any
}

declare module 'codemirror' {
  export { EditorView } from '@codemirror/view'
  export { EditorState } from '@codemirror/state'
}
