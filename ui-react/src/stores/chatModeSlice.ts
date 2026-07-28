import {create} from 'zustand';

export enum ChatMode {
  Chat = 'chat',
  Builder = 'builder'
}

export enum ExecutionMode {
  Execute = 'execute',
  Plan = 'plan'
}

interface ChatModeState {
  mode: ChatMode;
  executionMode: ExecutionMode;
  initOpen: boolean;
  setInitOpen: (initOpen: boolean) => void;
  setMode: (mode: ChatMode) => void;
  setExecutionMode: (executionMode: ExecutionMode) => void;
}

const useChatModeStore = create<ChatModeState>((set) => ({
  mode: ChatMode.Builder,
  executionMode: ExecutionMode.Execute,
  initOpen: false,
  setInitOpen: (initOpen) => set({ initOpen }),
  setMode: (mode) =>
    set((state) => ({
      mode,
      executionMode:
        mode === ChatMode.Chat ? ExecutionMode.Execute : state.executionMode,
    })),
  setExecutionMode: (executionMode) => set({ executionMode }),
}));

export default useChatModeStore;
