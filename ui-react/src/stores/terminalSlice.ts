import React from 'react'
import { create } from 'zustand';
import weTerminal from '../components/WeIde/components/Terminal/utils/weTerminal';

interface TerminalState {
  isDarkMode: boolean;
  terminals: Map<string | null, weTerminal>;
  newTerminal: (callback?: Function) => void;
  getEndTerminal: () => weTerminal | undefined;
  resetTerminals: () => void;
  addTerminal: (container: React.RefObject<HTMLDivElement>) => Promise<weTerminal>;
  removeTerminal: (processId: string) => void;
  setTheme: (isDark: boolean) => void;
  getTerminal: (index: number) => weTerminal | undefined;
}


const useTerminalStore = create<TerminalState>((set, get) => ({
  isDarkMode: false,
  terminals: new Map(),

  resetTerminals: () => {
    get().terminals.forEach((terminal) => {
      terminal.destroy()
    })

    set({ terminals: new Map() });

    get().newTerminal()
  },

  getEndTerminal: () => {
    const terminals = get().terminals;
    const terminalArray = Array.from(terminals.values());
    return terminalArray[terminalArray.length - 1];
  },

  getTerminal: (index: number) => {
    const terminals = get().terminals;
    const terminalArray = Array.from(terminals.values());
    return terminalArray[index];
  },

  // 暂不支持 从其他地方 非法地注册终端
  // 注册时，必须有明确的ref钩子，防止出现未知错误
  newTerminal: async (cb = () => { }) => {

    const ref = React.createRef<HTMLDivElement>()
    const t = await get().addTerminal(ref)

    cb(t)
  },

  // 添加终端
  // addTerminal: async (container: HTMLElement) => {
  addTerminal: async (containerRef: React.RefObject<HTMLDivElement>) => {

    // 实例化一个新的终端
    const terminal = new weTerminal(null);

    const processId = Math.random().toString(36).substr(2, 9);;
    // 初始化得到 processId
    await terminal.initialize(containerRef.current, processId)

    terminal.setContainerRef(containerRef);

    const newTerminals = new Map(get().terminals); // 获取当前的 terminals
    newTerminals.set(processId, terminal); // 添加新的终端

    set({ terminals: newTerminals }); // 更新状态

    return terminal;
  },

  // 移除终端
  removeTerminal: (processId: string) => {
    const newTerminals = new Map(get().terminals); // 获取当前的 terminals

    const terminal = newTerminals.get(processId) as weTerminal

    terminal?.destroy()
    newTerminals.delete(processId); // 移除指定的终端

    set({ terminals: newTerminals }); // 更新状态
  },

  // 设置主题
  setTheme: (isDark: boolean) => set({ isDarkMode: isDark }),
}));

export default useTerminalStore;