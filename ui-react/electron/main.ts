import {app, BrowserWindow, ipcMain, IpcMainInvokeEvent, nativeTheme} from "electron";
import path from "path";
import {spawn} from "child_process";
import {enable, initialize} from "@electron/remote/main";
import fs from "fs";
import {startLoginServer} from "./loginServer";
import {isHiddenNodeModules} from "../config/electronOrSrcCommonConfig"

import {MCPServer} from "@/types/mcp";
import {getBinaryPath, isBinaryExists, runInstallScript} from "../src/utils/process";
import MCPServiceManager from "./MCPServer";
import {proxyManager} from '../src/utils/proxyManager'

// Change log path to electron directory

const logPath = path.join(app.getAppPath(), "../../logs");
const logFile = path.join(
    logPath,
    `app-${new Date().toISOString().split("T")[0]}.log`
);

// Ensure log directory exists
try {
    if (!fs.existsSync(logPath)) {
        fs.mkdirSync(logPath, {recursive: true});
    }
} catch (error) {
    console.error("Failed to create log directory:", error);
}

// Create log function
function logToFile(message: string) {
    const timestamp = new Date().toISOString();
    const logMessage = `${timestamp} - ${message}\n`;
    try {
        fs.appendFileSync(logFile, logMessage);
        console.log(logMessage); // Also output to console
    } catch (error) {
        console.error("Failed to write log:", error);
    }
}

// Record startup information

logToFile(JSON.stringify(process.env, null, 2));
logToFile("Application starting...");
logToFile(`Log file location: ${logFile}`);
logToFile(`Process arguments: ${process.argv.join(" ")}`);
logToFile(`Electron version: ${process.versions.electron}`);
logToFile(`Chrome version: ${process.versions.chrome}`);
logToFile(`Node version: ${process.versions.node}`);
logToFile(app.getAppPath());
logToFile(__dirname);
logToFile(app.getPath("exe"));
// Catch unhandled exceptions
process.on("uncaughtException", (error) => {
    logToFile(`Uncaught Exception: ${error.stack || error.message}`);
});

process.on("unhandledRejection", (error) => {
    logToFile(`Unhandled Rejection: ${error}`);
});

// Add log before app ready
app.on("will-finish-launching", () => {
    logToFile("App will finish launching");
});

let pty: {
    spawn: (shell: string, args: string[], options: {
        name: string;
        cols: number;
        rows: number;
        cwd: string;
        env: NodeJS.ProcessEnv;
    }) => PtyProcess;
};
;
try {
    const ptyNodePath = path.join(__dirname, "../../../node_modules/node-pty");
    pty = require(ptyNodePath);
    console.log(
        "Successfully loaded node-pty from:",
        path.join(__dirname, "../../../node_modules/node-pty")
    );
} catch (error) {
    try {
        const ptyNodePath = path.join(__dirname, "./node_modules/node-pty");
        pty = require(ptyNodePath);
        console.log(
            "Successfully loaded node-pty from:",
            path.join(__dirname, "./node_modules/node-pty")
        );
    } catch (secondError) {
        try {
            const ptyNodePath = path.join(
                app.getAppPath(),
                "../../app.asar.unpacked/node_modules/node-pty"
            );
            pty = require(ptyNodePath);
            console.log(
                "Successfully loaded node-pty from:",
                path.join(
                    app.getAppPath(),
                    "../../app.asar.unpacked/node_modules/node-pty"
                )
            );
        } catch (error) {
            try {
                pty = require("node-pty");
                console.log("Successfully loaded node-pty from:", "node-pty");
            } catch (secondError) {
                console.error("Failed to load node-pty directly:", secondError);
                pty = {
                    spawn: () => {
                        throw new Error("node-pty not available");
                    },
                };
            }
        }
    }
}

let nowPath = "";
// Store active processes
const activeProcesses = new Map();

// Store pty instances
const ptyProcesses = new Map<string, PtyProcess>();

// Initialize remote module
initialize();
const mcpService = new MCPServiceManager();

export let mainWindow: BrowserWindow | null = null;

function createWindow() {
    logToFile("Starting to create window");

    try {
        mainWindow = new BrowserWindow({
            width: 1200,
            height: 800,
            webPreferences: {
                nodeIntegration: true,
                contextIsolation: true,
                preload: process.env.VITE_DEV_SERVER_URL
                    ? path.join(__dirname, "preload.js") // Development environment
                    : path.join(app.getAppPath(), "dist-electron", "preload.js"), // Production environment
            },
        });

        // Start login server and pass main window instance
        const loginServer = startLoginServer(mainWindow);

        // Add IPC listener for opening external URLs
        ipcMain.on("open:external:url", (_, url) => {
            const {shell} = require("electron");
            shell.openExternal(url);
        });

        // Clean up resources when window is closed
        mainWindow.on("closed", () => {
            mainWindow = null;
            if (loginServer) {
                loginServer.close();
            }
        });

        // Remove menu bar
        // Menu.setApplicationMenu(null);
        // Use dark theme
        nativeTheme.themeSource = "dark";

        // Listen for window load error
        mainWindow.webContents.on(
            "did-fail-load",
            (event, errorCode, errorDescription) => {
                logToFile(`Window failed to load: ${errorDescription} (${errorCode})`);
            }
        );

        // Add window error listener
        mainWindow.on("unresponsive", () => {
            logToFile("Window became unresponsive");
        });

        mainWindow.on("responsive", () => {
            logToFile("Window became responsive");
        });

        // Check for version updates


        // Load local server in development environment
        if (process.env.VITE_DEV_SERVER_URL) {
            logToFile(`Loading dev server URL: ${process.env.VITE_DEV_SERVER_URL}`);
            mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
            mainWindow.webContents.openDevTools();
        } else {
            const htmlPath = path.join(__dirname, "../dist/index.html");
            logToFile(`Loading production HTML file: ${htmlPath}`);
            mainWindow.loadFile(htmlPath);

            const APP_BASE_URL = "https://we0.ai";

            fetch(`${APP_BASE_URL}/wedev`, {
                method: "GET"
            }).then(async (res) => {
                try {
                    // Get remote HTML content
                    const remoteHtml = await res.text();

                    // Get local HTML content
                    const localHtmlPath = process.env.VITE_DEV_SERVER_URL
                        ? path.join(__dirname, "../dist/index.html")
                        : path.join(app.getAppPath(), "dist/index.html");
                    const localHtml = fs.readFileSync(localHtmlPath, 'utf-8');

                    // Compare HTML contents
                    if (remoteHtml !== localHtml) {
                        console.log('New version detected');
                        mainWindow.loadURL(`${APP_BASE_URL}/wedev`);
                    }
                } catch (error) {
                    logToFile(`Version check error: ${error}`);
                }
            }).catch(error => {
                console.log(error);
            });
        }

        // Enable remote module for this window
        enable(mainWindow.webContents);

        // Check if file exists
        ipcMain.handle(
            "node-container:check-file-exists",
            async (_event, path: string) => {
                try {
                    const fs = require("fs/promises");
                    await fs.access(path);
                    return true;
                } catch {
                    throw new Error("File does not exist");
                }
            }
        );
        // Register required IPC handlers
        ipcMain.handle("node-container:init", async () => {
            // Return initialization success
            return true;
        });

        ipcMain.handle("node-container:mkdir", async (_, dirPath, options) => {
            // Handle mkdir request
            const fs = require("fs/promises");
            await fs.mkdir(dirPath, options);
            return true;
        });

        ipcMain.handle(
            "node-container:writeFile",
            async (_, filePath, contents) => {
                // Handle writeFile request
                const fs = require("fs/promises");
                await fs.writeFile(filePath, contents);
                return true;
            }
        );

        ipcMain.handle("node-container:readFile", async (_, filePath, encoding) => {
            // Handle readFile request
            const fs = require("fs/promises");
            return await fs.readFile(filePath, {encoding});
        });

        ipcMain.handle("node-container:readdir", async (_, dirPath, options) => {
            // Handle readdir request
            const fs = require("fs/promises");
            return await fs.readdir(dirPath, options);
        });

        ipcMain.handle("node-container:platform", async () => {
            const os = require("os");
            return os.platform();
        });
        ipcMain.handle("node-container:set-now-path", (_, path) => {
            nowPath = path;
        });

        // Add handler for getting project root directory
        ipcMain.handle("node-container:get-project-root", () => {
            // In development environment
            if (nowPath) {
                return nowPath;
            }
            if (process.env.VITE_DEV_SERVER_URL) {
                return path.join(process.cwd(), "workspace");
            }
            // In production environment
            return path.join(app.getAppPath(), "../../workspace");
        });

        // Modify spawn command handler
        ipcMain.handle(
            "node-container:spawn",
            async (
                event: IpcMainInvokeEvent,
                command: string,
                args: string[],
                options: SpawnOptions
            ): Promise<ProcessResult> => {
                try {
                    console.log(
                        "Main Process: Spawning command:",
                        command,
                        args,
                        options
                    );
                    const proc = spawn(command, args, {
                        cwd: process.env.VITE_DEV_SERVER_URL
                            ? path.join(process.cwd(), "workspace")
                            : path.join(app.getAppPath(), "../../workspace"),
                        env: {
                            ...process.env,
                            PATH: `${process.env.PATH}${path.delimiter}${path.join(app.getAppPath(), "node_modules", ".bin")}`,
                            NODE_PATH: path.join(app.getAppPath(), "node_modules"),
                        },
                        shell: true,
                        stdio: ["pipe", "pipe", "pipe"],
                    });

                    const processId = Math.random().toString(36).substr(2, 9);
                    console.log("Main Process: Process ID:", processId);
                    activeProcesses.set(processId, proc);

                    // Use webContents instead of event.sender
                    const webContents = event.sender;

                    proc.stdout.on("data", (data) => {
                        const output = data.toString();
                        console.log("Main Process: stdout:", output);
                        webContents.send(`process-output-${processId}`, output);
                    });

                    proc.stderr.on("data", (data) => {
                        const output = data.toString();
                        console.error("Main Process: stderr:", output);
                        webContents.send(`process-output-${processId}`, output);
                    });

                    proc.on("error", (error) => {
                        console.error("Main Process: Process error:", error);
                        webContents.send(
                            `process-output-${processId}`,
                            `Error: ${error.message}\n`
                        );
                    });

                    proc.on("close", (code) => {
                        activeProcesses.delete(processId);
                        webContents.send(`process-exit-${processId}`, code || 0);
                    });

                    return {processId};
                } catch (error) {
                    console.error("Main Process: Spawn error:", error);
                    throw error;
                }
            }
        );

        // Add handler for getting process exit code
        ipcMain.handle(
            "node-container:wait-exit",
            async (event, processId: string) => {
                const proc = activeProcesses.get(processId);
                if (!proc) {
                    throw new Error("Process not found");
                }

                return new Promise((resolve) => {
                    proc.on("close", (code: string) => {
                        activeProcesses.delete(processId);
                        resolve(code);
                    });
                });
            }
        );

        // Add process termination handler
        ipcMain.handle(
            "node-container:kill-process",
            async (event, processId: string) => {
                const proc = activeProcesses.get(processId);
                if (proc) {
                    proc.kill();
                    activeProcesses.delete(processId);
                }
            }
        );

        // Add process stop handler
        ipcMain.handle("node-container:stop-server", async (_, port: number) => {
            if (process.platform === "win32") {
                spawn("taskkill", ["/F", "/PID", port.toString()]);
            } else {
                spawn("kill", ["-9", port.toString()]);
            }
        });

        ipcMain.handle(
            "node-container:stat",
            async (_: IpcMainInvokeEvent, filePath: string): Promise<FileStats> => {
                const fs = require("fs/promises");
                const stats = await fs.stat(filePath);
                return {
                    isDirectory: stats.isDirectory(),
                    isFile: stats.isFile(),
                    size: stats.size,
                    mtime: stats.mtime,
                };
            }
        );

        ipcMain.handle("node-container:sync-filesystem", async (event, files) => {
            try {
                const projectRoot = nowPath
                    ? nowPath
                    : process.env.VITE_DEV_SERVER_URL
                        ? path.join(process.cwd(), "workspace")
                        : path.join(app.getAppPath(), "../../workspace");

                const fs = require("fs/promises");

                // Ensure directory exists
                await fs.mkdir(projectRoot, {recursive: true});

                // Get existing file list
                // @ts-ignore
                async function getAllFiles(dir: string): Promise<string[]> {
                    const entries = await fs.readdir(dir, {withFileTypes: true});
                    const files = [];

                    for (const entry of entries) {
                        const fullPath = path.join(dir, entry.name);
                        if (isHiddenNodeModules.some(item => entry?.name?.indexOf(item) > -1)) {
                            continue;
                        }

                        if (entry.isDirectory()) {
                            files.push(...(await getAllFiles(fullPath)));
                        } else {
                            files.push(fullPath);
                        }
                    }

                    return files;
                }

                // Get all existing files
                const existingFiles = await getAllFiles(projectRoot);

                // Sync all files
                for (const [filePath, contents] of Object.entries(files)) {
                    if (typeof contents !== "string") {
                        console.log("Skipping non-string content:", filePath);
                        continue;
                    }
                    if (filePath.startsWith("node_modules/")) {
                        console.log("Skipping node_modules file:", filePath);
                        continue;
                    }

                    const fullPath = path.join(projectRoot, filePath);
                    const dirPath = path.dirname(fullPath);

                    await fs.mkdir(dirPath, {recursive: true});
                    await fs.writeFile(fullPath, contents, "utf-8");

                    // Remove processed files from existing file list
                    const index = existingFiles.indexOf(fullPath);
                    if (index > -1) {
                        existingFiles.splice(index, 1);
                    }
                }

                // Remove unnecessary files (excluding node_modules)
                for (const file of existingFiles) {
                    if (!isHiddenNodeModules.some(item => file?.indexOf(item) > -1)) {
                        await fs.unlink(file);
                    }
                }


                return true;
            } catch (error) {
                // console.error('Failed to sync filesystem:', error);
                throw error;
            }
        });

        // Modify terminal:create handler
        ipcMain.handle(
            "terminal:create",
            (event: IpcMainInvokeEvent, options: TerminalOptions): ProcessResult => {
                console.log("terminal:create", options);

                // Get default shell
                let shell = process.platform === "win32" ? "powershell.exe" : "bash";

                // Check zsh on macOS
                if (process.platform === "darwin") {
                    try {
                        const userShell = process.env.SHELL;
                        if (userShell && userShell.includes("zsh")) {
                            shell = "zsh";
                        } else if (fs.existsSync("/bin/zsh")) {
                            shell = "zsh";
                        }
                    } catch (error) {
                        console.error("Error detecting shell:", error);
                        // If detection fails, use bash as a fallback option
                    }
                }

                const processId = options.processId || Math.random().toString(36).substr(2, 9);

                // Add necessary environment variables
                const env = {
                    ...process.env,
                    PATH: process.env.PATH || "",
                };

                // If macOS, add common npm paths
                if (process.platform === "darwin") {
                    const additionalPaths = [
                        "/usr/local/bin", // Homebrew installed packages
                        "/opt/homebrew/bin", // Homebrew on Apple Silicon
                        "/usr/bin", // System binary files
                        "/bin", // Basic binary files
                        "/usr/sbin", // System management binary files
                        "/sbin", // Basic system management binary files
                        `${process.env.HOME}/.npm-global/bin`, // npm global installation path
                        `${process.env.HOME}/.nvm/versions/node/*/bin`, // nvm installed node paths
                    ];

                    env.PATH = `${additionalPaths.join(":")}:${env.PATH}`;
                }

                const ptyProcess = pty.spawn(shell, [], {
                    name: "xterm-color",
                    cols: options.cols || 80,
                    rows: options.rows || 24,
                    cwd: nowPath
                        ? nowPath
                        : process.env.VITE_DEV_SERVER_URL
                            ? path.join(process.cwd(), "workspace")
                            : path.join(app.getAppPath(), "../../workspace"),
                    env: env, // Use updated environment variables
                });

                ptyProcesses.set(processId, ptyProcess);

                // Forward pty output to render process
                ptyProcess.onData((data: string) => {
                    mainWindow.webContents.send(`terminal-output-${processId}`, data);
                });

                return {processId};
            }
        );

        // Handle terminal input
        ipcMain.handle("terminal:write", (_, processId, data) => {
            const ptyProcess = ptyProcesses.get(processId);
            if (ptyProcess) {
                ptyProcess.write(data);
            }
        });

        // Handle terminal size adjustment
        ipcMain.handle("terminal:resize", (_, processId, cols, rows) => {
            const ptyProcess = ptyProcesses.get(processId);
            if (ptyProcess) {
                ptyProcess.resize(cols, rows);
            }
        });
        // Get parent directory of current directory
        ipcMain.handle(
            "node-container:get-parent-paths",
            async (_: IpcMainInvokeEvent, currentPath: string): Promise<ParentPaths> => {
                try {
                    const parentPath = path.dirname(currentPath); // Parent directory
                    const grandParentPath = path.dirname(parentPath); // Grand parent directory
                    const lastGrandParentPath = path.dirname(grandParentPath); // Great grand parent directory
                    return {
                        parentPath,
                        grandParentPath,
                        lastGrandParentPath,
                    };
                } catch (error) {
                    throw new Error(`Failed to get parent directories: ${error.message}`);
                }
            }
        );

        // Execute command line commands
        ipcMain.handle(
            "node-container:exec-command",
            async (_event, command: string) => {
                try {
                    const {exec} = require("child_process");
                    return new Promise((resolve, reject) => {
                        exec(
                            command,
                            (error: Error | null, stdout: string, stderr: string) => {
                                if (error) {
                                    reject(error.message);
                                    return;
                                }
                                if (stderr) {
                                    reject(stderr);
                                    return;
                                }
                                resolve(stdout);
                            }
                        );
                    });
                } catch (error) {
                    throw new Error(`Command execution failed: ${error.message}`);
                }
            }
        );
        // Handle terminal destruction
        ipcMain.handle("terminal:dispose", (_, processId) => {
            console.log(2333)
            const ptyProcess = ptyProcesses.get(processId);
            if (ptyProcess) {
                console.log(2555)
                ptyProcess.kill();
                ptyProcesses.delete(processId);
            }
        });

        // Clean up all pty instances when window is closed
        mainWindow.on("closed", () => {
            for (const ptyProcess of Array.from(ptyProcesses.values())) {
                ptyProcess.kill();
            }

            ptyProcesses.clear();
        });
        // Register MCP handlers
        ipcMain.on('mcp:servers-from-renderer', (_, servers) => mcpService.setServers(servers))
        ipcMain.handle('mcp:list-servers', async () => mcpService.listAvailableServices())
        ipcMain.handle('mcp:add-server', async (_, server: MCPServer) => mcpService.addServer(server))
        ipcMain.handle('mcp:update-server', async (_, server: MCPServer) => mcpService.updateServer(server))
        ipcMain.handle('mcp:delete-server', async (_, serverName: string) => mcpService.deleteServer(serverName))
        ipcMain.handle('mcp:set-server-active', async (_, {name, isActive}) =>
            mcpService.setServerActive({name, isActive})
        )

        // According to preload, this should take no parameters, but our implementation accepts
        // an optional serverName for better flexibility
        ipcMain.handle('mcp:list-tools', async (_, serverName?: string) => mcpService.listTools(serverName))
        ipcMain.handle('mcp:call-tool', async (_, params: { client: string; name: string; args: any }) =>
            mcpService.callTool(params)
        )
        ipcMain.handle('mcp:cleanup', async () => mcpService.cleanup())

        ipcMain.handle('app:is-binary-exist', (_, name: string) => isBinaryExists(name))
        ipcMain.handle('app:get-binary-path', (_, name: string) => getBinaryPath(name))
        ipcMain.handle('app:install-uv-binary', () => runInstallScript('install-uv.js'))
        ipcMain.handle('app:install-bun-binary', () => runInstallScript('install-bun.js'))

        // Listen for changes in MCP servers and notify renderer
        mcpService.on('mcp:servers-changed', (servers) => {
            console.log('on.mcp:servers-changed', JSON.stringify(servers))
            mainWindow?.webContents.send('mcp:servers-changed', servers)
        })
        // Add detailed log
        logToFile(`App path: ${app.getAppPath()}`);
        logToFile(`__dirname: ${__dirname}`);
        logToFile(
            `Preload path: ${process.env.VITE_DEV_SERVER_URL
                ? path.join(__dirname, "preload.js")
                : path.join(app.getAppPath(), "dist-electron", "preload.js")
            }`
        );

        // 处理代理设置
        ipcMain.on('set-proxy', async ( _, {type, customProxy}) => {
            try {
                const config = {
                    mode: type as 'system' | 'custom' | 'none',
                    url: type === 'custom' ? customProxy : undefined
                }

                await proxyManager.configureProxy(config)
            } catch (error) {
                console.error('Failed to set proxy:', error)
            }
        })

    } catch (error) {
        logToFile(`Error creating window: ${error}`)
        throw error
    }
}

// Create window when Electron finishes initialization
app
    .whenReady()
    .then(() => {
        logToFile("App is ready")
        try {
            createWindow()
        } catch (error) {
            logToFile(`Error in whenReady handler: ${error}`)
        }
    })
    .catch((error) => {
        logToFile(`Failed to initialize app: ${error}`)
    })

// Exit application when all windows are closed
app.on("window-all-closed", () => {
    logToFile("All windows closed")
    if (process.platform !== "darwin") {
        app.quit()
    }
})

app.on("activate", () => {
    logToFile("App activated")
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow()
    }
})

app.on("quit", () => {
    logToFile("App is quitting")
})