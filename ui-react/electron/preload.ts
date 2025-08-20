import { contextBridge, ipcRenderer } from "electron";
import { dialog } from "@electron/remote";
import type { OpenDialogOptions } from 'electron';
import type { IpcRendererEvent } from 'electron';
import {MCPServer} from "@/types/mcp";

type ListenerFunction = (...args: unknown[]) => void;
const listeners = new Map<string, ListenerFunction[]>();

contextBridge.exposeInMainWorld("electron", {
  ipcRenderer: {
    invoke: (channel: string, ...args: unknown[]) => {
      return ipcRenderer.invoke(channel, ...args);
    },
    on: (channel: string, func: ListenerFunction) => {
      if (!listeners.has(channel)) {
        listeners.set(channel, []);
      }
      const wrappedFunc = (_: IpcRendererEvent, ...args: unknown[]) => func(...args);
      listeners.get(channel)?.push(wrappedFunc);
      ipcRenderer.on(channel, wrappedFunc);
    },
    removeListener: (channel: string, func: ListenerFunction) => {
      const wrappedFuncs = listeners.get(channel) || [];
      const index = wrappedFuncs.indexOf(func);
      if (index > -1) {
        const wrappedFunc = wrappedFuncs[index];
        ipcRenderer.removeListener(channel, wrappedFunc);
        wrappedFuncs.splice(index, 1);
      }
    },
    send: (channel: string, ...args: unknown[]) => {
      ipcRenderer.send(channel, ...args);
    },
    "terminal:create": (options: TerminalOptions) =>
      ipcRenderer.invoke("terminal:create", options),
    "terminal:write": (processId: string, data: string) =>
      ipcRenderer.invoke("terminal:write", processId, data),
    "terminal:resize": (processId: string, cols: number, rows: number) =>
      ipcRenderer.invoke("terminal:resize", processId, cols, rows),
    "terminal:dispose": (processId: string) =>
      ipcRenderer.invoke("terminal:dispose", processId),
  },
});

// Cleanup function
window.addEventListener("unload", () => {
  listeners.forEach((funcs, channel) => {
    funcs.forEach(func => {
      ipcRenderer.removeListener(channel, func);
    });
  });
  listeners.clear();
});

window.addEventListener("DOMContentLoaded", () => {
  const replaceText = (selector: string, text: string) => {
    const element = document.getElementById(selector);
    if (element) element.innerText = text;
  };

  for (const type of ["chrome", "node", "electron"]) {
    replaceText(`${type}-version`, process.versions[type] as string);
  }
});

// Safely expose API to renderer process
contextBridge.exposeInMainWorld("myAPI", {
  dialog: {
    showOpenDialog: (options: OpenDialogOptions) => dialog.showOpenDialog(options),
  },
  mcp: {
    listServers: () => ipcRenderer.invoke('mcp:list-servers'),
    addServer: (server: MCPServer) => ipcRenderer.invoke('mcp:add-server', server),
    updateServer: (server: MCPServer) => ipcRenderer.invoke('mcp:update-server', server),
    deleteServer: (serverName: string) => ipcRenderer.invoke('mcp:delete-server', serverName),
    setServerActive: (name: string, isActive: boolean) =>
        ipcRenderer.invoke('mcp:set-server-active', { name, isActive }),
    listTools: (serverName?: string) => ipcRenderer.invoke('mcp:list-tools', serverName),
    callTool: (params: { client: string; name: string; args: any }) => ipcRenderer.invoke('mcp:call-tool', params),
    cleanup: () => ipcRenderer.invoke('mcp:cleanup'),
    setServers: (servers: MCPServer[]) => ipcRenderer.send('mcp:servers-from-renderer', servers),
  },
  // Binary related APIs
  isBinaryExist: (name: string) => ipcRenderer.invoke('app:is-binary-exist', name),
  getBinaryPath: (name: string) => ipcRenderer.invoke('app:get-binary-path', name),
  installUVBinary: () => ipcRenderer.invoke('app:install-uv-binary'),
  installBunBinary: () => ipcRenderer.invoke('app:install-bun-binary'),
});
