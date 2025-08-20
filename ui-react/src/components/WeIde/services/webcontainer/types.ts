export interface CommandResult {
  output: string[];
  exitCode: number;
  newPath?: string;
}

export interface WebContainerState {
  instance: any | null;
  isBooting: boolean;
  error: Error | null;
}

export interface MountOptions {
  files: Array<{
    path: string;
    contents: string;
  }>;
}