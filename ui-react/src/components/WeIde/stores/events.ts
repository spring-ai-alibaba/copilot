export type FileEvent = {
  type: "add" | "update" | "rename" | "delete" | "createFolder" | "setFiles";
  path?: string;
  oldPath?: string;
  newPath?: string;
  content?: string;
  files?: Record<string, string>;
  syncFileClose?: boolean;
};

type FileEventListener = (event: FileEvent) => Promise<void> | void;

class FileEventEmitter {
  private listeners: FileEventListener[] = [];

  subscribe(listener: FileEventListener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  async emit(event: FileEvent) {
    await Promise.all(this.listeners.map((listener) => listener(event)));
  }
}

export const fileEventEmitter = new FileEventEmitter();
