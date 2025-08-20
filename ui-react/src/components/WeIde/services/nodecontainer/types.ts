import { Dirent } from "node:fs";
import { EventEmitter } from 'events';

export interface CommandResult {
  output: string[];
  exitCode: number;
  newPath?: string;
}

export interface NodeContainer extends EventEmitter {
  fs: {
    mkdir: (path: string, options?: { recursive?: boolean }) => Promise<void>;
    writeFile: (path: string, contents: string) => Promise<void>;
    readFile: (path: string, encoding: string) => Promise<string>;
    readdir: (path: string, options?: { withFileTypes?: boolean }) => Promise<string[] | Dirent[]>;
  };
  projectRoot: string;
  spawn: (command: string, args: string[], options?: { cwd?: string }) => Promise<{
    output: ReadableStream;
    exit: Promise<number>;
  }>;
  on(event: 'server-ready', listener: (port: number, url: string) => void): this;
  emit(event: 'server-ready', port: number, url: string): boolean;
}

export interface MountOptions {
  files: Array<{
    path: string;
    contents: string;
  }>;
} 