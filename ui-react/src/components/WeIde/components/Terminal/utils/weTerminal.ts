import {Terminal as XTerm} from '@xterm/xterm';
import {FitAddon} from '@xterm/addon-fit';
import {WebLinksAddon} from '@xterm/addon-web-links';
import {updateFileSystemNow} from '../../../services';
import {getWebContainerInstance} from '../../../services/webcontainer';
import {eventEmitter} from '@/components/AiChat/utils/EventEmitter';

interface CommandResult {
  output: string[];
  exitCode: number;
}
// Light theme in VSCode style
const lightTheme = {
  foreground: '#000000',
  cursor: '#000000',
  background: '#fefefe',
};

// Dark theme in VSCode style
const darkTheme = {
  background: '#18181a',
  foreground: '#ffffff',
};

/**
 * @description
 *
 * This is just a class. For unified terminal management,
 *
 * please refer to: apps\we-dev-client\src\stores\terminalSlice.ts
 *
 * ---
 *
 * @example
 * const terminal = new Terminal();
 * terminal.initialize(React.createRef<HTMLDivElement>().current)
 *
 */
class Terminal {
  private terminal: XTerm | null = null;
  private fitAddon: FitAddon | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private isReady: boolean = false;
  private isDarkMode: boolean = false;
  private initId: string | null = null;
  private processId: string | null = null;
  private initialized: boolean = false; // Add initialization flag
  private containerRef: React.RefObject<HTMLDivElement> | null = null

  constructor(private container: HTMLElement | null, isDarkMode: boolean = true) {
    this.isDarkMode = isDarkMode;
  }

  // Initialize terminal
  public async initialize(container: HTMLElement, processId: string, addError?: (error: any) => void) {
    if (this.initialized || !container) return; // Avoid repeated initialization

    this.initialized = true; // Mark as initialized

    this.processId = processId
    this.container = container;
    const term = new XTerm({
      cursorBlink: true,
      convertEol: true,
      theme: this.isDarkMode ? darkTheme : lightTheme,
      fontSize: 12,
      fontFamily: 'Menlo, Monaco, "Courier New", monospace',
      fontWeight: '500',
      letterSpacing: 0,
      lineHeight: 1.4,
    });

    const fitAddon = new FitAddon();
    const webLinksAddon = new WebLinksAddon();

    term.loadAddon(fitAddon);
    term.loadAddon(webLinksAddon);

    this.terminal = term;
    this.fitAddon = fitAddon;

    term.open(container);
    fitAddon.fit();

    term.onSelectionChange(() => {
      // When text is selected, it can be copied using Ctrl+C
      if (term.hasSelection()) {
        const selection = term.getSelection()
        console.log('Selected text:', selection)
      }
    })

    // Listen for keyboard events
    term.attachCustomKeyEventHandler((event) => {
      // Check if it's a copy operation (Ctrl for Windows/Linux, Command for Mac)
      const isCopyAction = (event.ctrlKey || event.metaKey) && event.key === 'c';
      if (isCopyAction && term.hasSelection()) {
        const selection = term.getSelection();
        navigator.clipboard.writeText(selection);
        event.preventDefault();
        event.stopPropagation();
        return false; // Prevent default behavior
      }

      // Check if it's a paste operation (Ctrl for Windows/Linux, Command for Mac)
      const isPasteAction = (event.ctrlKey || event.metaKey) && event.key === 'v';
      if (isPasteAction) {
        navigator.clipboard.readText().then(text => {
          term.paste(text);
        });
        return false; // Prevent default behavior
      }

      return true; // Allow other keyboard events
    });

    term.writeln('\x1b[1;32mWelcome to Terminal\x1b[0m');
    term.writeln('Type \x1b[1;34mhelp\x1b[0m for a list of commands\n');
    term.write('$ ');

    await this.waitCommand(addError);

    this.isReady = true;


    // Bind resize event
    this.handleResize = this.handleResize.bind(this);
    window.addEventListener('resize', this.handleResize);

    // Listen for container size changes
    this.resizeObserver = new ResizeObserver(() => {
      this.handleResize();
    });
    this.resizeObserver.observe(container);


  }

  // Check if initialized
  public getInitialized(): boolean {
    return this.initialized;
  }

  // Get isReady status
  public getIsReady(): boolean {
    return this.isReady;
  }

  public getContainer(): HTMLElement | null {
    return this.container;
  }

  // Get instanceId
  public getProcessId(): string | null {
    return this.processId;
  }

  public getContainerRef(): React.RefObject<HTMLDivElement> | null {
    return this.containerRef;
  }
  public setContainerRef(ref: React.RefObject<HTMLDivElement>) {
    this.containerRef = ref;
  }


  // Handle window size changes
  private handleResize() {
    if (this.fitAddon) {
      this.fitAddon.fit();
    }
  }

  // Set theme
  public setTheme(isDarkMode: boolean) {
    this.isDarkMode = isDarkMode;
    if (this.terminal) {
      this.terminal.options.theme = isDarkMode ? darkTheme : lightTheme;
    }
  }

  // Wait for command input - now always use WebContainer
  private async waitCommand(addError?: (error: any) => void) {
    await this.webWaitCommand(addError);
  }

  // Command wait in Web environment
  private async webWaitCommand(addError?: (error: any) => void) {
    const instance = await getWebContainerInstance();
    const process = await instance?.spawn('/bin/jsh', [], {
      terminal: {
        cols: 80,
        rows: 15,
      },
    });


    eventEmitter.emit('terminal:update', this.processId);

    const input = process?.input.getWriter();
    const output = process?.output;


    output?.pipeTo(
      new WritableStream({
        write: (data) => {
          if ((data.includes('error') || data.includes('failure')) && addError) {
            addError({
              message: 'compile error',
              code: this.stripAnsi(data),
              severity: 'error',
            });
          }
          if (!this.initId) {
            this.initId = data?.split('/')[1].split('[39m')[0].trim()
          }
          this.terminal.write(data.replaceAll(this.initId, 'alibaba copilot'))

        },
      }),
    );


    this.terminal?.onData((data) => {
      input?.write(data);
    });
  }

  // Execute command - now always use WebContainer
  public async executeCommand(command: string): Promise<CommandResult> {
    return this.executeCommandInWeb(command);
  }

  // Execute command in Web
  private async executeCommandInWeb(command: string): Promise<CommandResult> {
    const instance = await getWebContainerInstance();
    const process = await instance.spawn('jsh', ['-c', command], {
      env: { npm_config_yes: true },
    });

    process.output.pipeTo(
      new WritableStream({
        write: (data) =>{
          this.terminal?.write(data);
        },
      }),
    );

    const exitCode = await process.exit;

    return {
      output: [],
      exitCode,
    };
  }

  // Remove ANSI escape sequences and timestamps
  private stripAnsi(str: string): string {
    str = str.replace(/\u001b\[\d+m/g, '');
    if (/^\d{2}:\d{2}:\d{2}\s/.test(str)) {
      str = str.replace(/^\d{2}:\d{2}:\d{2}\s+/, '');
    }
    return str;
  }

  // Clean up resources
  public destroy() {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver = null;
    }
    if (this.terminal) {
      console.log("Release terminal", this.processId)
      this.terminal.dispose(); // Release XTerm resources
      this.terminal = null;
    }
    if (this.fitAddon) {
      console.log("Release fitAddon", this.processId)
      this.fitAddon.dispose(); // Release FitAddon resources
      this.fitAddon = null;
    }

    window.removeEventListener('resize', this.handleResize);
  }
}


export default Terminal;
