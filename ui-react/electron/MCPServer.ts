import {MCPServer, MCPTool} from '@/types/mcp'
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {SSEClientTransport} from '@modelcontextprotocol/sdk/client/sse.js';
import {EventEmitter} from 'events'
import {getBinaryPath} from '../src/utils/process'
import {mainWindow} from "./main";

/**
 * Service for managing Model Context Protocol servers and tools
 */
export default class MCPServiceManager extends EventEmitter {
    private servers: MCPServer[] = []
    private activeServers: Map<string, any> = new Map()
    private clients: { [key: string]: any } = {}
    private Client: any = null
    private stdioTransport: any = null
    private sseTransport: any = null
    private initialized = false
    private initPromise: Promise<void> | null = null

    // Simplified server loading state management
    private readyState = {
        serversLoaded: false,
        promise: null as Promise<void> | null,
        resolve: null as ((value: void) => void) | null
    }

    constructor() {
        super()
        this.createServerLoadingPromise()
        this.init().catch((err) => this.logError('Failed to initialize MCP service', err))
    }

    /**
     * Create a promise that resolves when servers are loaded
     */
    private createServerLoadingPromise(): void {
        this.readyState.promise = new Promise<void>((resolve) => {
            this.readyState.resolve = resolve
        })
    }

    /**
     * Set servers received from zustand and trigger initialization if needed
     */
    public setServers(servers: MCPServer[]): void {
        this.servers = servers

        // Mark servers as loaded and resolve the waiting promise
        if (!this.readyState.serversLoaded && this.readyState.resolve) {
            this.readyState.serversLoaded = true
            this.readyState.resolve()
            this.readyState.resolve = null
        }

        // 立即通知渲染进程服务器列表已更新
        this.notifyServersChanged(servers)

        // Initialize if not already initialized
        if (!this.initialized) {
            this.init().catch((err) => this.logError('Failed to initialize MCP service', err))
        }
    }

    /**
     * Initialize the MCP service if not already initialized
     */
    public async init(): Promise<void> {
        // If already initialized, return immediately
        if (this.initialized) return

        // If initialization is in progress, return that promise
        if (this.initPromise) return this.initPromise

        this.initPromise = (async () => {
            try {
                console.info('[MCP] Starting initialization')

                // Wait for servers to be loaded from zustand
                await this.waitForServers()
                // Load SDK components in parallel for better performance

                await this.loadSDKComponents()

                // Mark as initialized before loading servers
                this.initialized = true

                // Load active servers
                await this.loadActiveServers()
                console.info('[MCP] Initialization successfully')

                return
            } catch (err) {
                this.initialized = false // Reset flag on error
                console.error('[MCP] Failed to initialize:', err)
                throw err
            } finally {
                this.initPromise = null
            }
        })()

        return this.initPromise
    }

    // 单独的SDK加载方法，更清晰的职责分离
    private async loadSDKComponents(): Promise<void> {
        // 使用Promise.all并行加载所有SDK组件
        const [client, stdio, sse] = await Promise.all([
            this.importModule('@modelcontextprotocol/sdk/client/index.js'),
            this.importModule('@modelcontextprotocol/sdk/client/stdio.js'),
            this.importModule('@modelcontextprotocol/sdk/client/sse.js')
        ]);

        // 赋值给类属性
        this.Client = client.Client;
        this.stdioTransport = stdio.StdioClientTransport;
        this.sseTransport = sse.SSEClientTransport;
    }

    // 包装导入逻辑，增强错误处理和调试能力
    private async importModule(path: string): Promise<any> {
        try {
            return await import(path);
        } catch (err) {
            console.error(`[MCP] Failed to import module: ${path}`, err);
            throw err;
        }
    }

    /**
     * Wait for servers to be loaded from zustand
     */
    private async waitForServers(): Promise<void> {
        if (!this.readyState.serversLoaded && this.readyState.promise) {
            console.info('[MCP] Waiting for servers data from zustand...')
            await this.readyState.promise
            console.info('[MCP] Servers received, continuing initialization')
        }
    }

    /**
     * Helper to create consistent error logging functions
     */
    private logError(message: string, err?: any): void {
        console.error(`[MCP] ${message}`, err)
    }

    /**
     * List all available MCP servers
     */
    public async listAvailableServices(): Promise<MCPServer[]> {
        await this.ensureInitialized()
        return this.servers
    }

    /**
     * Ensure the service is initialized before operations
     */
    private async ensureInitialized() {
        if (!this.initialized) {
            console.debug('[MCP] Ensuring initialization')
            await this.init()
        }
    }

    /**
     * Add a new MCP server
     */
    public async addServer(server: MCPServer): Promise<void> {
        await this.ensureInitialized()

        // Check for duplicate name
        if (this.servers.some((s) => s.name === server.name)) {
            throw new Error(`Server with name ${server.name} already exists`)
        }

        // Activate if needed
        if (server.isActive) {
            await this.activate(server)
        }

        // Add to servers list
        this.servers = [...this.servers, server]
        this.notifyServersChanged(this.servers)
    }

    /**
     * Update an existing MCP server
     */
    public async updateServer(server: MCPServer): Promise<void> {
        await this.ensureInitialized()

        const index = this.servers.findIndex((s) => s.name === server.name)
        if (index === -1) {
            throw new Error(`Server ${server.name} not found`)
        }

        // Check activation status change
        const wasActive = this.servers[index].isActive
        if (wasActive && !server.isActive) {
            await this.deactivate(server.name)
        } else if (!wasActive && server.isActive) {
            await this.activate(server)
        } else {
            await this.restartServer(server)
        }

        // Update servers list
        const updatedServers = [...this.servers]
        updatedServers[index] = server
        this.servers = updatedServers

        // Notify zustand
        this.notifyServersChanged(updatedServers)
    }

    public async restartServer(_server: MCPServer): Promise<void> {
        await this.ensureInitialized()

        const server = this.servers.find((s) => s.name === _server.name)

        if (server) {
            if (server.isActive) {
                await this.deactivate(server.name)
            }
            await this.activate(server)
        }
    }

    /**
     * Delete an MCP server
     */
    public async deleteServer(serverName: string): Promise<void> {
        await this.ensureInitialized()

        // Deactivate if running
        if (this.clients[serverName]) {
            await this.deactivate(serverName)
        }

        // Update servers list
        const filteredServers = this.servers.filter((s) => s.name !== serverName)
        this.servers = filteredServers
        this.notifyServersChanged(filteredServers)
    }

    /**
     * Set a server's active state
     */
    public async setServerActive(params: { name: string; isActive: boolean }): Promise<void> {
        await this.ensureInitialized()

        const {name, isActive} = params
        const server = this.servers.find((s) => s.name === name)
        if (!server) {
            throw new Error(`Server ${name} not found`)
        }

        // Activate or deactivate as needed
        if (isActive) {
            await this.activate(server)
        } else {
            await this.deactivate(name)
        }
        // Update server status
        server.isActive = isActive
        this.notifyServersChanged([...this.servers])
    }

    /**
     * Notify zustand in the renderer process about server changes
     */
    private notifyServersChanged(servers: MCPServer[]): void {
        try {
            mainWindow.webContents.send('mcp:servers-changed', servers)
        } catch (error) {
            console.error('[MCP] 发送服务器变更通知失败:', error)
        }
    }

    /**
     * Activate an MCP server
     */
    public async activate(server: MCPServer): Promise<void> {
        await this.ensureInitialized()

        const {name, baseUrl, command, env} = server
        const args = [...(server.args || [])]

        // Skip if already running
        if (this.clients[name]) {
            console.info(`[MCP] Server ${name} is already running`)
            return
        }

        let transport: StdioClientTransport | SSEClientTransport

        try {
            // Create appropriate transport based on configuration
            if (baseUrl) {
                transport = new this.sseTransport!(new URL(baseUrl))
            } else if (command) {

                let cmd: string = command
                if (command === 'npx') {
                    cmd = await getBinaryPath('bun')

                    if (cmd === 'bun') {
                        cmd = 'npx'
                    }

                    console.info(`[MCP] Using command: ${cmd}`)

                    // add -x to args if args exist
                    if (args && args.length > 0) {
                        if (!args.includes('-y')) {
                            args.unshift('-y')
                        }
                        if (cmd.includes('bun') && !args.includes('x')) {
                            args.unshift('x')
                        }
                    }
                } else if (command === 'uvx') {
                    cmd = await getBinaryPath('uvx')
                }

                console.info(`[MCP] Starting server with command: ${cmd} ${args ? args.join(' ') : ''}`)
                const mirrorEnv = this.getMirrorEnvironment()
                // 获取镜像设置
                const fullEnv = {
                    PATH: this.getEnhancedPath(process.env.PATH || ''),
                    ...mirrorEnv,
                    ...env,
                }
                transport = new this.stdioTransport!({
                    command: cmd,
                    args,
                    stderr: 'pipe',
                    env: fullEnv
                })
            } else {
                throw new Error('Either baseUrl or command must be provided')
            }

            // Create and connect client
            const client = new this.Client!({name, version: '1.0.0'}, {capabilities: {}})

            await client.connect(transport)

            // Store client and server info
            this.clients[name] = client
            this.activeServers.set(name, {client, server})

            console.info(`[MCP] Activated server: ${server.name}`)
            this.emit('server-started', {name})
        } catch (error) {
            console.error(`[MCP] Error activating server ${name}:`, error)
            await this.setServerActive({name, isActive: false})
            throw error
        }
    }

    /**
     * Deactivate an MCP server
     */
    public async deactivate(name: string): Promise<void> {
        await this.ensureInitialized()

        if (!this.clients[name]) {
            console.warn(`[MCP] Server ${name} is not running`)
            return
        }

        try {
            console.info(`[MCP] Stopping server: ${name}`)
            await this.clients[name].close()
            delete this.clients[name]
            this.activeServers.delete(name)
            this.emit('server-stopped', {name})
        } catch (error) {
            console.error(`[MCP] Error deactivating server ${name}:`, error)
            throw error
        }
    }

    /**
     * List available tools from active MCP servers
     */
    public async listTools(serverName?: string): Promise<MCPTool[]> {
        await this.ensureInitialized()
        console.info(`[MCP] Listing tools from ${serverName || 'all active servers'}`)

        try {
            // If server name provided, list tools for that server only
            if (serverName) {
                return await this.listToolsFromServer(serverName)
            }

            // Otherwise list tools from all active servers
            let allTools: MCPTool[] = []

            for (const clientName in this.clients) {
                console.info(`[MCP] Listing tools from ${clientName}`)
                try {
                    const tools = await this.listToolsFromServer(clientName)
                    allTools = allTools.concat(tools)
                } catch (error) {
                    this.logError(`Error listing tools for ${clientName}`, error)
                }
            }

            console.info(`[MCP] Total tools listed: ${allTools.length}`)
            return allTools
        } catch (error) {
            this.logError('Error listing tools:', error)
            return []
        }
    }

    /**
     * Helper method to list tools from a specific server
     */
    private async listToolsFromServer(serverName: string): Promise<MCPTool[]> {
        console.info(`[MCP] start list tools from ${serverName}:`)
        if (!this.clients[serverName]) {
            throw new Error(`MCP Client ${serverName} not found`)
        }
        const {tools} = await this.clients[serverName].listTools()

        const transformedTools = tools.map((tool: any) => ({
            ...tool,
            serverName,
            id: `${serverName}.${tool.name}`,
        }))

        console.info(`[MCP] Tools from ${serverName}:`, transformedTools)
        return transformedTools
    }

    /**
     * Call a tool on an MCP server
     */
    public async callTool(params: { client: string; name: string; args: any }): Promise<any> {
        await this.ensureInitialized()

        const {client, name, args} = params

        if (!this.clients[client]) {
            throw new Error(`MCP Client ${client} not found`)
        }

        console.info('[MCP] Calling:', client, name, args)

        try {
            return await this.clients[client].callTool({
                name,
                arguments: args
            })
        } catch (error) {
            console.error(`[MCP] Error calling tool ${name} on ${client}:`, error)
            throw error
        }
    }

    /**
     * Clean up all MCP resources
     */
    public async cleanup(): Promise<void> {
        const clientNames = Object.keys(this.clients)

        if (clientNames.length === 0) {
            console.info('[MCP] No active servers to clean up')
            return
        }

        console.info(`[MCP] Cleaning up ${clientNames.length} active servers`)

        // Deactivate all clients
        await Promise.allSettled(
            clientNames.map((name) =>
                this.deactivate(name).catch((err) => {
                    console.error(`[MCP] Error during cleanup of ${name}:`, err)
                })
            )
        )

        this.clients = {}
        this.activeServers.clear()
        console.info('[MCP] All servers cleaned up')
    }

    /**
     * Load all active servers
     */
    private async loadActiveServers(): Promise<void> {
        const activeServers = this.servers.filter((server) => server.isActive)

        if (activeServers.length === 0) {
            console.info('[MCP] No active servers to load')
            return
        }

        console.info(`[MCP] Start loading ${activeServers.length} active servers`)

        // Activate servers in parallel for better performance
        await Promise.allSettled(
            activeServers.map(async (server) => {
                try {
                    await this.activate(server)
                } catch (error) {
                    this.logError(`Failed to activate server ${server.name}`, error)
                    this.emit('server-error', {name: server.name, error})
                }
            })
        )

        console.info(`[MCP] End loading ${Object.keys(this.clients).length} active servers`)
    }

    /**
     * Get enhanced PATH including common tool locations
     */
    private getEnhancedPath(originalPath: string): string {
        // 将原始 PATH 按分隔符分割成数组
        const pathSeparator = process.platform === 'win32' ? ';' : ':'
        const existingPaths = new Set(originalPath.split(pathSeparator).filter(Boolean))
        const homeDir = process.env.HOME || process.env.USERPROFILE || ''

        // 定义要添加的新路径
        const newPaths: string[] = []
        switch (process.platform) {
            case 'darwin':
                newPaths.push(
                    '/bin',
                    '/usr/bin',
                    '/usr/local/bin',
                    '/usr/local/sbin',
                    '/opt/homebrew/bin',
                    '/opt/homebrew/sbin',
                    '/usr/local/opt/node/bin',
                    `${homeDir}/.nvm/current/bin`,
                    `${homeDir}/.npm-global/bin`,
                    `${homeDir}/.yarn/bin`,
                    `${homeDir}/.cargo/bin`,
                    '/opt/local/bin'
                )
                break;
            case 'linux':
                newPaths.push(
                    '/bin',
                    '/usr/bin',
                    '/usr/local/bin',
                    `${homeDir}/.nvm/current/bin`,
                    `${homeDir}/.npm-global/bin`,
                    `${homeDir}/.yarn/bin`,
                    `${homeDir}/.cargo/bin`,
                    '/snap/bin'
                )
                break;
            case 'win32':
                newPaths.push(
                    `${process.env.APPDATA}\\npm`,
                    `${homeDir}\\AppData\\Local\\Yarn\\bin`,
                    `${homeDir}\\.cargo\\bin`
                )
                break;
        }

        // 只添加不存在的路径
        newPaths.forEach((path) => {
            if (path && !existingPaths.has(path)) {
                existingPaths.add(path)
            }
        })

        // 转换回字符串
        return Array.from(existingPaths).join(pathSeparator)
    }

    /**
     * Get mirror settings from localStorage and return as environment variables
     */
    private async getMirrorEnvironment(): Promise<Record<string, string>> {
        try {
            const env: Record<string, string> = {}

            try {
                // 尝试从主进程获取设置
                let settings: {
                    nodeMirror?: string;
                    customNodeMirror?: string;
                    pythonMirror?: string;
                    customPythonMirror?: string;
                } = {}

                if (mainWindow && mainWindow.webContents) {
                    const settingsStr = await mainWindow.webContents.executeJavaScript('localStorage.getItem("settingsConfig")')
                    if (settingsStr) {
                        settings = JSON.parse(settingsStr)
                    }
                }

                // 使用与GeneralSettings组件相同的默认值逻辑
                const settingsWithDefaults = {
                    nodeMirror: settings.nodeMirror || 'https://registry.npmjs.org/',
                    customNodeMirror: settings.customNodeMirror || '',
                    pythonMirror: settings.pythonMirror || 'https://pypi.org/simple',
                    customPythonMirror: settings.customPythonMirror || '',
                }

                // 添加npm镜像设置
                const nodeMirror = settingsWithDefaults.nodeMirror === 'custom' && settingsWithDefaults.customNodeMirror
                    ? settingsWithDefaults.customNodeMirror
                    : settingsWithDefaults.nodeMirror

                if (nodeMirror) {
                    env.npm_config_registry = nodeMirror
                }

                // 添加Python镜像设置
                const pythonMirror = settingsWithDefaults.pythonMirror === 'custom' && settingsWithDefaults.customPythonMirror
                    ? settingsWithDefaults.customPythonMirror
                    : settingsWithDefaults.pythonMirror

                if (pythonMirror) {
                    env.PIP_INDEX_URL = pythonMirror
                }
            } catch (err) {
                console.error('[MCP] Error reading settings from renderer:', err)
            }

            return env
        } catch (error) {
            console.error('[MCP] Error getting mirror settings:', error)
            return {}
        }
    }
}

